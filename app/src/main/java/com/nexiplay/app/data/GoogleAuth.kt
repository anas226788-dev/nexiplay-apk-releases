package com.nexiplay.app.data

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.nexiplay.app.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.security.SecureRandom

class GoogleSignInNotConfiguredException : IllegalStateException("Google sign-in is not configured")

/** Uses Google's native Credential Manager, then exchanges its verified ID token with Supabase. */
object GoogleAuth {
    private const val TAG = "GoogleAuth"

    suspend fun signIn(context: Context) {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (clientId.isBlank()) {
            Log.e(TAG, "Google sign-in aborted: GOOGLE_WEB_CLIENT_ID is blank in BuildConfig")
            throw GoogleSignInNotConfiguredException()
        }

        val nonceBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val nonce = Base64.encodeToString(
            nonceBytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(nonce.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .setNonce(hashedNonce)
            .build()

        val signInOption = GetSignInWithGoogleOption.Builder(clientId)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .addCredentialOption(signInOption)
            .build()

        val result = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Google sign-in cancelled by user")
            throw e
        } catch (e: NoCredentialException) {
            Log.e(TAG, "No matching Google credential found. Ensure Android SHA-1 is added in Google Cloud Console: ${e.message}", e)
            throw IllegalStateException("No Google account found or matching OAuth configuration. Check Google Cloud Console SHA-1.", e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager error: ${e.type} | ${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during getCredential: ${e.message}", e)
            throw e
        }

        val credential = result.credential as? CustomCredential
            ?: throw IllegalStateException("Unsupported Google sign-in response")
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Unsupported Google sign-in response: ${credential.type}")
        }

        val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
        Log.d(TAG, "Received Google ID token. Exchanging with Supabase Auth...")

        SupabaseClient.main.auth.signInWith(IDToken) {
            idToken = googleToken.idToken
            provider = Google
            this.nonce = nonce
        }
        Log.i(TAG, "Supabase Google sign-in successful!")
    }
}
