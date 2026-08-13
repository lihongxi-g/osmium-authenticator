package com.safekey.biotest

import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 72)
        }

        fun tv(text: String, size: Float = 14f): TextView = TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, 10, 0, 10)
        }

        fun btn(text: String, action: () -> Unit): Button = Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }

        col.addView(tv("BioTest 指纹诊断", 20f))
        col.addView(tv("设备: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.SDK_INT} (API ${Build.VERSION.SDK_INT})"))

        val caps = tv("检测中…")
        col.addView(caps)

        col.addView(btn("1️⃣ 指纹 (STRONG, 带取消按钮)") {
            launchPrompt("纯指纹", BiometricManager.Authenticators.BIOMETRIC_STRONG, withNegative = true)
        })
        col.addView(btn("2️⃣ 指纹或密码 (官方推荐组合)") {
            launchPrompt("指纹或密码", BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL, withNegative = false)
        })
        col.addView(btn("3️⃣ 仅锁屏密码") {
            launchPrompt("仅密码", BiometricManager.Authenticators.DEVICE_CREDENTIAL, withNegative = false)
        })
        col.addView(btn("4️⃣ FLAG_SECURE 开 + 指纹 (对照实验)") {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            log("FLAG_SECURE 已开启")
            launchPrompt("FLAG_SECURE+指纹", BiometricManager.Authenticators.BIOMETRIC_STRONG, withNegative = true)
        })
        col.addView(btn("5️⃣ 关闭 FLAG_SECURE") {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            log("FLAG_SECURE 已关闭")
        })
        col.addView(btn("🧹 清空日志") {
            logView.text = "— 日志已清空 —\n"
        })

        col.addView(tv("————— 日志 —————", 16f))
        logView = tv("")
        col.addView(logView)

        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)

        // capability report
        fun check(name: String, authenticators: Int?): String {
            val r = if (authenticators != null) {
                BiometricManager.from(this).canAuthenticate(authenticators)
            } else {
                BiometricManager.from(this).canAuthenticate()
            }
            val rs = when (r) {
                BiometricManager.BIOMETRIC_SUCCESS -> "SUCCESS ✅"
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "NO_HARDWARE ❌"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "HW_UNAVAILABLE ❌"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "NONE_ENROLLED ⚠️ (没录指纹/没设密码)"
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "UNSUPPORTED ❌"
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "SECURITY_UPDATE_REQUIRED ❌"
                else -> "code=$r"
            }
            return "$name → $rs"
        }

        caps.text = buildString {
            appendLine("—— 能力检测 ——")
            appendLine(check("BIOMETRIC_STRONG", BiometricManager.Authenticators.BIOMETRIC_STRONG))
            appendLine(check("BIOMETRIC_WEAK", BiometricManager.Authenticators.BIOMETRIC_WEAK))
            appendLine(check("DEVICE_CREDENTIAL", BiometricManager.Authenticators.DEVICE_CREDENTIAL))
            appendLine(check("STRONG|CREDENTIAL 组合", BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL))
        }
    }

    private fun log(msg: String) {
        logView.text = "${logView.text}\n$msg"
    }

    private fun launchPrompt(label: String, authenticators: Int, withNegative: Boolean) {
        log("=== 调用: $label ===")
        try {
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    log("✅ 成功! authenticationType=${result.authenticationType}")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val name = when (errorCode) {
                        BiometricPrompt.ERROR_CANCELED -> "ERROR_CANCELED"
                        BiometricPrompt.ERROR_USER_CANCELED -> "ERROR_USER_CANCELED"
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "ERROR_NEGATIVE_BUTTON"
                        BiometricPrompt.ERROR_HW_NOT_PRESENT -> "ERROR_HW_NOT_PRESENT"
                        BiometricPrompt.ERROR_HW_UNAVAILABLE -> "ERROR_HW_UNAVAILABLE"
                        BiometricPrompt.ERROR_LOCKOUT -> "ERROR_LOCKOUT"
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "ERROR_LOCKOUT_PERMANENT"
                        BiometricPrompt.ERROR_NO_BIOMETRICS -> "ERROR_NO_BIOMETRICS"
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> "ERROR_NO_DEVICE_CREDENTIAL"
                        BiometricPrompt.ERROR_NO_SPACE -> "ERROR_NO_SPACE"
                        BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED -> "ERROR_SECURITY_UPDATE_REQUIRED"
                        BiometricPrompt.ERROR_TIMEOUT -> "ERROR_TIMEOUT"
                        BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> "ERROR_UNABLE_TO_PROCESS"
                        BiometricPrompt.ERROR_VENDOR -> "ERROR_VENDOR"
                        else -> "ERROR_$errorCode"
                    }
                    log("❌ $name: $errString")
                }

                override fun onAuthenticationFailed() {
                    log("⚠️ 指纹不匹配")
                }
            }
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle("BioTest")
                .setSubtitle(label)
                .setAllowedAuthenticators(authenticators)
            if (withNegative) {
                builder.setNegativeButtonText("取消")
            }
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            log("💥 异常: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
