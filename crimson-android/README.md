# Crimson Command for Android — v1.1

A native Android APK client for the Crimson Command AI operations center.

## What the APK includes
- Full-screen dark crimson-and-black Android UI with a custom launcher icon.
- Secure connection screen that validates the Crimson Command `/api/bootstrap` endpoint before connecting.
- HTTPS-only connections to remote servers; HTTP is allowed **only on localhost**.
- Native WebView, cookie support, back navigation, and external browser link handling.
- Full web operations dashboard **once connected** to a running Crimson Command Node.js backend.

## Important limitation
This APK is **not** a self-contained implementation of all 12 AI connectors. The existing Node.js backend is a separate application and **must be running** for agents, workflows, credentials, scheduling, and integrations. The APK does not bundle Node.js, so no Termux is required on the phone *if* you host the backend on a trusted computer or server.

## How to install
1. Open the GitHub Actions run for **Build Crimson Command APK**.
2. Download the workflow artifact named `Crimson-Command-Android-APK`.
3. Extract its ZIP and install `app-debug.apk` on Android.
4. Run the existing [Crimson Command server](../../blob/crimson-command-apk/crimson-android/README.md) on a trusted computer or hosted machine.
5. Make the server accessible to the phone over private HTTPS, then enter that URL on the APK's connection screen.

## Build yourself
Android Studio / Gradle with JDK 17, Android SDK 35, Gradle 8.9:

```sh
cd crimson-android
gradle :app:assembleDebug
```
The installed debug build is signed by the build runner's development key, **not a production signing key**. For permanent distribution create a separately signed release build with your own keystore. Subsequent debug builds from different CI machines may have different signing keys and may require uninstalling the prior debug APK.

## Privacy & security
No analytics SDK or advertising libraries. Authentication continues to use HttpOnly cookies from the backend. The Android client stores only the chosen server origin in local app preferences; it does not store connector credentials.
 
The original backend is not audited for direct public deployment. Keep the server behind secure authentication and HTTPS, preferably in a trusted private network. Do not disable TLS validation.

## License
Original app code MIT. Third-party connectors retain their independent licenses.

