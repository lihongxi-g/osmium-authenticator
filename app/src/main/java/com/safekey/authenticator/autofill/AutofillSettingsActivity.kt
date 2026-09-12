package com.safekey.authenticator.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.safekey.authenticator.MainActivity

/**
 * Invisible trampoline referenced by res/xml/autofill_service.xml as the
 * service's settingsActivity: the "gear" next to Osmium in the system
 * autofill settings launches this, and it forwards straight to the main UI
 * opened on the Autofill page.
 *
 * Uses Theme.NoDisplay, so it must finish before onResume — done below.
 */
class AutofillSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_AUTOFILL_SETTINGS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (_: Exception) {
        }
        finish()
    }
}
