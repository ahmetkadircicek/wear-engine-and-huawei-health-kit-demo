# Development guide

This guide explains how the demo is built and how to run it with your own Huawei developer
account. It follows the order in which you would build the same features into your own app.

1. [Overview](#1-overview)
2. [Console setup](#2-console-setup)
3. [Project configuration](#3-project-configuration)
4. [Health Kit: authorization](#4-health-kit-authorization)
5. [Health Kit: reading workouts](#5-health-kit-reading-workouts)
6. [Workout push notifications](#6-workout-push-notifications)
7. [Deleting data and disconnecting](#7-deleting-data-and-disconnecting)
8. [Testing](#8-testing)
9. [Before production](#9-before-production)
10. [Troubleshooting](#10-troubleshooting)
11. [Official documentation](#11-official-documentation)

---

## 1. Overview

The app gets a Huawei Health Kit Cloud authorization with the login-free SDK: the user
allows the app in the Huawei Health app, without a HUAWEI ID sign-in screen. The app then
exchanges the code for tokens, reads the user's workouts and turns on workout push
notifications through the backend.

The screen follows the iOS and Flutter login-free samples: a **Last result** card, the
values that came back (`code`, `state`, `access_token`, `refresh_token`, `expires_in`), a
**Start login-free** button and a **Refresh token** button. Long press a value to copy it;
the trash button deletes the stored tokens and starts over.

```
+--------------------+  login-free  +---------------------+
|  Your phone app    | <----------> |  Huawei Health app  |
|  (this demo)       |    code      |                     |
+--------------------+              +---------------------+
          |                                   |
          | REST (OAuth 2.0)                  | sync
          v                                   v
+--------------------+              +---------------------+
|  Your backend      | <----------- |  Huawei Health Kit  |
|  (backend/)        |    notify    |  Cloud              |
+--------------------+              +---------------------+
```

---

## 2. Console setup

Do this first: some steps are reviewed by Huawei and take days.

### 2.1 Developer account

1. Register a HUAWEI ID on [HUAWEI Developers](https://developer.huawei.com/consumer/en/)
   and complete the identity verification, as an individual or an enterprise.
2. Open **Console** and create a project and an Android app in AppGallery Connect. This
   gives you the **App ID** and the **App secret**.

### 2.2 HUAWEI ID service

In **App services > Development > HUAWEI ID**, apply for the HUAWEI ID service:

| Field | Value |
| --- | --- |
| Product type | Mobile app |
| Package name | Your `applicationId`, exactly. It cannot be changed later. |
| SHA-256 certificate fingerprint | The fingerprint of the keystore that signs your builds |

The login-free SDK checks the App ID, the package name and the signing certificate. They
must match the console.

### 2.3 Health Service Kit

On the **Health Service Kit** card, apply for the scopes this demo uses:

| Scope | Used for |
| --- | --- |
| `openid` | Who the user is (`openId`), required |
| `https://www.huawei.com/healthkit/activityrecord.read` | Reading workout records |
| `https://www.huawei.com/healthkit/activityrecord.write` | Deleting the records this app wrote (section 7) |
| `https://www.huawei.com/healthkit/activity.read` | The detail data of a workout |

Data is only readable from the moment the user authorizes your app. To read older data,
apply for a history scope (`historydata.open.week`, `.month` or `.year`) and set
`healthkit.historyWindow`.

For **Method 1 (Web OAuth)** add your redirect URI to the app's callback URLs.

For **Method 2 (login-free SDK)**:

- Add Huawei's fixed callback URL to the app's callback URLs, exactly like this:
  `https://h5hosting.dbankcdn.com/cch5/healthkit/oauth-h5/oauth-callback.html`
- Enter your own deep link as the **Scheme URL**, for example
  `wearhealthkit://oauth/callback`. The console then gives you a **Scheme Secret**.
- Apply for the login-free authorization permission for your regions.

In test mode, at most 100 users can use the scopes. Apply for verification on the same card
to remove the limit.

### 2.4 Subscription (workout notifications)

On the Health Service Kit card, register the subscription capability in **HTTP** mode with
your backend's callback URL, which must be HTTPS and end with `/healthkit/notifications`.
Huawei runs a connectivity test and expects 204. You then get a **subscriber ID** and a
**subscriber secret** for the backend. See [backend/deploy/README.md](../backend/deploy/README.md).

### 2.5 Push

- **Push Kit** (Huawei phones): enable Push Kit for the app in AppGallery Connect and
  download `agconnect-services.json` into `app/`.
- **Firebase** (phones with Google services): add the app in the Firebase console and
  download `google-services.json` into `app/`. The Google Services plugin is applied only
  when this file exists, so the project builds without it.

---

## 3. Project configuration

### 3.1 local.properties

Copy `local.properties.example` to `local.properties`. The build reads it into
`BuildConfig`. Environment variables with the names in the third column work too, which is
what a CI job would use.

| Key | Meaning | Environment variable |
| --- | --- | --- |
| `healthkit.clientId` | App ID, used as `client_id` and, when `loginFreeAppId` is empty, as `com.huawei.hms.client.appid` in the manifest | `HEALTH_KIT_CLIENT_ID` |
| `healthkit.clientSecret` | App secret, Method 1 token exchange (see section 9) | `HEALTH_KIT_CLIENT_SECRET` |
| `healthkit.deepLink` | This app's deep link, the Method 2 Scheme URL | `HEALTH_KIT_DEEP_LINK` |
| `healthkit.redirectUri` | Method 1 redirect URI, exactly as registered | `HEALTH_KIT_REDIRECT_URI` |
| `healthkit.schemeSecret` | Method 2 Scheme Secret | `HEALTH_KIT_SCHEME_SECRET` |
| `healthkit.loginFreeAppId` | Method 2 App ID if it differs from `clientId`; also the manifest `com.huawei.hms.client.appid` | `HEALTH_KIT_LOGIN_FREE_APP_ID` |
| `healthkit.apiHost` | Pin a regional Health Kit host, or leave empty | `HEALTH_KIT_API_HOST` |
| `healthkit.authMethod` | `LOGIN_FREE_SDK`. The screen only offers this method | `HEALTH_KIT_AUTH_METHOD` |
| `healthkit.historyWindow` | `NONE`, `WEEK`, `MONTH` or `YEAR` | `HEALTH_KIT_HISTORY_WINDOW` |
| `backend.baseUrl` | Your backend, for push notifications | `BACKEND_BASE_URL` |

The keys are case sensitive. The build prints a warning for any unknown `healthkit.*` key
instead of silently using a default.

### 3.2 Signing

The login-free SDK rejects an APK signed with the Android debug key. Copy
`keystore.properties.example` to `keystore.properties` and point it at the keystore whose
SHA-256 fingerprint you registered. Both debug and release builds are then signed with it.

### 3.3 What never goes into the repository

`local.properties`, `keystore.properties`, `*.jks`, `app/agconnect-services.json`,
`app/google-services.json` and `backend/.env` are all in `.gitignore`.

---

## 4. Health Kit: authorization

Health Kit uses the OAuth 2.0 authorization code flow. The flow has four steps, and the two
methods differ only in step 1.

| Step | What happens | Code |
| --- | --- | --- |
| 1 | Get an authorization code. The user signs in if needed and allows the app. | `auth/WebOAuthAuthorizer.kt` or `auth/LoginFreeAuthorizer.kt` |
| 2 | Change the code into an access token and a refresh token | `token/HealthKitTokenClient.kt` |
| 3 | Read the workouts with the access token | `api/ActivityRecordsApi.kt` |
| 4 | Refresh the access token after 1 hour, or with the **Refresh token** button | `token/HealthKitTokenClient.kt` |

`HealthKitClient.kt` is the single entry point; the view model only talks to it.

### 4.1 The two methods

| | Method 1: Web OAuth | Method 2: login-free SDK |
| --- | --- | --- |
| SDK in the app | None, plain HTTPS | `com.huawei.hms:healthlite:6.16.0.300` |
| Sign-in page | Always | Not shown again when the user is already signed in |
| Where the user allows the app | Huawei page in a Custom Tab or an in-app WebView | HUAWEI ID screen, Huawei Health app, or a Huawei web page |
| Callback | Your redirect URI (`healthkit.redirectUri`) | Huawei's fixed callback URL, plus your deep link as the Scheme URL |

**The two callbacks are different values.** `healthkit.redirectUri` is what Method 1 sends to
Huawei. `healthkit.deepLink` is this app's own scheme, used for the manifest intent filter
and as the Method 2 Scheme URL. Mixing them up produces `invalid redirect_uri` (error 1101,
sub_error 20022) on Huawei's page.

When the redirect URI is not this app's own scheme (an `https` URL), Method 1 opens the page
in `WebAuthActivity` and catches the redirect there, because a Custom Tab cannot hand an
arbitrary web URL back to the app.

The screen only offers Method 2. `LoginFreeAuthorizer` calls
`requestAuthorizationWithPrivacyIntent`, which always uses the Huawei Health app and also
asks the user to turn on cloud sync. The Method 1 code is still in `auth/` for reference.

### 4.2 The Health Kit switch

Besides allowing your app, the user must turn on HUAWEI Health Kit in the Huawei Health app
(Me > Privacy management > Data sharing and authorization). If it is off, the workout list
stays empty and the response header `x-health-app-privacy` is `2`. The **Open Huawei Health**
button takes the user there.

---

## 5. Health Kit: reading workouts

```
GET https://health-api.cloud.huawei.com/healthkit/v2/activityRecords
    ?startTime=<ms>&endTime=<ms>
Authorization: Bearer <access token>
```

- The **Look back** control reads the last 7 days, 30 days, 90 days or 1 year. The window
  always ends now: Health Kit answers 403 "The query time is out of range" when `endTime`
  is far in the past.
- Results come in pages; `ActivityRecordsApi` follows the cursor.
- **Regional sites.** A token only works on the site its HUAWEI ID belongs to. Another site
  answers 403 with code 121001 ("site cross") and puts the right address in the `Location`
  response header, for example `https://health-api.cloud.huawei.eu/healthkit/v2/...`. The
  app sends the request again to that host once, and only to that host, as the Health Kit
  developer agreement asks (`api/SiteCross.kt`). It remembers the host (`ApiSiteStore.kt`);
  `healthkit.apiHost` sets the first host to try. The backend does the same per call
  (`backend/src/health-kit.js`), since its users can be on different sites.
- `WorkoutType.kt` turns the `activityType` number into a name.

---

## 6. Workout push notifications

Instead of polling, the backend subscribes each user to workout events. Huawei calls the
backend when a workout arrives, and the backend sends a push.

```
app: push token  ->  backend POST /devices
app: openId + access token  ->  backend POST /subscriptions  ->  Huawei subscription
watch records a workout  ->  Huawei calls POST /healthkit/notifications  ->  backend
backend  ->  Push Kit or Firebase  ->  phone shows "New workout"
```

On the phone (`push/`):

1. `PushTokens.kt` gets a token from Push Kit on Huawei phones or from Firebase on phones
   with Google services.
2. `WorkoutSubscriptions.kt` sends the token and the `openId` to the backend, then asks the
   backend to create the Huawei subscription with the user's access token. This runs right
   after a successful authorization.

On the backend (`backend/`):

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/devices` | Store `{openId, pushToken, provider}` |
| POST | `/subscriptions` | Subscribe the user to Health Kit events |
| GET, POST | `/healthkit/notifications` | The callback Huawei calls |
| POST | `/push-test/:openId` | Send a test push |

Huawei signs every notification with `x-notification-signature`, an HMAC-SHA256 over
`openId_eventType_eventTime` keyed with the Base64-decoded subscriber secret. The backend
answers 204 at once, drops a notification whose signature is wrong, and sends the push
afterwards, so Huawei never waits for the push service. Set `HEALTH_SUBSCRIBER_SECRET`
before going live: while it is empty, the signature check is skipped.

Run it locally:

```bash
cd backend
npm install
cp .env.example .env    # fill in your values
npm start
```

Node.js 20.6 or later is needed for `--env-file`. To deploy it, see
[backend/deploy/README.md](../backend/deploy/README.md).

---

## 7. Deleting data and disconnecting

| Button | What it does | Code |
| --- | --- | --- |
| Trash (start over) | Deletes the stored tokens on the phone. The Huawei authorization stays. | `HealthKitClient.signOut()` |
| Delete data | Deletes the activity records this app wrote, then cancels the authorization | `api/DataDeletionApi.kt` |

Cancelling the authorization is one call. It cancels all scopes the user granted to the
app; with `deleteData=true` the data the app created is deleted at once, otherwise after 3
days unless the user authorizes again:

```
DELETE https://health-api.cloud.huawei.com/healthkit/v2/consents/<app id>?deleteData=true
Authorization: Bearer <access token>
```

Huawei asks every app to give users a way to cancel the authorization. Users can also do it
themselves in Huawei Health (Me > Privacy management > Data sharing and authorization).

---

## 8. Testing

Use a real phone with the Huawei Health app installed and signed in. Workouts need a Huawei
watch or band that syncs to Huawei Health.

| Check | Expected |
| --- | --- |
| Start login-free | Huawei Health's permission screen, then **Token received** with the five values |
| Refresh token | A new `access_token` and `expires_in` |
| Record a workout on the watch | A push notification within minutes, if the backend runs |
| Delete data | The list empties and the authorization is cancelled |

---

## 9. Before production

- **Secrets.** This demo keeps the App secret in the app and exchanges the code there, so it
  stays a single module. A real app sends the authorization code to its backend, and only
  the backend holds the secret and the tokens.
- **Token storage.** Tokens are in `SharedPreferences` here. Keep them on your backend, or
  at least encrypt them on the phone.
- **WebView.** Method 1 falls back to a WebView when the redirect URI is not the app's own
  scheme. Register a custom scheme or an App Link you own and stay on the Custom Tab.
- **Login-free SDK.** `healthlite:6.16.0.300` needs `androidx.fragment` at runtime but does
  not declare it, so `app/build.gradle.kts` adds it. Without it the SDK fails with
  `NoClassDefFoundError: androidx/fragment/app/FragmentActivity`.
- **Maven versions.** Huawei's Maven repository publishes no `maven-metadata.xml`, so write
  versions in full (`6.16.0.300`, not `6.16.+`).
- **Verification.** Apply for Health Kit verification to lift the 100-user test limit, and
  remove the scopes your app does not use before you apply.
- **Callback.** Use HTTPS with a certificate from a public certificate authority, and set the
  subscriber secret so the signature is checked.

---

## 10. Troubleshooting

| What you see | What to do |
| --- | --- |
| `invalid redirect_uri` (1101, 20022) | `healthkit.redirectUri` is not exactly the registered value. |
| `redirect_uri not registered` (1101, 20023) in Huawei Health | Add Huawei's fixed callback URL to the app (section 2.3). |
| 403 with code 121001 | Wrong regional site. The app follows the `Location` header; if the response has none, check the token and `healthkit.apiHost`. |
| 403 "The query time is out of range" | The window starts before the authorization. Use a shorter window or a history scope. |
| Empty workout list | The Health Kit switch in Huawei Health is off (section 4.2). |
| 1203 on token refresh | The refresh token expired or was revoked. Authorize again. |
| No push arrives | Check `backend.baseUrl`, the backend logs, the subscription record and the push credentials. |

---

## 11. Official documentation

Health Kit Cloud

- [Authentication](https://developer.huawei.com/consumer/en/doc/HMSCore-Guides/auth-example-0000001054581058)
- [Login-free authorization, overview](https://developer.huawei.com/consumer/en/doc/HMSCore-Guides/cloud-free-auth-login-overview-0000002054076576)
- [Querying created exercise records](https://developer.huawei.com/consumer/en/doc/HMSCore-References/activityrecords_list-0000001050114862)
- [Subscribing to data](https://developer.huawei.com/consumer/en/doc/HMSCore-Guides/subscription-0000001078496860)
- [Canceling authorization](https://developer.huawei.com/consumer/en/doc/HMSCore-Guides/canceling-authorization-0000001209549384)
- [Applying for verification](https://developer.huawei.com/consumer/en/doc/HMSCore-Guides/verification-0000001211786611)

Push

- [Push Kit, obtaining a push token](https://developer.huawei.com/consumer/en/doc/HMSCore-Guides/android-client-dev-0000001050042041)
- [Push Kit, sending downlink messages](https://developer.huawei.com/consumer/en/doc/HMSCore-References/https-send-api-0000001050986197)

Huawei can change APIs, limits and console names. Check the official pages before you go
live.
