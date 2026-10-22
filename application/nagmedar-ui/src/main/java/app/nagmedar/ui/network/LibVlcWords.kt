package app.nagmedar.ui.network

import android.content.res.Resources
import app.nagmedar.ui.R

/**
 * S-1301: libVLC's own words in its questions, which reach Android in English, said in the
 * interface's language where Nağmedar knows them: the server certificate that TLS cannot vouch
 * for (gnutls' "Insecure site", its buttons and its two texts). Anything else is shown as libVLC
 * wrote it.
 */
object LibVlcWords {

    /** libVLC's labels, as gnutls.c writes them. */
    private val labels = mapOf(
        "Insecure site" to R.string.nag_cert_insecure_site,
        "Abort" to R.string.nag_sc_cancel,
        "View certificate" to R.string.nag_cert_view,
        "Accept 24 hours" to R.string.nag_cert_accept_day,
        "Accept permanently" to R.string.nag_cert_accept_always,
    )

    /** "You attempted to reach HOST. REASON\n…": the reason says whether the certificate is unknown or changed. */
    private val unknownOrChanged = Regex("""^You attempted to reach (.+?)\. However, the security certificate presented by the server (is unknown|changed since)""")

    /** "This is the certificate presented by HOST:\nDETAILS\n\nIf in doubt, abort now." */
    private val presented = Regex("""^This is the certificate presented by ([^\n]+?):\n(.*?)\n\nIf in doubt, abort now\.\s*$""", RegexOption.DOT_MATCHES_ALL)

    /** [text] (a title, a message or a button of libVLC's) in the interface's language when known. */
    fun localized(res: Resources, text: String): String {
        if (text.isEmpty()) return text
        labels[text.trim()]?.let { return res.getString(it) }
        unknownOrChanged.find(text)?.let { match ->
            val host = match.groupValues[1]
            return res.getString(if (match.groupValues[2] == "is unknown") R.string.nag_cert_unknown else R.string.nag_cert_changed, host)
        }
        presented.find(text)?.let { match -> return res.getString(R.string.nag_cert_presented, match.groupValues[1], match.groupValues[2].trim()) }
        return text
    }
}
