package com.safekey.authenticator

import android.app.Application
import com.safekey.authenticator.database.AppDatabase
import com.safekey.authenticator.data.SettingsRepository
import com.safekey.authenticator.repository.AccountRepository
import com.safekey.authenticator.security.CryptoManager

class SafeKeyApp : Application() {

    lateinit var accountRepository: AccountRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        accountRepository = AccountRepository(db.accountDao(), CryptoManager())
        settingsRepository = SettingsRepository(this)
    }
}
