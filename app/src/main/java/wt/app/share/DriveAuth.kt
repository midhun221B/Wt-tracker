package wt.app.share

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Google sign-in for partner sharing: only access to files this app creates in my Drive. */
object DriveAuth {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"

    fun request(): AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Asks Play services; the result either carries a token or a consent screen to show ([AuthorizationResult.hasResolution]). */
    suspend fun authorize(context: Context): AuthorizationResult = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request())
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    sealed interface Silent {
        data class Token(val token: String) : Silent
        /** The user must approve again (Settings shows "Sign in again to keep sharing"). */
        data object NeedsSignIn : Silent
        /** Play services or the network failed: try again later. */
        data object Unavailable : Silent
    }

    /** A token without showing anything, for the background upload. */
    suspend fun silent(context: Context): Silent = try {
        val r = authorize(context)
        val token = r.accessToken
        if (r.hasResolution() || token == null) Silent.NeedsSignIn else Silent.Token(token)
    } catch (e: Exception) {
        Silent.Unavailable
    }
}
