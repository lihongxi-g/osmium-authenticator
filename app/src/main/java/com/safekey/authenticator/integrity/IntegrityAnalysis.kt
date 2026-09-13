package com.safekey.authenticator.integrity

/**
 * Pure text analysis for the K1 probes — no Android dependencies, so every
 * rule here is unit-tested on the JVM (IntegrityAnalysisTest).
 */
internal object IntegrityAnalysis {

    private val MOUNT_TOKENS = listOf("magisk", "kernelsu", "susfs", "zygisk", "shamiko", "apatch")
    private val MAP_TOKENS = listOf("magisk", "zygisk", "lsposed", "riru", "shamiko", "kernelsu", "susfs")
    private val KERNEL_TOKENS = listOf("kernelsu", "susfs", "apatch", "magisk")

    /** Whole-partition mount targets that stock Android mounts read-only. */
    private val RW_SYSTEM_TARGETS = listOf("/system", "/system_ext", "/vendor", "/product", "/odm")

    fun mountsTokens(text: String): Set<String> = matchTokens(text, MOUNT_TOKENS)

    fun mapsTokens(text: String): Set<String> = matchTokens(text, MAP_TOKENS)

    fun kernelTokens(text: String): Set<String> = matchTokens(text, KERNEL_TOKENS)

    private fun matchTokens(text: String, tokens: List<String>): Set<String> {
        if (text.isEmpty()) return emptySet()
        val lower = text.lowercase()
        return tokens.filterTo(LinkedHashSet()) { lower.contains(it) }
    }

    /**
     * System partitions mounted read-write. Stock builds mount them "ro";
     * a live root stack (or an adb remount) flips one to "rw". Only exact
     * partition targets count — file-level binds are covered by the token
     * checks instead.
     */
    fun rwSystemMounts(mountsText: String): List<String> {
        val out = LinkedHashSet<String>()
        for (raw in mountsText.lineSequence()) {
            val fields = raw.trim().split(' ').filter { it.isNotEmpty() }
            if (fields.size < 4) continue
            if (fields[1] !in RW_SYSTEM_TARGETS) continue
            if ("rw" in fields[3].split(',')) out += fields[1]
        }
        return out.toList()
    }

    /** SELinux enforce flag: "0" = permissive. Unreadable values never flag. */
    fun selinuxPermissive(enforce: String?): Boolean = enforce?.trim() == "0"

    /**
     * Boot/debug properties that indicate a modified system (informational).
     * Reads fail-closed: an empty/absent value is never reported.
     */
    fun propFindings(props: Map<String, String>): List<String> {
        val out = mutableListOf<String>()
        fun value(key: String) = props[key]?.trim().orEmpty()

        val bootState = value("ro.boot.verifiedbootstate")
        if (bootState.isNotEmpty() && !bootState.equals("green", ignoreCase = true)) {
            out += "verifiedbootstate=$bootState"
        }
        if (value("ro.boot.vbmeta.device_state").equals("unlocked", ignoreCase = true)) {
            out += "vbmeta.device_state=unlocked"
        }
        if (value("ro.boot.flash.locked") == "0") out += "flash.locked=0"
        if (value("ro.debuggable") == "1") out += "ro.debuggable=1"
        if (value("ro.secure") == "0") out += "ro.secure=0"
        return out
    }

    /** Build.TAGS / Build.TYPE findings (informational). */
    fun buildTagFindings(tags: String, type: String): List<String> {
        val out = mutableListOf<String>()
        if (tags.contains("test-keys")) out += "build.tags=test-keys"
        if (type.isNotEmpty() && !type.equals("user", ignoreCase = true)) out += "build.type=$type"
        return out
    }
}
