package com.bsfreetv.launcher.domain.status

/** Public IP and, when known, its ISO 3166 country code ("NO"). */
data class IpInfo(val ip: String, val countryCode: String?)

/**
 * Parses Cloudflare's `/cdn-cgi/trace` reply: plain `key=value` lines, of
 * which only `ip` and `loc` are used. Null when there is no usable IP.
 */
object IpTrace {
    const val URL = "https://1.1.1.1/cdn-cgi/trace"
    const val MAX_BYTES = 4 * 1024

    private val ipChars = Regex("^[0-9A-Fa-f.:]{2,45}$")
    private val country = Regex("^[A-Z]{2}$")
    /** Cloudflare's "unknown" and "Tor" pseudo-countries. */
    private val notCountries = setOf("XX", "T1")

    fun parse(text: String): IpInfo? {
        if (text.length > MAX_BYTES) return null
        val fields = text.lineSequence()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
            .toMap()
        val ip = fields["ip"]?.takeIf { it.matches(ipChars) && (it.contains('.') || it.contains(':')) } ?: return null
        val loc = fields["loc"]?.uppercase()?.takeIf { it.matches(country) && it !in notCountries }
        return IpInfo(ip, loc)
    }
}

/** What the VPN pill shows after a press. */
sealed interface IpLookup {
    data object Idle : IpLookup
    data object Loading : IpLookup
    data class Done(val info: IpInfo) : IpLookup
    data object Failed : IpLookup
}

/** Status indicators the home screen can show next to the clock. */
data class StatusUi(
    val vpnShown: Boolean = false,
    val ip: IpLookup = IpLookup.Idle,
)
