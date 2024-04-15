







# WebAuthnableWebViewApp

![Apr-15-2024 22-10-38](https://github.com/nob3rise/WebauthnableWebVewApp/assets/22928833/37b7dcb1-25a5-4837-b71e-4d29b36e5786)

## Overview

This is a sample app with a WebAuthn executable WebView. The implementation is based on [Integrate Credential Manager with WebView](https://developer.android.com/training/sign-in/credential-manager-webview) published by Google.

## Requirement

Before trying the app, you need to have your own RP site that can run WebAuthn. You will also need to set up assetlinks.json on the site’s well-known directory to accept the app's package name as the origin.

## Usage

* Setting up an RP site
* Build and run the application
* Register a passkey as same way on Chrome.
* Touch your fingerprint or show your face following the guidance

## Features

Google's guidance was excellent, but the following implementation steps were missing, so I created a sample application. If you cannot run the application, view the implementation for reference.

- Injection javascript code dose not have getTransports() handler 
- There’s  no care  about PublicKeyCredentialDescriptor ID encording

This sample app implements the following use cases :
- Registration a passkey on WebView
- Authentication with a passkey on WebView

## Reference

- [Integrate Credential Manager with WebView](https://developer.android.com/training/sign-in/credential-manager-webview) 
- [Sign in your user with Credential Manager](https://developer.android.com/training/sign-in/passkeys)
- [Sample app: CredentialManager](https://github.com/android/identity-samples/tree/main/CredentialManager)

## Author

[twitter](https://twitter.com/noby111)

## Licence

WebAuthnableWebViewApp is distributed under the terms of the Apache License (Version 2.0) . 
