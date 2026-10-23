# CallerSphere Android

An independent Android caller-ID MVP inspired by common caller-ID and visual contacts features. This project does not use Eyecon's proprietary code, logo, or assets.

## Implemented in this MVP

- Native Kotlin Android app (Android 10 / API 29 minimum).
- Visual contacts list using device contacts only after explicit runtime permission.
- Contact actions for Android dialer, SMS app, and WhatsApp.
- Dial pad that opens the system phone app to place a call.
- Phone number lookup screen.
- Optional Supabase Edge Function integration for public caller directory lookups and spam reports.
- Local on-device block list.
- Android CallScreeningService foundation that rejects only numbers the user explicitly added to the local block list.
- GitHub Actions debug APK build artifact.

## What is not production-ready yet

This is not an exact copy of Eyecon and does not yet provide Eyecon's global identity/photo database, full-screen caller photos, SMS inbox, complete call-history/statistics screens, social profile matching, premium billing, or ad integration. Cloud lookup will find results only after vetted directory records have been added to Supabase. Test incoming-call blocking on real devices before relying on it.

## Configure Supabase

1. Create or select your Supabase project.
2. In the SQL Editor, run the migration at supabase/migrations/202610230001_caller_directory.sql.
3. Deploy supabase/functions/lookup-caller/index.ts as the function named lookup-caller.
4. In Supabase Auth settings, enable anonymous sign-ins for this MVP.
5. In the GitHub repository settings, add Actions repository secrets:
   - SUPABASE_URL = your project URL, such as https://YOUR_PROJECT_REF.supabase.co
   - SUPABASE_ANON_KEY = the public anon/publishable client key supported by your project
6. Run the Android Debug Build workflow again. The app embeds only the public client configuration; never add a service-role key to GitHub Actions build variables or Android code.

The Edge Function uses SUPABASE_SERVICE_ROLE_KEY from the Supabase function's server environment only. Do not create an Android build variable for that key.

### Security model

- The app never uploads the contacts list.
- Lookup/report requests are sent only when a user taps a relevant button.
- Anonymous auth is required before the Edge Function accepts a request.
- Direct client access to caller-directory and report tables is blocked with RLS and no client policies.
- The Edge Function applies a per-user hourly request limit.
- Only verified public business entries or spam entries with at least three reports are returned. User reports do not immediately publish an identity.
- Do not seed private person's name/photo, addresses, or social accounts into the directory without an appropriate lawful basis, safeguards, and user review/appeal paths.

Anonymous accounts can be recreated, so the initial rate limit is not sufficient protection for a large public launch. Before launch, add stronger abuse controls, moderation/admin tooling, retention limits, account/privacy deletion flows, a privacy policy, and country-specific legal review.

## Build locally

Requires JDK 17 and Android SDK/Gradle tooling.

    gradle assembleDebug

Debug APK output: app/build/outputs/apk/debug/app-debug.apk

GitHub Actions uploads the file as callersphere-debug-apk for 14 days after a successful build.

## Existing repository note

This source has been added to the connected repository zahoorfa72/Caller-id. The connected GitHub actions in this chat cannot create or rename a repository; rename it to Supa_Base from GitHub repository Settings or create that repository and push this project there if that exact repository name is required.
