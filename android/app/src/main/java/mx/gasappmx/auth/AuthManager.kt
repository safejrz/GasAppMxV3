package mx.gasappmx.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import mx.gasappmx.R

/**
 * Google sign-in via Credential Manager, exchanged for a Firebase credential. The web client ID
 * comes from `default_web_client_id`, a string resource the google-services plugin generates from
 * google-services.json (requires an OAuth web client in the Firebase project).
 */
class AuthManager(private val appContext: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager: CredentialManager = CredentialManager.create(appContext)

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** [activityContext] must be an Activity context so Credential Manager can show its UI. */
    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(appContext.getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(activityContext, request)
            val googleCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)

            val user = auth.signInWithCredential(firebaseCredential).await().user
                ?: return Result.failure(IllegalStateException("No se obtuvo el usuario."))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        auth.signOut()
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
