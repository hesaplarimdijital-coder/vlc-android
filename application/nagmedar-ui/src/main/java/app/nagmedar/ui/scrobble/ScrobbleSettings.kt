package app.nagmedar.ui.scrobble

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import java.security.GeneralSecurityException
import java.security.ProviderException

/** S-1008: why a service's queue is held: it does not take the account's scrobbles. */
@Immutable
data class ScrobbleHold(val reason: String?)

/**
 * S-1008: Settings › Scrobble as the preferences keep it, for each service: whether it is on, the
 * account signed in (its name, and its session key or token sealed by [box]) and whether its queue
 * is held. None of it goes into a settings backup ([keys]). A sealed secret that cannot be opened
 * (a new device, a restored copy of the preferences) is no account: the user signs in again.
 */
class ScrobbleSettings(private val prefs: SharedPreferences, private val box: SecretBox) {

    /** Whether scrobbles go to [service]; off until the user turns it on, and only with an account. */
    fun enabled(service: ScrobbleServiceId): Boolean = prefs.getBoolean(enabledKey(service), false) && accountName(service) != null

    fun setEnabled(service: ScrobbleServiceId, on: Boolean) {
        prefs.edit().putBoolean(enabledKey(service), on).apply()
    }

    /** The name [service] knows the account by; null without one. */
    fun accountName(service: ScrobbleServiceId): String? = prefs.getString(nameKey(service), null)?.takeIf { it.isNotEmpty() }

    /** The secret that scrobbles for the account; null without one, or when it cannot be opened. */
    fun secret(service: ScrobbleServiceId): String? {
        if (accountName(service) == null) return null
        return prefs.getString(secretKey(service), null)?.let(box::open)
    }

    /** Keeps [account] for [service], switched on, and lets its queue go on. False when the secret could not be sealed. */
    fun signIn(service: ScrobbleServiceId, account: ScrobbleAccount): Boolean {
        val sealed = try {
            box.seal(account.secret)
        } catch (e: GeneralSecurityException) {
            return false
        } catch (e: ProviderException) {
            return false
        }
        prefs.edit()
            .putString(nameKey(service), account.name)
            .putString(secretKey(service), sealed)
            .putBoolean(enabledKey(service), true)
            .remove(holdKey(service))
            .apply()
        return true
    }

    /** Forgets the account of [service] and its switch. */
    fun signOut(service: ScrobbleServiceId) {
        prefs.edit().remove(nameKey(service)).remove(secretKey(service)).remove(enabledKey(service)).remove(holdKey(service)).apply()
    }

    /** The hold on [service]'s queue, or null. */
    fun hold(service: ScrobbleServiceId): ScrobbleHold? =
        if (prefs.contains(holdKey(service))) ScrobbleHold(prefs.getString(holdKey(service), null)?.takeIf { it.isNotEmpty() }) else null

    fun setHold(service: ScrobbleServiceId, hold: ScrobbleHold?) {
        val edit = prefs.edit()
        if (hold == null) edit.remove(holdKey(service)) else edit.putString(holdKey(service), hold.reason.orEmpty())
        edit.apply()
    }

    /** Whether [service] sends what is queued: on, signed in, and not held. Scrobbles are queued whenever it is [enabled]. */
    fun active(service: ScrobbleServiceId): Boolean = enabled(service) && hold(service) == null

    companion object {
        private fun enabledKey(service: ScrobbleServiceId) = "nagmedar_scrobble_${service.key}_on"
        private fun nameKey(service: ScrobbleServiceId) = "nagmedar_scrobble_${service.key}_account"
        private fun secretKey(service: ScrobbleServiceId) = "nagmedar_scrobble_${service.key}_secret"
        private fun holdKey(service: ScrobbleServiceId) = "nagmedar_scrobble_${service.key}_hold"

        /** Every preference key of the scrobbling, which a backup of the settings leaves out (an account is not carried over). */
        val keys: Set<String> = ScrobbleServiceId.entries.flatMap {
            listOf(enabledKey(it), nameKey(it), secretKey(it), holdKey(it))
        }.toSet()
    }
}
