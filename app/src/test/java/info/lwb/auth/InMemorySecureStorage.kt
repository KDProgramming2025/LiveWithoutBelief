package info.lwb.auth

/** Simple in-memory implementation for unit tests (avoids Android Crypto dependencies). */
class InMemorySecureStorage : SecureStorage {
    private var token: String? = null
    private var exp: Long? = null
    private var name: String? = null
    private var email: String? = null
    private var avatar: String? = null

    override fun putIdToken(token: String) { this.token = token }
    override fun getIdToken(): String? = token
    override fun putTokenExpiry(epochSeconds: Long?) { exp = epochSeconds }
    override fun getTokenExpiry(): Long? = exp
    override fun clear() { token = null; exp = null; name = null; email = null; avatar = null }
    override fun putProfile(name: String?, email: String?, avatar: String?) { this.name = name; this.email = email; this.avatar = avatar }
    override fun getProfile(): Triple<String?, String?, String?> = Triple(name, email, avatar)
}