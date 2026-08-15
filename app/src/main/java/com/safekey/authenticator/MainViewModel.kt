package com.safekey.authenticator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.security.AppLog
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A rendered account: domain data + the live code for the current tick. */
data class AccountUi(
    val account: Account,
    val code: String,
    val remainingSeconds: Int,
    val periodFraction: Float
) {
    val isHotp: Boolean get() = account.isHotp
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val safeKeyApp = app as SafeKeyApp
    private val repo = safeKeyApp.accountRepository
    private val settingsRepo = safeKeyApp.settingsRepository
    val pinManager = PinManager(app)
    private val selfDestruct = SelfDestructManager()

    val nav = NavigationState()

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    // UI tick, ~2 Hz — smooth countdown without recomputing codes constantly
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now

    private val tickJob: Job = viewModelScope.launch {
        while (true) {
            _now.value = System.currentTimeMillis()
            delay(500)
        }
    }

    val accounts: StateFlow<List<Account>> = repo.accounts
        .flowOn(Dispatchers.Default) // decryption + flow ops off the main thread
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accountUiList: StateFlow<List<AccountUi>> =
        combine(accounts, _now, settings) { list, time, s ->
            val adjusted = time + s.timeOffsetSeconds * 1000L
            list.map { a ->
                AccountUi(
                    account = a,
                    code = TotpGenerator.generate(
                        secret = Base32.decode(a.secret),
                        timeMs = adjusted,
                        period = a.period,
                        digits = a.digits,
                        algorithm = a.algorithm,
                        steamAlphabet = if (a.isSteam) TotpGenerator.STEAM_ALPHABET else null,
                        // TOTP: null → time-based window. HOTP: explicit counter.
                        // Passing 0 for TOTP accounts would pin every code to
                        // counter 0 forever (the "codes never change" bug).
                        counter = if (a.isHotp) a.counter else null
                    ),
                    remainingSeconds = if (a.isHotp) 0 else TotpGenerator.remainingSeconds(adjusted, a.period),
                    periodFraction = if (a.isHotp) 0f else TotpGenerator.periodFraction(adjusted, a.period)
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Display order, applying the selected sort mode. */
    val sortedAccountUiList: StateFlow<List<AccountUi>> =
        combine(accountUiList, settings) { list, s ->
            when (s.sortMode) {
                AppSettings.SORT_ALPHA -> list.sortedBy {
                    it.account.displayTitle.lowercase()
                }
                AppSettings.SORT_COPIES -> list.sortedByDescending { it.account.copyCount }
                AppSettings.SORT_RANDOM -> {
                    // shuffled once per launch (stable across ticks)
                    if (randomOrder.isEmpty()) {
                        randomOrder = list.map { it.account.id }.shuffled()
                    }
                    randomOrder.mapNotNull { id -> list.firstOrNull { it.account.id == id } }
                }
                else -> list // SORT_ADDED: repository order (sortOrder = add order)
            }
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- search state ----
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    /** Random-order shuffle, computed once per app launch (see sortMode). */
    private var randomOrder: List<String> = emptyList()

    // ---- gate state (every app open requires verification) ----
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked

    // ---- PIN gate state (overrides everything) ----
    private val _pinRequired = MutableStateFlow(false)
    val pinRequired: StateFlow<Boolean> = _pinRequired

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError

    private val _destroyed = MutableStateFlow(false)
    val destroyed: StateFlow<Boolean> = _destroyed

    /** True while the app is in the foreground. */
    private var appInForeground = false

    /** Set by the activity: does this device have a usable fingerprint/face? */
    private var biometricAvailable = false

    fun setBiometricAvailable(available: Boolean) {
        biometricAvailable = available
    }

    init {
        // Re-evaluate the gate whenever settings load/change — e.g. the user
        // just disabled "verify on open", or the DataStore first read lands
        // after onStart already locked the app.
        viewModelScope.launch {
            settings.collect { s ->
                if (_destroyed.value) return@collect
                if (!s.gateOnOpen) {
                    _locked.value = false
                    _pinRequired.value = false
                }
            }
        }
    }

    /**
     * Called when the activity comes to the foreground.
     * When "verify on open" is enabled: fingerprint first (or lock-screen
     * credential via the prompt), app PIN when no biometrics exist.
     */
    fun onAppForeground() {
        appInForeground = true
        AppLog.d("foreground: biometric=$biometricAvailable pin=${pinManager.hasPin()} gate=${settings.value.gateOnOpen}")
        if (_destroyed.value) return
        _pinError.value = null
        if (!settings.value.gateOnOpen) {
            _locked.value = false
            _pinRequired.value = false
            return
        }
        if (biometricAvailable) {
            _locked.value = true
        } else if (pinManager.hasPin()) {
            _pinRequired.value = true
        } else {
            // no fingerprint and no PIN on this device — nothing to verify with
            _locked.value = false
            _pinRequired.value = false
        }
    }

    /** Called when the activity goes to the background. */
    fun onAppBackground() {
        appInForeground = false
        AppLog.d("background")
        if (settings.value.gateOnOpen) _locked.value = true
    }

    fun unlock() {
        AppLog.d("unlocked")
        _locked.value = false
        _pinRequired.value = false
    }

    // ---------------------------------------------------------------- PIN

    /** Ask for the PIN right now (gate without biometrics). */
    fun requirePin() {
        if (pinManager.hasPin() && !_destroyed.value) {
            AppLog.d("pin gate shown")
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
            AppLog.d("pin verified")
            _pinRequired.value = false
            _pinError.value = null
            viewModelScope.launch {
                settingsRepo.setPinFailCount(0)
            }
            return true
        }
        // wrong PIN — count toward self-destruct threshold
        AppLog.d("pin WRONG")
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
        AppLog.d("biometric FAILED (mismatch)")
        viewModelScope.launch {
            val count = settings.value.biometricFailCount + 1
            settingsRepo.setBiometricFailCount(count)
            maybeSelfDestructOnFailures(settings.value.pinFailCount, count)
        }
    }

    /** Called on successful biometric authentication — resets the counter. */
    fun onBiometricSucceeded() {
        AppLog.d("biometric succeeded")
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

    /**
     * ANYWHERE a PIN is entered: if the self-destruct PIN is armed and the
     * entered PIN matches it, destroy all data. Returns true when destroyed.
     */
    fun checkSelfDestructPin(pin: String): Boolean {
        if (settings.value.destroyMode != AppSettings.DESTROY_PIN) return false
        if (!pinManager.hasDestroyPin()) return false
        if (pinManager.verifyDestroyPin(pin)) {
            selfDestruct()
            return true
        }
        return false
    }

    fun remainingAttempts(): Int? {
        if (settings.value.destroyMode != AppSettings.DESTROY_FAIL_COUNT) return null
        val remaining = settings.value.failThreshold -
            (settings.value.pinFailCount + settings.value.biometricFailCount)
        return remaining.coerceAtLeast(0)
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
        AppLog.d("SELF-DESTRUCT triggered")
        viewModelScope.launch {
            try {
                selfDestruct.destroyMasterKey()
                safeKeyApp.accountDao.deleteAll()
                pinManager.wipeAll()
                settingsRepo.wipeSettings()
                _pinRequired.value = false
                _locked.value = false
                _destroyed.value = true
                AppLog.d("self-destruct complete: key destroyed, data wiped")
            } catch (e: Exception) {
                // nothing sensible left to do — surface the state anyway
                AppLog.d("self-destruct error: ${e.message}")
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

    fun addAccount(
        issuer: String,
        label: String,
        secret: String,
        algorithm: String,
        digits: Int,
        period: Int,
        type: String = Account.TYPE_TOTP,
        counter: Long = 0
    ) {
        viewModelScope.launch {
            try {
                repo.add(issuer, label, secret, algorithm, digits, period, type, counter)
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
    fun setGateOnOpen(enabled: Boolean) = viewModelScope.launch { settingsRepo.setGateOnOpen(enabled) }
    fun setAllowScreenshots(enabled: Boolean) = viewModelScope.launch { settingsRepo.setAllowScreenshots(enabled) }
    fun setHideCodes(enabled: Boolean) = viewModelScope.launch { settingsRepo.setHideCodes(enabled) }
    fun setSortMode(mode: String) = viewModelScope.launch { settingsRepo.setSortMode(mode) }
    fun setTimeOffsetSeconds(offset: Int) = viewModelScope.launch { settingsRepo.setTimeOffsetSeconds(offset) }
    fun incrementCopyCount(id: String) = viewModelScope.launch { repo.incrementCopyCount(id) }
    fun incrementCounter(id: String) = viewModelScope.launch { repo.incrementCounter(id) }
    fun setDestroyMode(mode: String) = viewModelScope.launch { settingsRepo.setDestroyMode(mode) }
    fun setFailThreshold(threshold: Int) = viewModelScope.launch { settingsRepo.setFailThreshold(threshold) }

    fun setAppPin(pin: String) {
        pinManager.setPin(pin)
        viewModelScope.launch { settingsRepo.setPinFailCount(0) }
    }

    fun clearAppPin() {
        pinManager.clearPin()
        AppLog.d("app pin cleared")
        viewModelScope.launch {
            settingsRepo.setPinFailCount(0)
        }
    }

    fun setSelfDestructPin(pin: String) {
        pinManager.setDestroyPin(pin)
    }

    fun clearDestroyPin() {
        pinManager.clearDestroyPin()
        AppLog.d("destruct pin cleared")
    }
}
