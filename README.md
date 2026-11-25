> **Note:** To access all shared projects, get information about environment setup, and view other guides, please visit [Explore-In-HMOS-Wearable Index](https://github.com/Explore-In-HMOS-Wearable/hmos-index).

# Huawei Health Kit login-free demo

An Android app, with a small Node.js backend, that authorizes Huawei Health Kit Cloud with
the **login-free SDK**: the user allows the app in the Huawei Health app, without a HUAWEI ID
sign-in screen. The screen follows the iOS and Flutter login-free samples.

```
phone app  ->  Huawei Health app  ->  code  ->  access token  ->  Health Kit Cloud (REST)
watch  ->  Huawei Health app  ->  Huawei cloud  ->  backend  ->  push to the phone app
```

The full walkthrough, from the console setup to production notes, is in
**[docs/DEVELOPMENT_GUIDE.md](docs/DEVELOPMENT_GUIDE.md)**.

## What the app does

| Feature | Code |
| --- | --- |
| Start the login-free authorization in Huawei Health | `healthkit/auth/LoginFreeAuthorizer.kt` |
| Exchange the code, show `code`, `state`, `access_token`, `refresh_token`, `expires_in` | `healthkit/HealthKitClient.kt` |
| Refresh the access token | `healthkit/token/HealthKitTokenClient.kt` |
| Read the workouts of the last 7 days to 1 year | `healthkit/api/ActivityRecordsApi.kt` |
| Turn on workout push notifications | `push/WorkoutSubscriptions.kt` |
| Delete the app's data and cancel the authorization | `healthkit/api/DataDeletionApi.kt` |
| Receive Huawei's subscription callback and send the push | `backend/` |

Long press a value to copy it. The trash button deletes the stored tokens and starts over.

## Quick start

1. Register your app on HUAWEI Developers, apply for Health Service Kit and the login-free
   authorization. See [Console setup](docs/DEVELOPMENT_GUIDE.md#2-console-setup).
2. Copy `local.properties.example` to `local.properties` and fill in your values.
3. Copy `keystore.properties.example` to `keystore.properties` and point it at the keystore
   whose SHA-256 fingerprint you registered. The login-free SDK rejects the debug key.
4. For push: add your own `app/agconnect-services.json` (Push Kit) and, for phones with
   Google services, `app/google-services.json` (Firebase).
5. Open the project in Android Studio and run it on a real phone that has the Huawei Health
   app installed and signed in.
6. For workout notifications, run the backend: see [backend/README.md](backend/README.md).

No ID, secret or key is in this repository. `local.properties`, `keystore.properties`, the
two JSON files and `backend/.env` are all in `.gitignore`.

## Project layout

```
app/                    Android app (Kotlin, Jetpack Compose)
  healthkit/            Health Kit Cloud: authorization, tokens, workouts, deletion
  push/                 push token and backend registration
  ui/, viewmodels/      screens and state
backend/                Node.js service for the Health Kit subscription and the push
docs/                   development guide
```

## Requirements

| | Version |
| --- | --- |
| Android Studio | A recent version with AGP 8.9 |
| Android | minSdk 23, compileSdk and targetSdk 35 |
| Health Kit login-free SDK | `com.huawei.hms:healthlite:6.16.0.300` |
| Huawei Health on the phone | 14.0.12.310 or later |
| Node.js (backend) | 20.6 or later |

## License

See [LICENSE](LICENSE).
