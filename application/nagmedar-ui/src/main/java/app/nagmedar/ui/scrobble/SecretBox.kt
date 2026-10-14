package app.nagmedar.ui.scrobble

/**
 * S-1008: a way to keep a secret (a session key, a token) at rest: [seal] turns it into text that
 * is safe to put in the preferences, [open] gets it back, or null when it cannot be (the text is
 * damaged, or the key that sealed it is gone: another device, a restored backup).
 */
interface SecretBox {
    fun seal(secret: String): String

    fun open(sealed: String): String?
}
