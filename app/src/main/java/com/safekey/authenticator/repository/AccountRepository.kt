package com.safekey.authenticator.repository

import com.safekey.authenticator.database.AccountDao
import com.safekey.authenticator.database.AccountEntity
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Single source of truth for accounts: Room (encrypted at rest) ⇄ domain model.
 */
class AccountRepository(
    private val dao: AccountDao,
    private val crypto: CryptoManager
) {

    /** All accounts, decrypted, ordered by sortOrder. */
    val accounts: Flow<List<Account>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain(crypto) } }

    suspend fun getAll(): List<Account> = dao.getAll().map { it.toDomain(crypto) }

    suspend fun getById(id: String): Account? = dao.getById(id)?.toDomain(crypto)

    suspend fun add(
        issuer: String,
        label: String,
        secret: String,
        algorithm: String,
        digits: Int,
        period: Int,
        type: String = Account.TYPE_TOTP,
        counter: Long = 0
    ): Account {
        val now = System.currentTimeMillis()
        val order = dao.maxSortOrder() + 1
        val account = Account(
            id = UUID.randomUUID().toString(),
            issuer = issuer.trim(),
            label = label.trim(),
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
        return account
    }

    suspend fun update(account: Account, issuer: String, label: String, secret: String, algorithm: String, digits: Int, period: Int) {
        val updated = account.copy(
            issuer = issuer.trim(),
            label = label.trim(),
            secret = secret.trim().uppercase().replace(" ", ""),
            algorithm = algorithm,
            digits = digits,
            period = period,
            updatedAt = System.currentTimeMillis()
        )
        dao.update(updated.toEntity(crypto))
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
        }
    }

    /** Export all accounts; [pinSalt]/[pinHash] bind the app PIN to the file. */
    suspend fun exportVault(pinSalt: String = "", pinHash: String = ""): VaultFile {
        val all = dao.getAll().map { entity ->
            val domain = entity.toDomain(crypto)
            VaultAccount(
                issuer = domain.issuer,
                label = domain.label,
                secret = domain.secret,
                algorithm = domain.algorithm,
                digits = domain.digits,
                period = domain.period,
                type = domain.type,
                counter = domain.counter
            )
        }
        return VaultFile(
            version = 1,
            format = "osmium-vault",
            exportedAt = System.currentTimeMillis(),
            accounts = all,
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

    private fun AccountEntity.toDomain(crypto: CryptoManager): Account {
        val issuer = crypto.decrypt(CryptoManager.EncryptedField(issuerIv, issuerCiphertext))
        val label = crypto.decrypt(CryptoManager.EncryptedField(labelIv, labelCiphertext))
        val secret = crypto.decrypt(CryptoManager.EncryptedField(secretIv, secretCiphertext))
        return Account(
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
    }

    suspend fun incrementCopyCount(id: String) {
        dao.incrementCopyCount(id)
    }

    suspend fun incrementCounter(id: String) {
        dao.incrementCounter(id)
    }
}
