# Firebase Google Login Setup (Foodie API)

## Endpoint

`POST /api/v1/auth/google`

Request JSON:

```json
{
  "firebaseIdToken": "<Firebase ID token obtained after Google sign-in>"
}
```

The successful response uses the existing API envelope and `TokenResponse` fields (`access`, `refresh`, `issuedAt`, `expiredAt`). Existing email/password login and refresh-token behavior remain unchanged.

## Configure server credentials

1. In Firebase Console, open the separate Firebase project used by the Foodie Flutter app.
2. Open **Project settings → Service accounts** and create/download a private service-account key only if your hosting environment does not already provide Application Default Credentials.
3. Store the JSON securely on the backend host, outside the repository.
4. Set `GOOGLE_APPLICATION_CREDENTIALS` to the absolute path of that file before starting Spring Boot.

PowerShell example for local development:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\secure\foodie-firebase-admin.json"
.gradlew.bat bootRun
```

Linux example:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/secure/foodie-firebase-admin.json
./gradlew bootRun
```

Never commit the service-account JSON, paste its private key into source code, or put it in Flutter assets. The Firebase Admin SDK verifies the ID token against the configured Firebase project. A missing server credential is reported as a configuration error.

## First Android setup

Register the Android application ID from `android/app/build.gradle.kts` in Firebase and put the downloaded `google-services.json` in the Flutter app's `android/app/` directory. Enable Google as a sign-in provider in Firebase Authentication.
