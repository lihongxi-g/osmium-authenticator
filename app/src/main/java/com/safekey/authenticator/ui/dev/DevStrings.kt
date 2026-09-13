package com.safekey.authenticator.ui.dev

import android.content.Context
import com.safekey.authenticator.data.LanguagePrefs
import java.util.Locale

/**
 * Fixed two-language (English + Simplified Chinese) string table for the
 * developer-mode UI. Deliberately outside the localized resource system:
 * developer mode is only ever shown in English or Simplified Chinese —
 * every other app language (Traditional Chinese included) falls back to
 * English.
 */
data class DevStrings(
    val sectionTitle: String,
    val entryDesc: String,
    // status
    val rootStatusLabel: String,
    val rootStatusDetected: String,
    val rootStatusClean: String,
    val rootStatusUnknown: String,
    val lastChecked: String, // %s = formatted time
    val scanning: String,
    val restrictionActive: String,
    val restrictionLifted: String,
    // tool rows
    val runRootCheck: String,
    val runRootCheckDesc: String,
    val keystoreStatus: String,
    val keystoreStatusDesc: String,
    val disableRootSecurity: String,
    val disableRootSecurityDesc: String,
    val plaintextExport: String,
    val plaintextExportDesc: String,
    val reencrypt: String,
    val reencryptDesc: String,
    val hideFeatures: String,
    val hideFeaturesDesc: String,
    val extraDigits: String,
    val extraDigitsDesc: String,
    val exitDevMode: String,
    val exitDevModeDesc: String,
    // common
    val on: String,
    val off: String,
    val confirm: String,
    val cancel: String,
    val close: String,
    val continueLabel: String,
    val enable: String,
    val copy: String,
    val copied: String,
    // ceremony
    val verifyTitle: String,
    val disclaimerTitle: String,
    val disclaimerBody: String,
    val disclaimerAccept: String,
    val phraseTitle: String,
    val phraseInstruction: String,
    val phraseMismatch: String,
    val phraseDisableRootSecurity: String,
    val phrasePlaintextExport: String,
    val phraseReencrypt: String,
    // root report
    val reportTitle: String,
    val reportHint: String,
    val reportEmpty: String,
    val tierStrong: String,
    val tierInfo: String,
    val hitLabel: String,
    val missLabel: String,
    // keystore status
    val ksAlias: String,
    val ksPresent: String,
    val ksMissing: String,
    val ksSecurityLevel: String,
    val ksSelfTest: String,
    val ksOk: String,
    val ksFail: String,
    val ksDbFields: String,
    val ksAllEncrypted: String,
    val ksIssuesFound: String,
    // plaintext export
    val plainExportWarningTitle: String,
    val plainExportWarningBody: String,
    val plainExportAccept: String,
    val plainExportToast: String,
    // misc results
    val devEnabled: String,
    val devDisabled: String,
    val hideSaved: String,
    val hideHint: String,
    val reencryptDonePrefix: String,
    val reencryptFailed: String,
    val reencryptRunning: String
) {
    companion object {
        fun forContext(context: Context): DevStrings {
            val tag = LanguagePrefs.get(context) ?: Locale.getDefault().toLanguageTag()
            return forLanguageTag(tag)
        }

        /** Simplified Chinese only; everything else (zh-Hant included) is English. */
        fun forLanguageTag(tag: String): DevStrings {
            val t = tag.lowercase()
            val simplifiedChinese = t.startsWith("zh") &&
                !t.contains("hant") && !t.contains("tw") &&
                !t.contains("hk") && !t.contains("mo")
            return if (simplifiedChinese) ZH else EN
        }

        private val EN = DevStrings(
            sectionTitle = "Developer mode",
            entryDesc = "Advanced tools; risky actions need multiple confirmations",
            rootStatusLabel = "Root status",
            rootStatusDetected = "Root detected",
            rootStatusClean = "Not detected",
            rootStatusUnknown = "Unknown",
            lastChecked = "Last check: %s",
            scanning = "Scanning…",
            restrictionActive = "Root security restrictions are active",
            restrictionLifted = "Root security restrictions are lifted",
            runRootCheck = "Run root detection",
            runRootCheckDesc = "Show every signal and its raw values",
            keystoreStatus = "Keystore encryption status",
            keystoreStatusDesc = "Hardware backing, key info, database state",
            disableRootSecurity = "Disable root security restrictions",
            disableRootSecurityDesc = "Lift the forced security features and the blocked list",
            plaintextExport = "Plaintext export",
            plaintextExportDesc = "All exports become unencrypted — dangerous",
            reencrypt = "Re-encrypt Keystore database",
            reencryptDesc = "Decrypt and re-encrypt every stored field with fresh IVs",
            hideFeatures = "Hide settings entries",
            hideFeaturesDesc = "Hide selected entries from the Settings screen",
            extraDigits = "Allow 4/5/7-digit codes",
            extraDigitsDesc = "Accept and generate 4, 5 and 7 digit codes",
            exitDevMode = "Turn off developer mode",
            exitDevModeDesc = "Also resets the dangerous toggles",
            on = "On",
            off = "Off",
            confirm = "Confirm",
            cancel = "Cancel",
            close = "Close",
            continueLabel = "Continue",
            enable = "Enable",
            copy = "Copy",
            copied = "Copied",
            verifyTitle = "Verify your identity",
            disclaimerTitle = "Risk disclaimer",
            disclaimerBody = "These features weaken the app's defenses or expose your secrets. " +
                "Only enable them if you fully understand the consequences and accept the risk. " +
                "A lifted restriction or a plaintext export can let other apps read your accounts. " +
                "Osmium and its developer accept no liability for any loss caused by using developer mode.",
            disclaimerAccept = "I have read the disclaimer and accept the risks",
            phraseTitle = "Type to confirm",
            phraseInstruction = "Type the phrase below exactly:",
            phraseMismatch = "The phrase doesn't match — try again.",
            phraseDisableRootSecurity = "Disable the restrictions",
            phrasePlaintextExport = "Export in plaintext",
            phraseReencrypt = "Re-encrypt now",
            reportTitle = "Root detection report",
            reportHint = "Raw signal values for troubleshooting. Contains no secrets.",
            reportEmpty = "No report yet — run a detection first.",
            tierStrong = "STRONG",
            tierInfo = "INFO",
            hitLabel = "HIT",
            missLabel = "miss",
            ksAlias = "Keystore alias",
            ksPresent = "Present",
            ksMissing = "Missing",
            ksSecurityLevel = "Security level",
            ksSelfTest = "Encrypt/decrypt self-test",
            ksOk = "Passed",
            ksFail = "Failed",
            ksDbFields = "Encrypted database fields",
            ksAllEncrypted = "All encrypted fields OK",
            ksIssuesFound = "Problems found",
            plainExportWarningTitle = "Plaintext export",
            plainExportWarningBody = "The exported file will contain ALL your secrets in plaintext. " +
                "Anyone who obtains it can read every account. Keep it safe and delete it right after use.",
            plainExportAccept = "I understand — export in plaintext",
            plainExportToast = "Exported as plaintext",
            devEnabled = "Developer mode enabled",
            devDisabled = "Developer mode turned off",
            hideSaved = "Saved",
            hideHint = "Checked entries won't appear in Settings. Uncheck to restore.",
            reencryptDonePrefix = "Re-encrypted fields: ",
            reencryptFailed = "Re-encryption failed — nothing was changed",
            reencryptRunning = "Re-encrypting…"
        )

        private val ZH = DevStrings(
            sectionTitle = "开发者模式",
            entryDesc = "高级工具；危险操作需多重确认",
            rootStatusLabel = "Root 状态",
            rootStatusDetected = "检测到 Root",
            rootStatusClean = "未检测到",
            rootStatusUnknown = "未知",
            lastChecked = "最近检测：%s",
            scanning = "检测中…",
            restrictionActive = "Root 安全限制生效中",
            restrictionLifted = "Root 安全限制已解除",
            runRootCheck = "手动运行 Root 检测",
            runRootCheckDesc = "查看每一项信号及其原始值",
            keystoreStatus = "KeyStore 加密状态检测",
            keystoreStatusDesc = "硬件支持、密钥属性、数据库加密现状",
            disableRootSecurity = "关闭检测到 Root 后的安全限制",
            disableRootSecurityDesc = "解除强制开启的安全功能和禁用列表",
            plaintextExport = "导出明文密钥",
            plaintextExportDesc = "所有导出方式均输出明文——危险",
            reencrypt = "强制重新加密 KeyStore 数据库",
            reencryptDesc = "用新 IV 重新加密全部已加密字段",
            hideFeatures = "隐藏设置项",
            hideFeaturesDesc = "把选定条目从设置界面隐藏",
            extraDigits = "支持 4/5/7 位密钥",
            extraDigitsDesc = "接受并生成 4、5、7 位验证码",
            exitDevMode = "关闭开发者模式",
            exitDevModeDesc = "同时重置危险开关",
            on = "已开启",
            off = "已关闭",
            confirm = "确认",
            cancel = "取消",
            close = "关闭",
            continueLabel = "继续",
            enable = "开启",
            copy = "复制",
            copied = "已复制",
            verifyTitle = "验证身份",
            disclaimerTitle = "免责声明",
            disclaimerBody = "这些功能会削弱应用防护或直接暴露你的密钥。请仅在完全理解后果并自愿承担风险的前提下开启。" +
                "解除限制或明文导出可能导致其他应用读取你的账户。因使用开发者模式造成的任何损失，Osmium 及其开发者不承担责任。",
            disclaimerAccept = "我已阅读免责声明并自愿承担风险",
            phraseTitle = "输入确认",
            phraseInstruction = "请逐字输入下方短语：",
            phraseMismatch = "短语不匹配，请重试。",
            phraseDisableRootSecurity = "关闭安全限制",
            phrasePlaintextExport = "导出明文密钥",
            phraseReencrypt = "立即重新加密",
            reportTitle = "Root 检测报告",
            reportHint = "以下为排障用原始信号值，不含任何密钥。",
            reportEmpty = "暂无报告——请先运行一次检测。",
            tierStrong = "强信号",
            tierInfo = "参考",
            hitLabel = "命中",
            missLabel = "未命中",
            ksAlias = "密钥别名",
            ksPresent = "存在",
            ksMissing = "缺失",
            ksSecurityLevel = "安全级别",
            ksSelfTest = "加解密自检",
            ksOk = "通过",
            ksFail = "失败",
            ksDbFields = "数据库加密字段",
            ksAllEncrypted = "加密字段全部正常",
            ksIssuesFound = "发现问题",
            plainExportWarningTitle = "明文导出",
            plainExportWarningBody = "导出的文件将以明文包含你全部密钥。任何获得该文件的人都能读取所有账户。请妥善保管，用后立即删除。",
            plainExportAccept = "我已知悉——明文导出",
            plainExportToast = "已按明文导出",
            devEnabled = "开发者模式已开启",
            devDisabled = "开发者模式已关闭",
            hideSaved = "已保存",
            hideHint = "勾选的条目将不在设置界面显示；取消勾选可恢复。",
            reencryptDonePrefix = "已重新加密字段数：",
            reencryptFailed = "重新加密失败——数据未做任何改动",
            reencryptRunning = "重新加密中…"
        )
    }
}
