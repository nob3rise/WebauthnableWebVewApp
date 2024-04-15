package app.web.simplewebauthn.webauthnablewebvewapp

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
This web listener looks for the 'postMessage()' call on the javascript web code, and when it
receives it, it will handle it in the manner dictated in this local codebase. This allows for
javascript on the web to interact with the local setup on device that contains more complex logic.

The embedded javascript can be found in CredentialManagerWebView/javascript/encode.js.
It can be modified depending on the use case. If you wish to minify, please use the following command
to call the toptal minifier API.
```
cat encode.js | grep -v '^let __webauthn_interface__;$' | \
curl -X POST --data-urlencode input@- \
https://www.toptal.com/developers/javascript-minifier/api/raw | tr '"' "'" | pbcopy
```
pbpaste should output the proper minimized code. In linux, you may have to alias as follows:
```
alias pbcopy='xclip -selection clipboard'
alias pbpaste='xclip -selection clipboard -o'
```
in your bashrc.
 */
// The class talking to Javascript should inherit:
class PasskeyWebListener(
    private val activity: Activity,
    private val coroutineScope: CoroutineScope,
    private val credentialManagerHandler: CredentialManagerHandler
) : WebViewCompat.WebMessageListener {

    /** havePendingRequest is true if there is an outstanding WebAuthn request.
    There is only ever one request outstanding at a time. */
    private var havePendingRequest = false

    /** pendingRequestIsDoomed is true if the WebView has navigated since
    starting a request. The FIDO module cannot be canceled, but the response
    will never be delivered in this case. */
    private var pendingRequestIsDoomed = false

    /** replyChannel is the port that the page is listening for a response on.
    It is valid if havePendingRequest is true. */
    private var replyChannel: ReplyChannel? = null

    /**
     * Called by the page during a WebAuthn request.
     *
     * @param view Creates the WebView.
     * @param message The message sent from the client using injected JavaScript.
     * @param sourceOrigin The origin of the HTTPS request. Should not be null.
     * @param isMainFrame Should be set to true. Embedded frames are not
    supported.
     * @param replyProxy Passed in by JavaScript. Allows replying when wrapped in
    the Channel.
     * @return The message response.
     */
    @UiThread
    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val messageData = message.data ?: return
        onRequest(
            messageData,
            sourceOrigin,
            isMainFrame,
            JavaScriptReplyChannel(replyProxy)
        )
    }

    private fun onRequest(
        msg: String,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        reply: ReplyChannel,
    ) {
        msg?.let {
            val jsonObj = JSONObject(msg);
            val type = jsonObj.getString(TYPE_KEY)
            val message = jsonObj.getString(REQUEST_KEY)

            if (havePendingRequest) {
                postErrorMessage(reply, "The request already in progress", type)
                return
            }

            replyChannel = reply
            if (!isMainFrame) {
                reportFailure("Requests from subframes are not supported", type)
                return
            }
            val originScheme = sourceOrigin.scheme
            if (originScheme == null || originScheme.lowercase() != "https") {
                reportFailure("WebAuthn not permitted for current URL", type)
                return
            }

//            // Verify that origin belongs to your website,
//            // it's because the unknown origin may gain credential info.
//            if (isUnknownOrigin(originScheme)) {
//                return
//            }

            havePendingRequest = true
            pendingRequestIsDoomed = false

            // Use a temporary "replyCurrent" variable to send the data back, while
            // resetting the main "replyChannel" variable to null so it’s ready for
            // the next request.
            val replyCurrent = replyChannel
            if (replyCurrent == null) {
                Log.i(TAG, "The reply channel was null, cannot continue")
                return;
            }

            when (type) {
                CREATE_UNIQUE_KEY ->
                    this.coroutineScope.launch {
                        handleCreateFlow(credentialManagerHandler, message, replyCurrent)
                    }

                GET_UNIQUE_KEY -> this.coroutineScope.launch {
                    Log.d(TAG, message)
                    handleGetFlow(credentialManagerHandler, message, replyCurrent)
                }

                else -> Log.i(TAG, "Incorrect request json")
            }
        }
    }

    // Handles the get flow in a less error-prone way
    private suspend fun handleGetFlow(
        credentialManagerHandler: CredentialManagerHandler,
        message: String,
        reply: ReplyChannel,
    ) {
        try {
            havePendingRequest = false
            pendingRequestIsDoomed = false
            val r = credentialManagerHandler.getPasskey(message)
            val successArray = ArrayList<Any>();
            successArray.add("success");
            r?.let {
                successArray.add(JSONObject((r.credential as PublicKeyCredential).authenticationResponseJson))
            }
            successArray.add(GET_UNIQUE_KEY);
            reply.send(JSONArray(successArray).toString())
            replyChannel = null // setting initial replyChannel for next request given temp 'reply'
        } catch (e: GetCredentialException) {
            reportFailure("Error: ${e.errorMessage} w type: ${e.type} w obj: $e", GET_UNIQUE_KEY)
        } catch (t: Throwable) {
            reportFailure("Error: ${t.message}", GET_UNIQUE_KEY)
        }
    }

    private suspend fun handleCreateFlow(
        credentialManagerHandler: CredentialManagerHandler,
        message: String,
        reply: ReplyChannel,
    ) {
        try {
            havePendingRequest = false
            pendingRequestIsDoomed = false
            val response = credentialManagerHandler.createPasskey(message)
            val successArray = ArrayList<Any>();
            successArray.add("success");
            response?.let {
                val responseJson = JSONObject(it.registrationResponseJson)
                successArray.add(responseJson)
            }
            successArray.add(CREATE_UNIQUE_KEY);
            Log.v(TAG, JSONArray(successArray).toString())
            reply.send(JSONArray(successArray).toString())
            replyChannel = null // setting initial replyChannel for the next request
        } catch (e: CreateCredentialException) {
            reportFailure(
                "Error: ${e.errorMessage} w type: ${e.type} w obj: $e",
                CREATE_UNIQUE_KEY
            )
        } catch (t: Throwable) {
            reportFailure("Error: ${t.message}", CREATE_UNIQUE_KEY)
        }
    }

    fun onPageStarted() {
        if (havePendingRequest) {
            pendingRequestIsDoomed = true
        }
    }

    /** Sends an error result to the page.  */
    private fun reportFailure(message: String, type: String) {
        havePendingRequest = false
        pendingRequestIsDoomed = false
        val reply: ReplyChannel = replyChannel!! // verifies non null by throwing NPE
        replyChannel = null
        postErrorMessage(reply, message, type)
    }

    private fun postErrorMessage(reply: ReplyChannel, errorMessage: String, type: String) {
        Log.i(TAG, "Sending error message back to the page via replyChannel $errorMessage");
        val array: MutableList<Any?> = ArrayList()
        array.add("error")
        array.add(errorMessage)
        array.add(type)
        reply.send(JSONArray(array).toString())
        var toastMsg = errorMessage
        Toast.makeText(this.activity.applicationContext, toastMsg, Toast.LENGTH_SHORT).show()
    }

    private class JavaScriptReplyChannel(private val reply: JavaScriptReplyProxy) :
        ReplyChannel {
        override fun send(message: String?) {
            try {
                reply.postMessage(message!!)
            } catch (t: Throwable) {
                Log.i(TAG, "Reply failure due to: " + t.message);
            }
        }
    }

    /** ReplyChannel is the interface over which replies to the embedded site are sent. This allows
    for testing because AndroidX bans mocking its objects.*/
    interface ReplyChannel {
        fun send(message: String?)
    }

    companion object {
        /** INTERFACE_NAME is the name of the MessagePort that must be injected into pages. */
        const val INTERFACE_NAME = "__webauthn_interface__"

        const val TYPE_KEY = "type"
        const val REQUEST_KEY = "request"
        const val CREATE_UNIQUE_KEY = "create"
        const val GET_UNIQUE_KEY = "get"
        const val TAG = "PasskeyWebListener"

        /** INJECTED_VAL is the minified version of the JavaScript code described at this class
         * heading. The non minified form is found at credmanweb/javascript/encode.js.*/
        const val INJECTED_VAL = """var __webauthn_interface__,__webauthn_hooks__;
(function(c){function p(a){if(null===k||null===l)console.log("Reply failure: Resolve: "+d+" and reject: "+e);else if("success"!=a[0]){var b=l;l=k=null;b(new DOMException(a[1],"NotAllowedError"))}else a=q(a[1]),b=k,l=k=null,b(a)}function f(a){var b=a.length%4;return Uint8Array.from(atob(a.replace(/-/g,"+").replace(/_/g,"/").padEnd(a.length+(0===b?0:4-b),"=")),function(g){return g.charCodeAt(0)}).buffer}function m(a){return btoa(Array.from(new Uint8Array(a),function(b){return String.fromCharCode(b)}).join("")).replace(/\+/g,
"-").replace(/\//g,"_").replace(/=+${'$'}/,"")}function r(a){if(null===d||null===e)console.log("Reply failure: Resolve: "+d+" and reject: "+e);else if("success"!=a[0]){var b=e;e=d=null;b(new DOMException(a[1],"NotAllowedError"))}else a=q(a[1]),b=d,e=d=null,b(a)}function q(a){a.rawId=f(a.rawId);a.response.clientDataJSON=f(a.response.clientDataJSON);a.response.hasOwnProperty("attestationObject")&&(a.response.attestationObject=f(a.response.attestationObject));a.response.hasOwnProperty("authenticatorData")&&
(a.response.authenticatorData=f(a.response.authenticatorData));a.response.hasOwnProperty("signature")&&(a.response.signature=f(a.response.signature));a.response.hasOwnProperty("userHandle")&&(a.response.userHandle=f(a.response.userHandle));a.hasOwnProperty("clientExtensionResults")&&(a.getClientExtensionResults=function(){return a.clientExtensionResults});a.response.hasOwnProperty("transports")&&(a.response.getTransports=function(){return a.response.transports});return a}__webauthn_interface__.addEventListener("message",
function(a){a=JSON.parse(a.data);var b=a[2];"get"===b?p(a):"create"===b?r(a):console.log("Incorrect response format for reply")});var k=null,d=null,l=null,e=null;c.create=function(a){if(!("publicKey"in a))return c.originalCreateFunction(a);var b=new Promise(function(h,n){d=h;e=n});a=a.publicKey;if(a.hasOwnProperty("challenge")){var g=m(a.challenge);a.challenge=g}a.hasOwnProperty("user")&&a.user.hasOwnProperty("id")&&(g=m(a.user.id),a.user.id=g);a=JSON.stringify({type:"create",request:a});__webauthn_interface__.postMessage(a);
return b};c.get=function(a){if(!("publicKey"in a))return c.originalGetFunction(a);var b=new Promise(function(h,n){k=h;l=n});a=a.publicKey;if(a.hasOwnProperty("challenge")){var g=m(a.challenge);a.challenge=g}Object.values(a.allowCredentials).forEach(function(h){h.hasOwnProperty("id")&&(h.id=m(h.id))});a=JSON.stringify({type:"get",request:a});console.log("json",a);__webauthn_interface__.postMessage(a);return b};c.onReplyGet=p;c.CM_base64url_decode=f;c.CM_base64url_encode=m;c.onReplyCreate=r})(__webauthn_hooks__||
(__webauthn_hooks__={}));__webauthn_hooks__.originalGetFunction=navigator.credentials.get;__webauthn_hooks__.originalCreateFunction=navigator.credentials.create;navigator.credentials.get=__webauthn_hooks__.get;navigator.credentials.create=__webauthn_hooks__.create;window.PublicKeyCredential=function(){};window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable=function(){return Promise.resolve(!1)};"""
    }
}