package com.safekey.authenticator.integrity

/**
 * K2 consistency analysis — pure functions over probe snapshots (no Android
 * dependencies), unit-tested on the JVM in IntegrityConsistencyTest.
 *
 * The K2 idea: a hiding framework can hook one API or doctor one proc view,
 * but keeping three independent views of the same kernel state consistent is
 * much harder. Divergences are weak evidence (WARN) — never a hard verdict.
 */
internal object IntegrityConsistency {

    /**
     * Suspicious markers for the mount views (same families as the K1 mount
     * scan). K2 only checks that every view agrees on the affected entries.
     */
    private val SUSPICIOUS_MOUNT_MARKERS = listOf(
        "magisk", "kernelsu", "susfs", "zygisk", "shamiko", "apatch", "/data/adb"
    )

    private val OCTAL_ESCAPE = Regex("\\\\([0-7]{3})")

    /** Decodes the octal escapes used by /proc mount tables (e.g. \040 = space). */
    fun unescapeMountField(field: String): String =
        OCTAL_ESCAPE.replace(field) { m -> m.groupValues[1].toInt(8).toChar().toString() }

    /**
     * Mount points of entries that carry a suspicious marker, from either
     * fstab-style (/proc/mounts, /proc/self/mounts: mount point = field 2)
     * or mountinfo style (mount point = field 5) rows.
     */
    fun suspiciousMountPoints(text: String, mountinfo: Boolean): Set<String> {
        if (text.isBlank()) return emptySet()
        val out = LinkedHashSet<String>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val lower = line.lowercase()
            if (SUSPICIOUS_MOUNT_MARKERS.none { it in lower }) continue
            val fields = line.trim().split(' ')
            val point = fields.getOrNull(if (mountinfo) 4 else 1)?.let(::unescapeMountField)
            if (!point.isNullOrEmpty()) out += point
        }
        return out
    }

    /**
     * Cross-checks the three mount views for divergence in suspicious
     * entries. Returns a human-readable detail, or null when the views agree
     * (or when nothing was readable — a miss, never a guess).
     */
    fun mountViewMismatch(mounts: String, selfMounts: String, mountinfo: String): String? {
        val views = listOf(
            "mounts" to mounts,
            "self/mounts" to selfMounts,
            "mountinfo" to mountinfo
        )
        val unreadable = views.filter { it.second.isBlank() }.map { it.first }
        if (unreadable.size == views.size) return null
        if (unreadable.isNotEmpty()) {
            return "view unreadable: " + unreadable.joinToString(", ")
        }
        val suspicious = views.map { (name, text) ->
            name to suspiciousMountPoints(text, mountinfo = name.contains("mountinfo"))
        }
        val reference = suspicious.first().second
        for ((_, points) in suspicious.drop(1)) {
            val diff = (reference - points) + (points - reference)
            if (diff.isNotEmpty()) {
                return "views disagree on: " + diff.sorted().joinToString(", ").take(140)
            }
        }
        return null
    }

    /**
     * Tri-state file route check: for every candidate path the three routes
     * (File.exists, Os.stat, parent directory listing) must agree. A split
     * verdict means at least one of those APIs is hooked.
     */
    fun fileRouteMismatch(routes: Map<String, List<Boolean>>): List<String> =
        routes.filterValues { verdicts -> verdicts.distinct().size > 1 }
            .keys
            .sorted()

    /**
     * Compares the early (K0) snapshot with the live re-read. Only entries
     * present on both sides count — a failed re-read is never a change.
     */
    fun driftChanges(
        beforeProps: Map<String, String>,
        afterProps: Map<String, String>,
        beforeBits: Map<String, Boolean>,
        afterBits: Map<String, Boolean>,
        beforeSuspiciousMounts: Set<String>,
        afterSuspiciousMounts: Set<String>
    ): List<String> {
        val out = mutableListOf<String>()
        for ((key, before) in beforeProps) {
            val after = afterProps[key] ?: continue
            if (before != after) out += "$key: $before -> $after"
        }
        for ((path, before) in beforeBits) {
            val after = afterBits[path] ?: continue
            if (before != after) out += "$path: $before -> $after"
        }
        if (beforeSuspiciousMounts != afterSuspiciousMounts) {
            val added = (afterSuspiciousMounts - beforeSuspiciousMounts).take(4)
            val gone = (beforeSuspiciousMounts - afterSuspiciousMounts).take(4)
            out += "mounts: +$added -$gone"
        }
        return out
    }
}
