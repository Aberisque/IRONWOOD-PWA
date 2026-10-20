# CRIMSON COMMAND — Standalone Android v2.0

**No server URL needed.** This is a native Android client with an on-device three-role AI mission runner and local history. It is not a hosting environment for Python/Node frameworks.

## Installation
1. Download the `Crimson-Command-Standalone-Android-APK` workflow artifact ZIP from [GitHub Actions](https://github.com/Aberisque/IRONWOOD-PWA/actions/workflows/build-crimson-command.yml).
2. Extract the artifact and install its `app-debug.apk` onto Android. You may need to approve "Install unknown apps" for your file manager.
3. Open **Crimson Command**. It opens to Headquarters with no server address step.
4. Open **Vault** and enter a free-tier **Gemini API key** ([Google AI Studio](https://aistudio.google.com/app/apikey)) or **Groq API key** ([Groq Console](https://console.groq.com/keys)). Save and test the provider; models and available free usage may change.
5. Return to HQ, choose Engineering/Marketing/Research/Operations, enter a task, and tap **Deploy Agent Team**.
6. Keep the app in the foreground during mission execution; three consecutive model calls will produce a plan, draft, and QA review. Read outputs in Missions and History.

## Direct integrations
- **Gemini/Groq**: HTTPS model inference straight from the Android app, no proxy.
- **GitHub**: store your own fine-grained token securely, list your repositories, and manually dispatch named workflows.
- **n8n/custom webhook**: manually POST a JSON payload to an existing trusted HTTPS webhook.
- **Bluesky**: save handle and app password, then explicitly confirm a post before publishing.
- **Paperclip / CrewAI / LangGraph / Microsoft Agent Framework / OpenHands / OpenClaw**: directory links; they require their own runtimes or individually implemented API bridges. The APK doesn't execute these server-side frameworks.

## Security and truth in capabilities
- Credentials are AES-256-GCM encrypted with a non-exportable key stored in Android Keystore. Keys are never displayed back to the UI. Mission history is stored in app-private preferences (not additionally encrypted), and Android backups are disabled.
- No background engine/service: the app must remain open during a mission. This build doesn't schedule overnight missions.
- AI agents are **sequential prompt roles**, not sandboxes: generated code and plans are not executed or deployed automatically.
- The application requires Internet access, and free API tiers impose quotas. There is no unlimited inference.
- Integration endpoints use HTTPS with normal Android certificate validation. Do not paste sensitive data into missions without reviewing provider policies.
- This is a development-signed debug APK. Android may require you to uninstall v1.1 before installing because each GitHub Actions runner can generate a different debug signing key; uninstalling deletes local app data.
- The APK has been CI compiled. Functional execution still requires connecting real credentials and testing on your device; a successful build does **not** prove every external integration works.

## Build from source
Gradle 8.9, JDK 17, Android SDK 35:
```sh
cd crimson-android
gradle :app:assembleDebug
```

## Roadmap
- Approval-gated code worktrees and automated pull-request workflows.
- Credential-safe adapter protocols for Paperclip/OpenHands and CrewAI/LangGraph.
- Android WorkManager scheduled triggers with clear OS battery constraints and run histories.
- More robust on-device diagnostics, export/import, and model rate-limit backoff.

The prior v1.1 Android client depended on a separate Node.js server. This v2.0 branch is the standalone replacement.
