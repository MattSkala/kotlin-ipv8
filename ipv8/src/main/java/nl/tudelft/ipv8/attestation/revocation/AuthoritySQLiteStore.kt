package nl.tudelft.ipv8.attestation.revocation

import mu.KotlinLogging
import nl.tudelft.ipv8.attestation.Authority
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.sqldelight.Database
import nl.tudelft.ipv8.sqldelight.GetAllRevocations
import nl.tudelft.ipv8.util.toHex

private val authorityMapper: (
    ByteArray?,
    ByteArray,
    Long?,
    Long?,
) -> Authority = { public_key, hash, version, recognized ->
    Authority(public_key?.let { defaultCryptoProvider.keyFromPublicBin(it) },
        hash,
        version ?: 0L,
        recognized?.toInt() == 1)
}

private val logger = KotlinLogging.logger {}

class AuthoritySQLiteStore(database: Database) : AuthorityStore {
    private val dao = database.dbAuthorityQueries

    override fun getKnownAuthorities(): List<Authority> {
        return dao.getAllAuthorities(authorityMapper).executeAsList()
    }

    override fun getRecognizedAuthorities(): List<Authority> {
        return dao.getAllRecognizedAuthorities(authorityMapper).executeAsList()
    }

    override fun getAuthorityByHash(hash: ByteArray): Authority? {
        return dao.getAuthorityByHash(hash, authorityMapper).executeAsOneOrNull()
    }

    override fun recognizeAuthority(hash: ByteArray) {
        return dao.recognizeAuthority(hash)
    }

    override fun disregardAuthority(hash: ByteArray) {
        return dao.disregardAuthority(hash)
    }

    override fun insertAuthority(publicKey: PublicKey) {
        return dao.insertAuthority(publicKey.keyToBin(), publicKey.keyToHash(), null, null)
    }

    override fun insertAuthority(hash: ByteArray) {
        return dao.insertAuthority(null, hash, null, null)
    }

    override fun insertRevocations(
        publicKeyHash: ByteArray,
        version: Long,
        signature: ByteArray,
        revokedHashes: List<ByteArray>,
    ) {
        var authorityId = dao.getAuthorityIdByHash(publicKeyHash).executeAsOneOrNull()
        if (authorityId == null) {
            this.insertAuthority(publicKeyHash)
            authorityId = dao.getAuthorityIdByHash(publicKeyHash).executeAsOne()
        }

        if (version >= 2L) {
            val previousId =
                dao.getVersionByAuthorityIDandVersionNumber(authorityId, version - 1L).executeAsOneOrNull()?.version_id
            if (previousId == null) {
                logger.warn("Received revocations out of order, skipping!")
                return
            }

        }

        var versionId =
            dao.getVersionByAuthorityIDandVersionNumber(authorityId, version).executeAsOneOrNull()?.version_id
        if (versionId == null) {
            dao.insertVersion(authorityId, version, signature)
            versionId = dao.getVersionByAuthorityIDandVersionNumber(authorityId, version).executeAsOne().version_id
        }

        revokedHashes.forEach { dao.insertRevocation(authorityId, versionId, it) }
        dao.updateVersionFor(versionId, publicKeyHash)
    }

    override fun getVersionsSince(publicKeyHash: ByteArray, sinceVersion: Long): List<Long> {
        val authorityId = dao.getAuthorityIdByHash(publicKeyHash).executeAsOneOrNull()
        return if (authorityId == null) {
            emptyList()
        } else
            dao.getVersionsSince(authorityId, sinceVersion).executeAsList()
    }

    override fun getRevocations(
        publicKeyHash: ByteArray,
        versions: List<Long>,
    ): List<RevocationBlob> {
        val authorityId = dao.getAuthorityIdByHash(publicKeyHash).executeAsOne()
        val versionEntries =
            dao.getVersionsByAuthorityIDandVersionNumbers(authorityId, versions).executeAsList()

        return versionEntries.map {
            RevocationBlob(publicKeyHash, it.version_number,
                it.signature,
                dao.getRevocationsByAuthorityIdAndVersionId(authorityId, it.version_id)
                    .executeAsList())
        }
    }

    override fun getAllRevocations(): List<GetAllRevocations> {
        return dao.getAllRevocations().executeAsList()
    }

}
