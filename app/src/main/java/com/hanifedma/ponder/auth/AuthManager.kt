package com.hanifedma.ponder.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.hanifedma.ponder.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** The signed-in person, reduced to what the UI actually shows. */
data class UserInfo(
    val uid: String,
    val displayName: String,
    val photoUrl: String?,
)

/** Why a sign-in attempt did not produce a session. Maps to an i18n key. */
enum class AuthError(val messageKey: String) {
    CANCELLED("err.auth.cancelled"),
    NETWORK("err.auth.network"),
    NO_ACCOUNT("err.auth.noAccount"),
    NOT_ALLOWED("err.auth.notAllowed"),
    CONFIGURATION("err.auth.config"),
    GENERIC("err.auth.generic"),
}

/**
 * Google sign-in through Credential Manager, then Firebase Auth — which yields
 * the same `uid` the web app signs in as, so both clients land on the same
 * `/users/{uid}` documents.
 *
 * Everything here is optional: when the app is built without a
 * google-services.json, [isAvailable] is false and the app runs device-only.
 */
class AuthManager(context: Context) {

    private val appContext = context.applicationContext

    /**
     * True only when this build was compiled against a real google-services.json
     * *and* Firebase actually came up.
     */
    val isAvailable: Boolean = BuildConfig.FIREBASE_CONFIGURED &&
        runCatching { FirebaseApp.getApps(appContext).isNotEmpty() }
            .getOrDefault(false)

    private val auth: FirebaseAuth? =
        if (isAvailable) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null

    /**
     * The OAuth web client id that `google-services.json` generates. Looked up by
     * name so the app still compiles when that file is absent.
     */
    private val webClientId: String? by lazy {
        val id = appContext.resources.getIdentifier(
            "default_web_client_id", "string", appContext.packageName,
        )
        if (id == 0) null else runCatching { appContext.getString(id) }.getOrNull()
    }

    /** Emits the current user (or null) and again on every change. */
    fun authState(): Flow<UserInfo?> = callbackFlow {
        val instance = auth
        if (instance == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { fa ->
            trySend(fa.currentUser?.let {
                UserInfo(
                    uid = it.uid,
                    displayName = it.displayName ?: it.email ?: "Signed in",
                    photoUrl = it.photoUrl?.toString(),
                )
            })
        }
        instance.addAuthStateListener(listener)
        awaitClose { instance.removeAuthStateListener(listener) }
    }

    /**
     * Shows the Google account picker and signs the chosen account into Firebase.
     * Returns null on success, or the reason it did not happen.
     */
    suspend fun signIn(activity: Activity): AuthError? {
        val instance = auth ?: return AuthError.CONFIGURATION
        val clientId = webClientId ?: return AuthError.CONFIGURATION

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()

        val credential = try {
            CredentialManager.create(appContext).getCredential(activity, request).credential
        } catch (e: GetCredentialCancellationException) {
            return AuthError.CANCELLED
        } catch (e: NoCredentialException) {
            return AuthError.NO_ACCOUNT
        } catch (e: GetCredentialProviderConfigurationException) {
            Log.e(TAG, "Credential Manager is not configured on this device", e)
            return AuthError.CONFIGURATION
        } catch (e: Exception) {
            Log.e(TAG, "Credential Manager failed", e)
            return AuthError.GENERIC
        }

        val idToken = (credential as? CustomCredential)
            ?.takeIf { it.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL }
            ?.let { runCatching { GoogleIdTokenCredential.createFrom(it.data).idToken }.getOrNull() }
            ?: run {
                Log.e(TAG, "Unexpected credential type: ${credential.type}")
                return AuthError.GENERIC
            }

        return try {
            instance.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            null
        } catch (e: FirebaseNetworkException) {
            AuthError.NETWORK
        } catch (e: FirebaseAuthException) {
            Log.e(TAG, "Firebase rejected the Google credential: ${e.errorCode}", e)
            if (e.errorCode == "ERROR_OPERATION_NOT_ALLOWED") AuthError.NOT_ALLOWED
            else AuthError.GENERIC
        } catch (e: Exception) {
            Log.e(TAG, "Firebase sign-in failed", e)
            AuthError.GENERIC
        }
    }

    suspend fun signOut() {
        auth?.signOut()
        // Stops the next sign-in from silently reusing the account that was just
        // signed out of.
        runCatching {
            CredentialManager.create(appContext)
                .clearCredentialState(ClearCredentialStateRequest())
        }.onFailure { Log.w(TAG, "Could not clear credential state", it) }
    }

    private companion object {
        const val TAG = "PonderAuth"
    }
}
