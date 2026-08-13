package com.safekey.authenticator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.security.PinManager
import com.safekey.authenticator.security.SelfDestructManager
import com.safekey.authenticator.totp.Base32
import com.safekey.authenticator.totp.TotpGenerator
import com.safekey.authenticator.ui.navigation.NavigationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

/** A rendered account: domain data + the live code for the current tick. */
data class AccountUi(
    val account: Account,
    val code: String,
    val remainingSeconds: Int,
    val periodFraction: Float
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val safeKeyApp = app as SafeKeyApp
    private val repo = safeKeyApp.accountRepository
    private val settingsRepo = safeKeyApp.settingsRepository
    val pinManager = PinManager(app)
    private val selfDestruct = SelfDestructManager()

    val nav = NavigationState()

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val accounts: StateFlow<List<Account>> = repo.accounts
        .flowOn(Dispatchers.Default) // decryption + flow ops off the main thread
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // UI tick, ~2 Hz — smooth countdown without recomputing codes constantly
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now

    private val tickJob: Job = viewModelScope.launch {
        while (true) {
            _now.value = System.currentTimeMillis()
            delay(500)
        }
    }

    /**
     * Live codes for every account, recomputed each tick.
     * HMAC runs on Dispatchers.Default — never on the main thread.
     */
    val accountUiList: StateFlow<List<AccountUi>> =
        combine(accounts, _now) { list, time ->
            list.map { a ->
                AccountUi(
                    account = a,
                    code = TotpGenerator.generate(
                        secret = Base32.decode(a.secret),
                        timeMs = time,
                        period = a.period,
                        digits = a.digits,
                        algorithm = a.algorithm
                    ),
                    remainingSeconds = TotpGenerator.remainingSeconds(time, a.period),
                    periodFraction = TotpGenerator.periodFraction(time, a.period)
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- search state ----
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    // ---- biometric lock state ----
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    // ---- PIN gate state (overrides everything) ----
    private val _pinRequired = MutableStateFlow(false)
    val pinRequired: StateFlow<Boolean> = _pinRequired

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError

    private val _destroyed = MutableStateFlow(false)
    val destroyed: StateFlow<Boolean> = _destroyed

    /** True while the app is in the foreground (drives random-time PIN checks). */
    private var appInForeground = false

    private var randomPinJob: Job? = null

    init {
        viewModelScope.launch {
            settings.collect { s ->
                restartRandomPinScheduler(s)
            }
        }
    }

    /** Called when the activity comes to the foreground. */
    fun onAppForeground() {
        appInForeground = true
        if (settings.value.biometricLock) _locked.value = true
        checkDailyPin()
    }

    /** Called when the activity goes to the background. */
    fun onAppBackground() {
        appInForeground = false
        if (settings.value.biometricLock) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
        checkDailyPin()
    }

    // ---------------------------------------------------------------- PIN

    /** Ask for the PIN right now (periodic checks + manual triggers). */
    fun requirePin() {
        if (pinManager.hasPin() && !_destroyed.value) {
            _pinError.value = null
            _pinRequired.value = true
        }
    }

    /**
     * Validate an entered PIN.
     * @return true when correct.
     */
    fun onPinEntered(pin: String): Boolean {
        if (pinManager.verifyPin(pin)) {
            _pinRequired.value = false
            _pinError.value = null
            viewModelScope.launch {
                settingsRepo.setPinFailCount(0)
                settingsRepo.setLastPinVerifiedDay(today())
            }
            restartRandomPinScheduler(settings.value)
            return true
        }
        // wrong PIN — count toward self-destruct threshold
        _pinError.value = getApplication<Application>().getString(R.string.pin_wrong)
        viewModelScope.launch {
            val count = settings.value.pinFailCount + 1
            settingsRepo.setPinFailCount(count)
            maybeSelfDestructOnFailures(count, settings.value.biometricFailCount)
        }
        return false
    }

    /** Called on biometric authentication failure (fingerprint mismatch). */
    fun onBiometricFailed() {
        viewModelScope.launch {
            val count = settings.value.biometricFailCount + 1
            settingsRepo.setBiometricFailCount(count)
            maybeSelfDestructOnFailures(settings.value.pinFailCount, count)
        }
    }

    /** Called on successful biometric authentication — resets the counter. */
    fun onBiometricSucceeded() {
        viewModelScope.launch { settingsRepo.setBiometricFailCount(0) }
    }

    fun cancelPinGate() {
        _pinRequired.value = false
        _pinError.value = null
    }

    /** Self-destruct PIN entered during a PIN prompt: verify + wipe. */
    fun onSelfDestructPinEntered(pin: String): Boolean {
        return pinManager.verifyDestroyPin(pin)
    }

    fun remainingAttempts(): Int? {
        if (settings.value.destroyMode != AppSettings.DESTROY_FAIL_COUNT) return null
        val remaining = settings.value.failThreshold -
            (settings.value.pinFailCount + settings.value.biometricFailCount)
        return remaining.coerceAtLeast(0)
    }

    private fun checkDailyPin() {
        val s = settings.value
        if (s.pinVerifyMode != AppSettings.PIN_VERIFY_DAILY) return
        if (!pinManager.hasPin()) return
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val fixedMinutes = s.pinFixedHour * 60 + s.pinFixedMinute
        if (nowMinutes >= fixedMinutes && s.lastPinVerifiedDay != today()) {
            requirePin()
        }
    }

    private fun today(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Random-time PIN checks: fire at a random point in the next 5–25 minutes,
     * then wait for the gate to clear before scheduling the next one.
     */
    private fun restartRandomPinScheduler(s: AppSettings) {
        randomPinJob?.cancel()
        randomPinJob = viewModelScope.launch {
            while (true) {
                val delayMs = Random.nextLong(5 * 60_000L, 25 * 60_000L)
                delay(delayMs)
                val current = settings.value
                if (current.pinVerifyMode != AppSettings.PIN_VERIFY_RANDOM) return@launch
                if (!pinManager.hasPin()) return@launch
                if (_destroyed.value) return@launch
                if (appInForeground && !_locked.value && !_pinRequired.value) {
                    requirePin()
                }
                // wait for the gate to clear (verified or cancelled) before re-arming
                _pinRequired.first { !it }
            }
        }
    }

    // -------------------------------------------------------- self destruct

    private fun maybeSelfDestructOnFailures(pinFails: Int, biometricFails: Int) {
        val s = settings.value
        if (s.destroyMode != AppSettings.DESTROY_FAIL_COUNT) return
        if (pinFails + biometricFails >= s.failThreshold) {
            selfDestruct()
        }
    }

    /**
     * Destroys all data irreversibly: deletes the AndroidKeyStore master key
     * (every ciphertext becomes permanently undecryptable), wipes the account
     * table, PINs and settings.
     */
    fun selfDestruct() {
        if (_destroyed.value) return
        viewModelScope.launch {
            try {
                selfDestruct.destroyMasterKey()
                safeKeyApp.accountDao.deleteAll()
                pinManager.wipeAll()
                settingsRepo.wipeSettings()
                _pinRequired.value = false
                _locked.value = false
                _destroyed.value = true
            } catch (_: Exception) {
                // nothing sensible left to do — surface the state anyway
                _destroyed.value = true
            }
        }
    }

    /** Check whether the current PIN matches the one baked into an import file. */
    fun verifyImportPin(pin: String, salt: String, hash: String): Boolean =
        com.safekey.authenticator.security.PinHasher.verify(pin, salt, hash)

    fun hasLocalPin(): Boolean = pinManager.hasPin()

    fun verifyLocalPin(pin: String): Boolean = pinManager.verifyPin(pin)

    // ------------------------------------------------------------ toasts

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast
    fun showToast(message: String) { _toast.value = message }
    fun clearToast() { _toast.value = null }

    // ---------------------------------------------------- account actions

    fun addAccount(issuer: String, label: String, secret: String, algorithm: String, digits: Int, period: Int) {
        viewModelScope.launch {
            try {
                repo.add(issuer, label, secret, algorithm, digits, period)
                showToast(getApplication<Application>().getString(R.string.account_added))
            } catch (e: Exception) {
                showToast(e.message ?: "Error")
            }
        }
    }

    fun updateAccount(accountId: String, issuer: String, label: String, secret: String, algorithm: String, digits: Int, period: Int) {
        viewModelScope.launch {
            val account = repo.getById(accountId) ?: return@launch
            try {
                repo.update(account, issuer, label, secret, algorithm, digits, period)
                showToast(getApplication<Application>().getString(R.string.account_updated))
            } catch (e: Exception) {
                showToast(e.message ?: "Error")
            }
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            repo.delete(account)
            showToast(getApplication<Application>().getString(R.string.deleted))
        }
    }

    fun reorderAccounts(orderedIds: List<String>) {
        viewModelScope.launch { repo.reorder(orderedIds) }
    }

    fun applyImport(toAdd: List<VaultAccount>, toUpdate: List<Pair<Account, VaultAccount>>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            repo.applyImport(toAdd, toUpdate)
            onDone(toAdd.size + toUpdate.size)
        }
    }

    // ---------------------------------------------------- settings actions

    fun setThemeMode(mode: String) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepo.setDynamicColor(enabled) }
    fun setBiometricLock(enabled: Boolean) = viewModelScope.launch { settingsRepo.setBiometricLock(enabled) }
    fun setClipboardClearSeconds(seconds: Int) = viewModelScope.launch { settingsRepo.setClipboardClearSeconds(seconds) }
    fun setPinVerifyMode(mode: String) = viewModelScope.launch { settingsRepo.setPinVerifyMode(mode) }
    fun setPinFixedTime(hour: Int, minute: Int) = viewModelScope.launch { settingsRepo.setPinFixedTime(hour, minute) }
    fun setDestroyMode(mode: String) = viewModelScope.launch { settingsRepo.setDestroyMode(mode) }
    fun setFailThreshold(threshold: Int) = viewModelScope.launch { settingsRepo.setFailThreshold(threshold) }

    fun setAppPin(pin: String) {
        pinManager.setPin(pin)
        viewModelScope.launch { settingsRepo.setPinFailCount(0) }
    }

    fun clearAppPin() {
        pinManager.clearPin()
        viewModelScope.launch {
            settingsRepo.setPinVerifyMode(AppSettings.PIN_VERIFY_OFF)
            settingsRepo.setPinFailCount(0)
        }
    }

    fun setSelfDestructPin(pin: String) {
        pinManager.setDestroyPin(pin)
    }
}
