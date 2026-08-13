package com.safekey.authenticator

import android.app.Application
import com.safekey.authenticator.database.AppDatabase
import com.safekey.authenticator.data.SettingsRepository
import com.safekey.authenticator.repository.AccountRepository
import com.safekey.authenticator.security.AppLog
import com.safekey.authenticator.security.CryptoManager

class SafeKeyApp : Application() {

    lateinit var accountRepository: AccountRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var accountDao: com.safekey.authenticator.database.AccountDao
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        installCrashHandler()
        AppLog.d("app start v${BuildConfig.VERSION_NAME}")
        val db = AppDatabase.get(this)
        accountDao = db.accountDao()
        accountRepository = AccountRepository(accountDao, CryptoManager())
        settingsRepository = SettingsRepository(this)
    }

    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.d("CRASH on ${thread.name}: ${throwable.javaClass.simpleName}: ${throwable.message}")
            throwable.stackTrace.take(24).forEach { AppLog.d("  at $it") }
            AppLog.persistCrash()
            default?.uncaughtException(thread, throwable)
        }
    }
}
