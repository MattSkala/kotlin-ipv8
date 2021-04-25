package nl.tudelft.ipv8.attestation

import nl.tudelft.ipv8.attestation.revocation.AuthorityStore
import nl.tudelft.ipv8.attestation.revocation.RevocationBlob
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.ByteArrayKey
import nl.tudelft.ipv8.util.toKey

class Authority(
    val publicKey: PublicKey?,
    val hash: ByteArray,
    var version: Long = 0L,
    val recognized: Boolean = false,
)

class Revocations(
    val publicKeyHash: String,
    val encryptedVersion: ByteArray,
    val signature: String,
    val revocations: List<String>,
)

class AuthorityManager(val authorityDatabase: AuthorityStore) {

    private val trustedAuthorities = hashMapOf<ByteArrayKey, Authority>()
    private val lock = Object()

    fun loadTrustedAuthorities() {
        val authorities = authorityDatabase.getRecognizedAuthorities()
        synchronized(lock) {
            authorities.forEach {
                trustedAuthorities[it.hash.toKey()] = it
            }
        }
    }

    fun insertRevocations(publicKeyHash: ByteArray, versionNumber: Long, signature: ByteArray, revokedHashes: List<ByteArray>) {
        authorityDatabase.insertRevocations(publicKeyHash, versionNumber, signature, revokedHashes)
        if (this.trustedAuthorities.containsKey(publicKeyHash.toKey())) {
            this.trustedAuthorities[publicKeyHash.toKey()]!!.version = versionNumber
        }
    }

    fun getLatestRevocationPreviews(): Map<ByteArrayKey, Long> {
        val authorities = authorityDatabase.getKnownAuthorities()
        val localRefs = hashMapOf<ByteArrayKey, Long>()
        authorities.forEach {
            localRefs[it.hash.toKey()] = it.version
        }
        return localRefs
    }

    fun getRevocations(publicKeyHash: ByteArray, fromVersion: Long = 0): List<RevocationBlob> {
        val versions = authorityDatabase.getVersionsSince(publicKeyHash, fromVersion)
        return authorityDatabase.getRevocations(publicKeyHash, versions)
    }

    fun loadDefaultAuthorities() {
        TODO("Preinstalled Authorities yet to be designed.")
    }

    fun getAuthorities(): List<Authority> {
        return this.authorityDatabase.getKnownAuthorities()
    }

    fun getTrustedAuthorities(): List<Authority> {
        return this.trustedAuthorities.values.toList()
    }

    fun addTrustedAuthority(publicKey: PublicKey) {
        val hash = publicKey.keyToHash()
        if (!this.contains(hash)) {
            val localAuthority = authorityDatabase.getAuthorityByHash(hash)
            if (localAuthority == null) {
                authorityDatabase.insertAuthority(publicKey)
                synchronized(lock) {
                    this.trustedAuthorities[hash.toKey()] = Authority(publicKey, hash)
                }
            } else {
                authorityDatabase.recognizeAuthority(publicKey.keyToHash())
                synchronized(lock) {
                    this.trustedAuthorities[hash.toKey()] = localAuthority
                }
            }
        }
    }

    fun deleteTrustedAuthority(hash: ByteArray) {
        if (this.contains(hash)) {
            this.trustedAuthorities.remove(hash.toKey())
            this.authorityDatabase.disregardAuthority(hash)
        }
    }

    fun getTrustedAuthority(hash: ByteArray): Authority? {
        return this.trustedAuthorities.get(hash.toKey())
    }

    fun getAuthority(hash: ByteArray): Authority? {
        return this.trustedAuthorities.get(hash.toKey()) ?: authorityDatabase.getAuthorityByHash(hash)
    }

    fun deleteTrustedAuthority(publicKey: PublicKey) {
        return this.deleteTrustedAuthority(publicKey.keyToHash())
    }

    fun contains(hash: ByteArray): Boolean {
        return this.trustedAuthorities.containsKey(hash.toKey())
    }


}
