# Sparkora Staff — Android App

Native Android app for **Sparkora** field staff (cleaners and service employees).
It talks to the same backend as the Sparkora web platform
(`https://api.sparkora.co.uk`) and gives employees a phone-first version of the
staff portal:

| Tab | What it does |
| --- | --- |
| **Today** | See today's jobs, clock in / clock out with GPS capture, live shift timer, geofence check against the client's site (with manager-visible override), today's activity log |
| **Schedule** | Weekly schedule browser (previous / next week), job times, client names, notes and statuses |
| **Leave** | View leave requests and their approval status, submit new time-off requests (annual / sick / unpaid / other), cancel pending ones |
| **Pay** | Payslips issued to you: hours, gross, net, deductions, status |
| **Profile** | Your employee record, connected server, sign out |

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3), single-activity
- **Retrofit + OkHttp + kotlinx.serialization** for the REST API
- **DataStore** for session/token persistence
- **Fused Location Provider** for one-shot GPS fixes at clock in/out
- Manual DI (a small `AppContainer`) — no framework
- `minSdk 26`, `targetSdk 35`

## How it authenticates

Employees sign in with **email + password** via `POST /api/auth/pin-login`
(role `employee`), exactly like the web staff portal. Multi-tenant companies
are selected with the optional **Company ID** field — the same `companyId`
that appears in employee invite links (`?company=...`). The JWT (12 h expiry)
is stored in DataStore; on a 401 the app clears the session and returns to the
login screen.

The server URL is configurable on the login screen under **Server settings**
(defaults to `https://api.sparkora.co.uk`), so the app works against staging
or self-hosted instances too.

## GPS clock-in flow

1. Employee taps **Clock in** on a job → app requests location permission
   (denying still allows clocking in, just unverified — mirrors the web app's
   GPS-consent behaviour).
2. If the job has a client attached, the app calls
   `POST /api/geo/validate-clock` with the current coordinates.
3. Outside the geofence: if the company allows overrides the employee can
   confirm "Clock in anyway" (recorded as `geo_override`); otherwise the
   clock-in is blocked with the distance shown.
4. `POST /api/clock-entries` opens the shift (`clock_out = null`); the server
   enforces one active session per employee.
5. **Clock out** does `PUT /api/clock-entries/:id` with the clock-out time and
   coordinates, then marks today's matching job complete
   (`PATCH /api/schedules/:id/complete`), like the web portal.

## Building

```bash
./gradlew assembleDebug     # debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease   # unsigned release (set up signing first)
```

Requirements: JDK 17+ and the Android SDK (Android Studio installs both).
CI builds the debug APK on every push — grab it from the
**Actions → Android CI → sparkora-staff-debug-apk** artifact.

## Testing

```bash
./gradlew testDebugUnitTest          # JVM tests (fast)
./gradlew lintDebug                  # static analysis
./gradlew connectedDebugAndroidTest  # on a device/emulator
```

- **Contract tests** (`ApiContractTest`) pin the JSON dialect to what the
  backend actually serves: pg NUMERIC columns as strings, DATE columns as ISO
  timestamps, defaulted request fields always encoded.
- **Integration tests** (`RepositoryIntegrationTest`, `LoginViewModelTest`,
  `HomeViewModelTest`) run the real Retrofit/OkHttp/serialization stack
  against MockWebServer — login paths, bearer headers, 401 session expiry,
  409 double clock-in, and the geofence block/override/happy flows.
- **Instrumented tests** boot the real app on an emulator: a login-screen
  smoke test plus a full journey (sign in → today's jobs → GPS clock in →
  live shift card → clock out) against an on-device mock backend.
- CI runs all of the above on every push (emulator included) and also does an
  `assembleRelease` R8 check.

## Project layout

```
app/src/main/java/com/sparkora/app/
├── SparkoraApp.kt          # Application + AppContainer (manual DI)
├── MainActivity.kt
├── data/
│   ├── SessionManager.kt   # DataStore-backed session (token, employee, server)
│   ├── api/                # Retrofit interface + DTOs + client factory
│   └── repo/               # Repository with typed ApiResult error handling
├── location/               # Fused location one-shot helper
├── ui/
│   ├── SparkoraRoot.kt     # Login/app switch + bottom-nav scaffold
│   ├── login/  home/  schedule/  timeoff/  payslips/  profile/
│   └── theme/
└── util/Dates.kt           # Date parsing tolerant of the API's pg formats
```

## Releasing to the Play Store

Release builds are signed automatically when signing credentials are present
(otherwise they build unsigned, so PRs and fresh clones keep working).

**One-time setup** — generate an upload keystore:

```bash
keytool -genkey -v -keystore upload.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias sparkora
```

**Local signed build** — create a git-ignored `keystore.properties` at the repo root:

```properties
storeFile=/absolute/path/to/upload.jks
storePassword=…
keyAlias=sparkora
keyPassword=…
```

Then `./gradlew bundleRelease` produces a signed `.aab` for the Play Console.

**Automated releases** — add these repository secrets
(Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 upload.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias (e.g. `sparkora`) |
| `KEY_PASSWORD` | key password |

Pushing a version tag then builds a signed AAB + APK and attaches them to a
GitHub Release:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

See `.github/workflows/release.yml`. Upload the `.aab` to the Play Console to
publish; the `.apk` is for direct sideloading.

### Notes / v1 limitations

- Amounts are shown in GBP (the platform default); a per-company currency
  setting can be wired in later.
- English-only UI for now (the backend supports per-user `lang`).
- **Push notifications** (shift reminders, leave approvals) need a Firebase
  project + `google-services.json` — a natural next step once you have one.
- Job photos, job messages, shift swaps and documents exist in the backend
  and are natural next features.
