package com.safekey.authenticator.autofill

/** Minimal account shape for the matcher — keeps this layer JVM-pure. */
data class AccountLite(val id: String, val issuer: String, val label: String)

/**
 * Orders accounts for the app requesting autofill:
 *  1. an explicit per-app binding always wins;
 *  2. otherwise name similarity between the target app's label and the
 *     account's issuer/label decides;
 *  3. ties keep the original order (stable sort), so the list stays
 *     predictable across suggestions.
 */
object AccountMatcher {

    /** How many account suggestions one fill request may offer. */
    const val MAX_SUGGESTIONS = 3

    fun rank(
        accounts: List<AccountLite>,
        boundAccountId: String?,
        appLabel: String?,
    ): List<AccountLite> {
        val rawTarget = appLabel.orEmpty()
        val target = normalize(rawTarget)
        return accounts
            .map { account ->
                val score = when {
                    account.id == boundAccountId -> 1000
                    target.isEmpty() -> 0
                    else -> nameScore(account, target, rawTarget)
                }
                account to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun nameScore(account: AccountLite, target: String, rawTarget: String): Int {
        val issuer = normalize(account.issuer)
        val label = normalize(account.label)
        return when {
            issuer.isEmpty() && label.isEmpty() -> 0
            issuer == target || label == target -> 500
            issuer.isNotEmpty() && (issuer in target || target in issuer) -> 400
            label.isNotEmpty() && (label in target || target in label) -> 300
            tokensOverlap(issuer, label, rawTarget) -> 200
            else -> 0
        }
    }

    private fun tokensOverlap(issuer: String, label: String, rawTarget: String): Boolean {
        val tokens = rawTarget.lowercase()
            .split(Regex("[^a-z0-9\\u4e00-\\u9fff]+"))
            .filter { it.length >= 3 }
        return tokens.any { t ->
            (issuer.isNotEmpty() && (t in issuer || issuer in t)) ||
                (label.isNotEmpty() && (t in label || label in t))
        }
    }

    /** Lowercase, strip spaces/punctuation; keeps letters, digits and CJK. */
    internal fun normalize(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9\\u4e00-\\u9fff]+"), "")
}
