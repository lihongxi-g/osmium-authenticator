package com.safekey.authenticator.autofill

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.content.res.Configuration
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.safekey.authenticator.R
import com.safekey.authenticator.SafeKeyApp
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.security.AppLog
import com.safekey.authenticator.totp.Base32
import com.safekey.authenticator.totp.TotpGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Osmium as a system autofill service: when another app shows a one-time-code
 * field, we offer the user's Osmium accounts above the keyboard and fill the
 * current code after the user picks a suggestion (and, when the device
 * supports it, verifies their identity first).
 *
 * Design notes (verified against the official AutofillService and
 * Dataset.Builder references, 2026):
 *  - requests are stateless: the system binds per request and may kill the
 *    process after we reply, so nothing session-scoped is kept here;
 *  - a fill response of `null` means "no suggestions" and is the correct
 *    reply when no OTP-looking field is on screen;
 *  - the authentication IntentSender deliberately uses FLAG_MUTABLE: the
 *    platform must be able to fill authentication arguments into it
 *    (immutable would break the flow);
 *  - onSaveRequest must call SaveCallback immediately — Osmium never saves
 *    data belonging to other apps;
 *  - we never read or log field *contents*; only structural metadata.
 */
class OsmiumAutofillService : AutofillService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Parsed synchronously while the AssistStructure is guaranteed valid. */
    private class ParsedRequest(
        val packageName: String,
        val fieldInfos: List<FieldInfo>,
        val nodes: List<android.service.autofill.AssistStructure.ViewNode>,
        val detection: Detection,
    )

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val parsed = try {
            parseRequest(request)
        } catch (e: Exception) {
            AppLog.d("autofill: parse error ${e.javaClass.simpleName}: ${e.message}")
            null
        }
        if (parsed == null) {
            // No OTP-looking field — stay quiet (null = no suggestions).
            callback.onSuccess(null)
            return
        }

        scope.launch {
            val response = try {
                buildResponse(parsed)
            } catch (e: Exception) {
                AppLog.d("autofill: build error ${e.javaClass.simpleName}: ${e.message}")
                null
            }
            withContext(Dispatchers.Main) {
                if (cancellationSignal.isCanceled) {
                    AppLog.d("autofill: request cancelled pkg=${parsed.packageName}")
                } else {
                    callback.onSuccess(response)
                }
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Nothing to save: Osmium is an authenticator, not a credential store.
        // The framework requires an immediate callback (see the reference).
        callback.onSuccess()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ parse

    private fun parseRequest(request: FillRequest): ParsedRequest? {
        val contexts = request.fillContexts
        val structure = contexts.lastOrNull()?.structure ?: return null
        val app = applicationContext as? SafeKeyApp ?: return null

        val fields = ArrayList<FieldInfo>()
        val nodes = ArrayList<android.service.autofill.AssistStructure.ViewNode>()

        fun visit(node: android.service.autofill.AssistStructure.ViewNode?) {
            node ?: return
            val autofillId: AutofillId? = node.autofillId
            val autofillable = autofillId != null && node.autofillType != View.AUTOFILL_TYPE_NONE
            fields += FieldInfo(
                idEntry = node.idEntry,
                hintText = node.hint?.toString(),
                autofillHints = node.autofillHints?.toList() ?: emptyList(),
                rawInputType = node.inputType,
                isFocused = node.isFocused,
                isVisible = node.visibility == View.VISIBLE,
                autofillable = autofillable,
            )
            nodes += node
            for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }
        for (w in 0 until structure.windowNodeCount) {
            visit(structure.getWindowNodeAt(w)?.rootViewNode)
        }

        val detection = OtpFieldDetector.detect(fields) ?: return null
        val pkg = structure.activityComponent?.packageName ?: ""
        return ParsedRequest(pkg, fields, nodes, detection)
    }

    // ------------------------------------------------------------------ build

    private suspend fun buildResponse(parsed: ParsedRequest): FillResponse? {
        val app = applicationContext as SafeKeyApp
        val accounts = app.accountRepository.getAll().filterNot { it.hidden }
        if (accounts.isEmpty()) {
            AppLog.d("autofill: pkg=${parsed.packageName} matched but vault is empty")
            return null
        }

        val settings = app.settingsRepository.settings.first()
        val bindings = app.settingsRepository.autofillBindings.first()
        val boundId = bindings[parsed.packageName]

        val appLabel = try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(parsed.packageName, 0)).toString()
        } catch (_: Exception) {
            null
        }

        val ranked = AccountMatcher.rank(
            accounts.map { AccountLite(it.id, it.issuer, it.label) },
            boundId,
            appLabel,
        ).take(AccountMatcher.MAX_SUGGESTIONS)

        val byId = accounts.associateBy { it.id }
        val target = parsed.nodes.getOrNull(parsed.detection.index)?.autofillId ?: return null
        val authEnabled = DeviceAuth.canAuthenticate(this)

        val response = FillResponse.Builder()
        var added = 0
        for ((slot, lite) in ranked.withIndex()) {
            val account = byId[lite.id] ?: continue
            val code = generateCode(account, settings.timeOffsetSeconds) ?: continue
            val dataset = buildDataset(account, code, target, authEnabled, slot) ?: continue
            response.addDataset(dataset)
            added++
        }

        if (added == 0) {
            AppLog.d("autofill: pkg=${parsed.packageName} level=${parsed.detection.level} no datasets")
            return null
        }
        // Metadata only — never codes or secrets (privacy contract).
        AppLog.d(
            "autofill: pkg=${parsed.packageName} level=${parsed.detection.level} " +
                "reason=${parsed.detection.reason} datasets=$added auth=$authEnabled"
        )
        return response.build()
    }

    private fun generateCode(account: Account, timeOffsetSeconds: Int): String? = try {
        val secret = Base32.decode(account.secret)
        val adjustedNow = System.currentTimeMillis() + timeOffsetSeconds * 1000L
        TotpGenerator.generate(
            secret = secret,
            timeMs = adjustedNow,
            period = account.period,
            digits = account.digits,
            algorithm = account.algorithm,
            steamAlphabet = if (account.isSteam) TotpGenerator.STEAM_ALPHABET else null,
            // TOTP uses the time window; HOTP fills the stored counter value
            // (the same code the list shows — advancing stays a manual action).
            counter = if (account.isHotp) account.counter else null,
        )
    } catch (e: Exception) {
        AppLog.d("autofill: code generation failed for a row: ${e.javaClass.simpleName}")
        null
    }

    @Suppress("DEPRECATION") // Builder(RemoteViews) is the API 26+ path we must
    // support (minSdk 26); Presentations only exist on Android 13+.
    private fun buildDataset(
        account: Account,
        code: String,
        target: AutofillId,
        requireAuth: Boolean,
        slot: Int,
    ): Dataset? = try {
        val presentation = buildPresentation(account)
        val builder = Dataset.Builder(presentation)
        builder.setValue(target, AutofillValue.forText(code))
        if (requireAuth) {
            authIntentSender(slot)?.let { builder.setAuthentication(it) }
        }
        builder.build()
    } catch (e: Exception) {
        AppLog.d("autofill: dataset build failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    /**
     * Note the flags: the platform must be able to add authentication
     * arguments to this PendingIntent, so it CANNOT be FLAG_IMMUTABLE
     * (see Dataset.Builder#setAuthentication). Distinct request codes keep
     * the per-dataset intents from cancelling one another.
     */
    private fun authIntentSender(slot: Int): IntentSender? = try {
        val intent = Intent(this, AutofillAuthActivity::class.java)
        val mutability = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getActivity(
            this,
            slot,
            intent,
            mutability or PendingIntent.FLAG_CANCEL_CURRENT,
        )
        pi.intentSender
    } catch (e: Exception) {
        AppLog.d("autofill: auth intent failed: ${e.javaClass.simpleName}")
        null
    }

    private fun buildPresentation(account: Account): RemoteViews {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val titleColor = if (night) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
        val subtitleColor = if (night) 0xFFB0AEB3.toInt() else 0xFF5F6368.toInt()
        return RemoteViews(packageName, R.layout.autofill_dataset).apply {
            setTextViewText(R.id.autofill_title, account.displayTitle)
            setTextViewText(R.id.autofill_subtitle, account.displaySubtitle)
            setTextColor(R.id.autofill_title, titleColor)
            setTextColor(R.id.autofill_subtitle, subtitleColor)
        }
    }
}
