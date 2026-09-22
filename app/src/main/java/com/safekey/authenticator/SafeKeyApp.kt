package com.safekey.authenticator

import android.app.Application
import com.safekey.authenticator.database.AppDatabase
import com.safekey.authenticator.data.SettingsRepository
import com.safekey.authenticator.integrity.IntegrityEarly
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
    lateinit var tagRepository: com.safekey.authenticator.repository.TagRepository
        private set
    lateinit var lanBlockRepository: com.safekey.authenticator.data.LanBlockRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // K0 early snapshot on a dedicated thread: capture the earliest
        // observable system state without ever stalling app start (the
        // capture spawns getprop once).
        Thread { IntegrityEarly.capture() }.apply {
            isDaemon = true
            name = "integrity-early"
        }.start()
        AppLog.init(this)
        installCrashHandler()
        AppLog.d("app start v${BuildConfig.VERSION_NAME}")
        val db = AppDatabase.get(this)
        accountDao = db.accountDao()
        val crypto = CryptoManager()
        accountRepository = AccountRepository(accountDao, crypto, db.tagDao(), db)
        tagRepository = com.safekey.authenticator.repository.TagRepository(db.tagDao(), db)
        settingsRepository = SettingsRepository(this, crypto)
        lanBlockRepository = com.safekey.authenticator.data.LanBlockRepository(this, crypto)
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
