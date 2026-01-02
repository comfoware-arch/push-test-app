# Manager Alerts (Android Kotlin POC)

Waiters install the Android app, tap **Activar notificaciones**, and receive table alerts.
All devices get the alert. One waiter taps **¡La tomo!** and the backend dismisses the alert on everyone.

## Stack
- Android Kotlin + Firebase Cloud Messaging (FCM)
- Supabase (DB + Edge Functions)
- GitHub Actions build
- Firebase App Distribution for easy install

---

## A) Firebase setup (once)
1) Create Firebase project
2) Add Android app:
   - Package: `com.restaurant.manageralerts`
3) Download `google-services.json` and place it at:
   - `app/google-services.json`
4) In Firebase console:
   - Enable **App Distribution**
   - Create group named `testers` and add tester emails

5) Create a Service Account JSON for App Distribution:
   - Google Cloud Console → IAM & Admin → Service Accounts → Create
   - Grant: Firebase App Distribution Admin (or equivalent)
   - Create JSON key

## B) GitHub Secrets
In your GitHub repo → Settings → Secrets and variables → Actions:

- `FIREBASE_ANDROID_APP_ID` = Firebase Android App ID (looks like `1:123...:android:...`)
- `FIREBASE_SERVICE_ACCOUNT_JSON` = the full service account JSON file content

Push to `main` → GitHub Actions builds + distributes.

---

## C) Supabase setup
1) Run `supabase/schema.sql` in Supabase SQL editor.
2) Deploy Edge Function `manager-api` with env vars:

Required env vars:
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `GOOGLE_SERVICE_ACCOUNT_JSON` (contains client_email + private_key)
- `FCM_PROJECT_ID`

3) Update Android constants:
`app/src/main/java/.../data/SupabaseApi.kt`
- EDGE_BASE
- SUPABASE_ANON

---

## D) Sending calls
Your QR website can call:
POST {EDGE_BASE}/send-call
body: { "zone": "patio", "table": 12 }

The Edge Function creates a `table_calls` row and sends an FCM message:
data: { type: "call", requestId, zone, table }

When a waiter presses **¡La tomo!**, Android calls:
POST {EDGE_BASE}/claim-call
body: { request_id, device_id, name }

Backend claims atomically and broadcasts:
data: { type: "dismiss", requestId }

All devices cancel that notification.

---

## Notes
- Android 13+ requires POST_NOTIFICATIONS permission; the app asks when you press subscribe.
- This is a POC: auth is minimal. For production, add auth/restaurant scoping.

---

## IMPORTANT: Gradle Wrapper jar placeholder
This repo generator creates `gradle/wrapper/gradle-wrapper.jar` as a placeholder (empty file),
so the structure matches the "initial commit" tree.

To actually build with Gradle wrapper, you must add the real `gradle-wrapper.jar` later.
(If you create the repo in GitHub UI, the jar would normally be committed from Android Studio.)
