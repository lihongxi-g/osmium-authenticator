package com.safekey.authenticator.repository

import com.safekey.authenticator.database.AccountDao
import com.safekey.authenticator.database.AccountEntity
import com.safekey.authenticator.database.AccountTagCrossRef
import com.safekey.authenticator.database.TagDao
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.model.Tag
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.model.VaultTag
import com.safekey.authenticator.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Single source of truth for accounts: Room (encrypted at rest) ⇄ domain model.
 */
class AccountRepository(
    private val dao: AccountDao,
    private val crypto: CryptoManager,
    private val tagDao: TagDao? = null
) {

    /** All accounts, decrypted, ordered by sortOrder. */
    val accounts: Flow<List<Account>> =
        dao.observeAll().map { entities -> entities.mapNotNull { it.toDomain(crypto) } }

    suspend fun getAll(): List<Account> = dao.getAll().mapNotNull { it.toDomain(crypto) }

    suspend fun getById(id: String): Account? = dao.getById(id)?.toDomain(crypto)

    suspend fun add(
        issuer: String,
        label: String,
        secret: String,
        algorithm: String,
        digits: Int,
        period: Int,
        type: String = Account.TYPE_TOTP,
        counter: Long = 0,
        tagIds: Set<String> = emptySet()
    ): Account {
        val now = System.currentTimeMillis()
        val order = dao.maxSortOrder() + 1
        val account = Account(
            id = UUID.randomUUID().toString(),
            issuer = issuer.trim(),
            label = autoName(label.trim(), System.currentTimeMillis()),
            secret = secret.trim().uppercase().replace(" ", ""),
            algorithm = algorithm,
            digits = digits,
            period = period,
            sortOrder = order,
            createdAt = now,
            updatedAt = now,
            type = type,
            counter = counter
        )
        dao.insert(account.toEntity(crypto))
        tagDao?.insertRefs(tagIds.map { com.safekey.authenticator.database.AccountTagCrossRef(account.id, it) })
        return account
    }

    suspend fun update(account: Account, issuer: String, label: String, secret: String, algorithm: String, digits: Int, period: Int, tagIds: Set<String> = account.tags.map { it.id }.toSet(), counter: Long? = null) {
        val updated = account.copy(
            issuer = issuer.trim(),
            label = autoName(label.trim(), account.createdAt),
            secret = secret.trim().uppercase().replace(" ", ""),
            algorithm = algorithm,
            digits = digits,
            period = period,
            // HOTP: allow the UI to set a starting counter (e.g. migrating an
            // existing account mid-sequence). TOTP passes null → unchanged.
            counter = counter ?: account.counter,
            updatedAt = System.currentTimeMillis()
        )
        dao.update(updated.toEntity(crypto))
        tagDao?.deleteRefsForAccount(updated.id)
        tagDao?.insertRefs(tagIds.map { com.safekey.authenticator.database.AccountTagCrossRef(updated.id, it) })
    }

    suspend fun delete(account: Account) {
        dao.getById(account.id)?.let { dao.delete(it) }
    }

    /** Persist a new ordering (e.g. after drag reorder). */
    suspend fun reorder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id ->
            dao.updateSortOrder(id, index.toLong())
        }
    }

    /** Add accounts from an import plan (skipping none — UI pre-filters). */
    suspend fun applyImport(toAdd: List<VaultAccount>, toUpdate: List<Pair<Account, VaultAccount>>) {
        val now = System.currentTimeMillis()
        var order = dao.maxSortOrder()
        for (v in toAdd) {
            order += 1
            val account = Account(
                id = UUID.randomUUID().toString(),
                issuer = v.issuer.trim(),
                label = v.label.trim(),
                secret = v.secret.trim().uppercase(),
                algorithm = v.algorithm,
                digits = v.digits,
                period = v.period,
                sortOrder = order,
                createdAt = now,
                updatedAt = now,
                type = v.type,
                counter = v.counter
            )
            dao.insert(account.toEntity(crypto))
            tagDao?.insertRefs(v.tagIds.map { com.safekey.authenticator.database.AccountTagCrossRef(account.id, it) })
        }
        for ((existing, v) in toUpdate) {
            val updated = existing.copy(
                issuer = v.issuer.trim(),
                label = v.label.trim(),
                secret = v.secret.trim().uppercase(),
                algorithm = v.algorithm,
                digits = v.digits,
                period = v.period,
                updatedAt = now
            )
            dao.update(updated.toEntity(crypto))
            tagDao?.deleteRefsForAccount(updated.id)
            tagDao?.insertRefs(v.tagIds.map { com.safekey.authenticator.database.AccountTagCrossRef(updated.id, it) })
        }
    }

    /** Export all accounts; [pinSalt]/[pinHash] bind the app PIN to the file.
     *  Hidden accounts are excluded from backups entirely. */
    suspend fun exportVault(pinSalt: String = "", pinHash: String = ""): VaultFile {
        val domains = dao.getAll()
            .mapNotNull { entity -> entity.toDomain(crypto)?.let { entity.id to it } }
            .filter { !it.second.hidden }
        val refsByAccount = tagDao?.getRefsForAccounts(domains.map { it.first })?.groupBy { it.accountId }
            ?.mapValues { (_, refs) -> refs.map { it.tagId } } ?: emptyMap()
        val all = domains.map { (id, domain) ->
            VaultAccount(
                issuer = domain.issuer,
                label = domain.label,
                secret = domain.secret,
                algorithm = domain.algorithm,
                digits = domain.digits,
                period = domain.period,
                type = domain.type,
                counter = domain.counter,
                tagIds = refsByAccount[id].orEmpty()
            )
        }
        return VaultFile(
            version = 2,
            format = "osmium-vault",
            exportedAt = System.currentTimeMillis(),
            accounts = all,
            tags = tagDao?.getAll()?.map { VaultTag(it.id, it.name, it.color) } ?: emptyList(),
            pinSalt = pinSalt,
            pinHash = pinHash
        )
    }

    private fun Account.toEntity(crypto: CryptoManager): AccountEntity {
        val issuer = crypto.encrypt(issuer)
        val label = crypto.encrypt(label)
        val secret = crypto.encrypt(secret)
        return AccountEntity(
            id = id,
            issuerIv = issuer.iv,
            issuerCiphertext = issuer.ciphertext,
            labelIv = label.iv,
            labelCiphertext = label.ciphertext,
            secretIv = secret.iv,
            secretCiphertext = secret.ciphertext,
            algorithm = algorithm,
            digits = digits,
            period = period,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt,
            copyCount = copyCount,
            type = type,
            counter = counter,
            hidden = hidden
        )
    }

    private fun AccountEntity.toDomain(crypto: CryptoManager): Account? {
        return try {
            val issuer = crypto.decrypt(CryptoManager.EncryptedField(issuerIv, issuerCiphertext))
            val label = crypto.decrypt(CryptoManager.EncryptedField(labelIv, labelCiphertext))
            val secret = crypto.decrypt(CryptoManager.EncryptedField(secretIv, secretCiphertext))
            Account(
                id = id,
                issuer = issuer,
                label = label,
                secret = secret,
                algorithm = algorithm,
                digits = digits,
                period = period,
                sortOrder = sortOrder,
                createdAt = createdAt,
                updatedAt = updatedAt,
                copyCount = copyCount,
                type = type,
                counter = counter,
                hidden = hidden
            )
        } catch (e: Exception) {
            // One corrupt row must never take down the whole app on launch
            com.safekey.authenticator.security.AppLog.d("decrypt failed for account $id: ${e.message}")
            null
        }
    }


    /** Auto-generated account name: add date (YYYYMM) + add order (2 digits).
     *  e.g. the first account added in May 2026 becomes "20260501". */
    private suspend fun autoName(raw: String, timeMs: Long): String {
        if (raw.isNotBlank()) return raw
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timeMs }
        val ymd = "%04d%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
        val order = (dao.count() + 1).toString().padStart(2, '0')
        return ymd + order
    }

    suspend fun incrementCopyCount(id: String) {
        dao.incrementCopyCount(id)
    }

    suspend fun incrementCounter(id: String) {
        dao.incrementCounter(id)
    }
}
