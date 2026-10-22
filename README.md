# CallerSphere Android

An independent Android caller-ID prototype. The current version is an early native Kotlin scaffold with a local sample lookup screen and a conservative Android CallScreeningService foundation.

## Current status
- Native Kotlin Android app (minimum Android 10 / API 29)
- Local demo lookup only; no real global caller database
- Call-screening role request
- Call screening currently allows calls and does not upload numbers
- GitHub Actions builds a debug APK on pushes, pull requests, and manual runs

## Build
The workflow installs JDK 17 and Gradle 8.9, then runs:
```sh
gradle assembleDebug
```
The debug APK is uploaded as the workflow artifact `callersphere-debug-apk`.

## Planned phases
1. Improve caller lookup and calling UX; test on real devices.
2. Design privacy-reviewed shared caller database and moderation.
3. Add managed PostgreSQL and a secured API without exposing database credentials in the app.
4. Add consent, privacy policy, deletion/dispute workflows, and security controls.
5. Integrate AdMob using test ads first and complete required disclosures.
6. Prepare signing, AAB generation, Play Console testing and release.

## Privacy and safety
Do not upload personal address books by default. Do not treat community labels as verified identity. Provide reporting and correction routes before launching public contributions. Do not commit database passwords, API secrets, or release-signing credentials.

This prototype is not yet a production caller-ID service and does not identify arbitrary unknown callers.
