package com.nexiplay.app.data

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
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
    suspend fun signIn(context: Context) {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (clientId.isBlank()) throw GoogleSignInNotConfiguredException()

        val nonceBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val nonce = Base64.encodeToString(
            nonceBytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(nonce.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

        val option = GetSignInWithGoogleOption.Builder(clientId)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = CredentialManager.create(context).getCredential(context, request)
        val credential = result.credential as? CustomCredential
            ?: throw IllegalStateException("Unsupported Google sign-in response")
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Unsupported Google sign-in response")
        }

        val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
        SupabaseClient.main.auth.signInWith(IDToken) {
            idToken = googleToken.idToken
            provider = Google
            this.nonce = nonce
        }
    }
}
