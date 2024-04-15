package app.web.simplewebauthn.webauthnablewebvewapp

import android.app.Activity
import androidx.credentials.CredentialManager
import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialCustomException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext

class CredentialManagerHandler {
    private val coroutineScope: CoroutineScope
    private val credentialManager: CredentialManager
    private val appActivity: Activity

    constructor(activity: Activity) {
        this.appActivity = activity
        this.credentialManager = CredentialManager.create(activity.applicationContext)
        this.coroutineScope = CoroutineScope(EmptyCoroutineContext)
    }

    suspend fun createPasskey(requestJson: String) : CreatePublicKeyCredentialResponse? {
        val createRequest = CreatePublicKeyCredentialRequest(requestJson)
        try {
            return this.credentialManager.createCredential(appActivity, createRequest) as CreatePublicKeyCredentialResponse
        } catch (e: CreateCredentialException) {
            // For error handling use guidance from https://developer.android.com/training/sign-in/passkeys
            Log.i(TAG, "Error creating credential: ErrMessage: ${e.errorMessage}, ErrType: ${e.type}")
            throw e
        }
    }

    suspend fun getPasskey(requestJson: String): GetCredentialResponse {
        val getRequest = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson, null)))
        Log.i(TAG, requestJson)
        try {
            return this.credentialManager.getCredential(appActivity, getRequest)
        } catch (e: Exception) {
            handleFailure(e)
            throw e
        }
    }

    private fun handleFailure(e: java.lang.Exception) {
        when (e) {
            is CreatePublicKeyCredentialDomException -> {
                // Handle the passkey DOM errors thrown according to the
                // WebAuthn spec.
            }
            is CreateCredentialCancellationException -> {
                // The user intentionally canceled the operation and chose not
                // to register the credential.
            }
            is CreateCredentialInterruptedException -> {
                // Retry-able error. Consider retrying the call.
            }
            is CreateCredentialProviderConfigurationException -> {
                // Your app is missing the provider configuration dependency.
                // Most likely, you're missing the
                // "credentials-play-services-auth" module.
            }
            is CreateCredentialUnknownException -> {

            }
            is CreateCredentialCustomException -> {
                // You have encountered an error from a 3rd-party SDK. If you
                // make the API call with a request object that's a subclass of
                // CreateCustomCredentialRequest using a 3rd-party SDK, then you
                // should check for any custom exception type constants within
                // that SDK to match with e.type. Otherwise, drop or log the
                // exception.
            }
            else -> Log.w(TAG, "Unexpected exception type ${e::class.java.name}")
        }
    }


    companion object {
        const val TAG = "CredentialManagerHandler"
    }
}