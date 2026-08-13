package com.safekey.authenticator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.totp.Base32
import com.safekey.authenticator.totp.TotpGenerator
import com.safekey.authenticator.ui.navigation.NavigationState
import com.safekey.authenticator.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val nav = NavigationState()

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val accounts: StateFlow<List<Account>> = repo.accounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // UI tick, ~4 Hz — smooth countdown/progress without recomputing codes constantly
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now

    init {
        viewModelScope.launch {
            while (true) {
                _now.value = System.currentTimeMillis()
                delay(250)
            }
        }
    }

    /** Live codes for every account, recomputed each tick. */
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
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- search state ----
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    // ---- lock state ----
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    /** Called when the activity comes to the foreground: lock if biometric lock is on. */
    fun onAppForeground() {
        if (settings.value.biometricLock) _locked.value = true
    }

    /** Called when the activity goes to the background: always re-lock if enabled. */
    fun onAppBackground() {
        if (settings.value.biometricLock) _locked.value = true
    }

    fun unlock() { _locked.value = false }

    // ---- toast ----
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast
    fun showToast(message: String) { _toast.value = message }
    fun clearToast() { _toast.value = null }

    // ---- account actions ----
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

    // ---- settings actions ----
    fun setThemeMode(mode: String) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepo.setDynamicColor(enabled) }
    fun setBiometricLock(enabled: Boolean) = viewModelScope.launch { settingsRepo.setBiometricLock(enabled) }
    fun setClipboardClearSeconds(seconds: Int) = viewModelScope.launch { settingsRepo.setClipboardClearSeconds(seconds) }
}
