# Android Shared Location Project Design

## 1. Document Purpose

This document defines the full design for a consent-based Android location sharing system.

Project goal:

- A person installs an Android app and explicitly agrees to share their location.
- The Android app periodically uploads the device's current location to a backend.
- A web viewer shows the latest location on a map.
- The web viewer provides one-click navigation in Google Maps using public transit.
- The system should be deployable with GitHub for source control, Vercel for web/API hosting, and a managed Postgres database.

This document is written so that future development can continue even if the original discussion context is gone.

### 1.1 Current Implementation Status (2026-04-17)

This repository is no longer design-only. The current implementation state is:

- monorepo scaffold exists with `apps/android`, `apps/web`, `packages/api-types`, and `infra/db`
- database choice for the current build is finalized as Supabase Postgres inside the existing `chinese2000` project
- all product tables live in a dedicated `location` schema so this app can coexist with other products in the same Supabase project
- `infra/db/schema.sql` has been applied successfully, and `infra/db/seed_demo.sql` provides repeatable demo data for local and UI verification
- the web app no longer uses the original in-memory mock store for the main viewer flow; it now reads and writes real data through server-side Supabase Data API access
- currently implemented web/API routes are `GET /api/health`, `GET /api/devices`, `GET /api/devices/{deviceId}/latest`, `GET /api/devices/{deviceId}/history`, and `POST /api/location`
- `POST /api/location` now enforces device upload authentication through the `X-Device-Token` header and the device record in `location.devices`
- the dashboard and device detail viewer are working against real database data, including MapLibre rendering and Google Maps transit handoff
- the web root now redirects directly to the active Tokyo demo device view, and the viewer uses a manual refresh action rather than auto-polling
- a manual API upload test has already been verified end to end: valid token returns `200`, invalid token returns `401`, and the viewer reflects the new coordinates
- local verification has been completed: browser smoke test passed, TypeScript typecheck passed, and `next build` passed
- the Android app uses native Android `LocationManager` instead of Google Play Services `FusedLocationProviderClient`, so it works on devices without GMS (e.g. HONOR, Huawei, and other Chinese market phones)
- the Android app has been verified end-to-end on a real HONOR device (LSA-AN00): location collection, battery status, and upload to the public API all work correctly
- the web app is deployed to Vercel and accessible at `https://location.sophia.beer/`
- the Android app uploads to the public Vercel endpoint; the full pipeline from phone GPS to public web viewer is operational
- source code is hosted at `https://github.com/JerryZ8889/location.git`

Immediate gaps from the current repo state:

- no real auth provider is wired yet
- no pairing token creation and consume flow is wired yet
- no real device registration flow is wired yet
- the Android internal MVP still depends on a temporary device token bootstrap instead of a real registration flow
- `Latest Android Update` is not refreshing reliably in the deployed viewer, which indicates continuous Android upload behavior still needs hardening
- no automated tests are in place yet

## 2. Scope And Boundaries

### 2.1 In Scope

- Android native app for a location sharer
- Web application for viewing current location
- Backend API for device registration, authorization, location ingestion, and location retrieval
- Database schema for users, devices, sharing relationships, and location history
- Deployment design using GitHub + Vercel + managed Postgres
- Google Maps deep link for public transit directions
- Security, privacy, consent, and operational requirements

### 2.2 Out Of Scope For MVP

- Stealth tracking
- Hidden app behavior
- Call/SMS/contact access
- Complex route planning inside the app
- Multi-tenant enterprise administration
- iOS app
- Full live streaming location with sub-second updates

### 2.3 Product Positioning

This is a mutual-consent shared location product, not a surveillance tool.

The app must clearly communicate:

- who can view the location
- whether sharing is active
- how to stop sharing
- when the last upload happened

## 3. High-Level Product Requirements

### 3.1 Core User Stories

Sharer:

- As a sharer, I can install the Android app and sign in.
- As a sharer, I can explicitly link my device to a viewer account.
- As a sharer, I can grant location permission and start sharing.
- As a sharer, I can stop sharing at any time.
- As a sharer, I can see whether the app is currently sharing and when the last upload happened.

Viewer:

- As a viewer, I can log into a web page and see the current location on a map.
- As a viewer, I can see the last update time and approximate accuracy.
- As a viewer, I can open Google Maps and get public transit directions to the latest location.

System:

- As a system, I can safely receive, store, and serve location data.
- As a system, I can preserve an audit trail for authorization changes and revocations.

### 3.2 Non-Functional Requirements

- Clear consent model
- Secure authentication
- Reasonable battery usage on Android
- Tolerant of temporary network failures
- Simple deployment and maintenance
- Good enough freshness for human coordination, not emergency tracking

## 4. Recommended Architecture

## 4.1 MVP Architecture Summary

- Android app: Kotlin native app that collects location and uploads it
- Backend API: Next.js API routes or Route Handlers deployed on Vercel
- Database: Supabase Postgres in the existing `chinese2000` project, isolated inside the `location` schema
- Web viewer: Next.js app deployed on Vercel
- Map rendering: MapLibre with OpenStreetMap-compatible tiles
- Navigation handoff: Google Maps URL with `travelmode=transit`
- Database access pattern: server-side Supabase Data API access from Next.js; clients should not call the database directly

### 4.2 Why This Architecture

Reasons:

- GitHub + Vercel is fast to ship and easy to maintain
- Vercel is well suited for a web app plus standard API endpoints
- Managed Postgres reduces operational burden
- MapLibre avoids immediate Google Maps JavaScript billing complexity for the embedded map
- Google Maps deep links still provide strong navigation UX

### 4.3 Real-Time Strategy

For MVP, do not build full WebSocket real-time.

Use:

- Android upload interval: around every 30 to 60 seconds while actively sharing
- Viewer refresh: manual refresh button in the current deployed build, with optional polling reconsidered later if truly needed

Why:

- Simpler than real-time sockets
- Works well with Vercel request-based infrastructure
- Good enough for first release

If later near-live updates are needed:

- Supabase Realtime
- Pusher
- Ably

should be considered instead of custom WebSocket infrastructure on Vercel.

## 5. End-To-End Data Flow

### 5.1 Initial Pairing Flow

1. Sharer installs Android app.
2. Sharer signs in or creates an account.
3. Viewer signs into the web app.
4. Viewer generates a pairing code or invite token.
5. Sharer enters the code in the Android app.
6. Backend creates a sharing relationship between viewer and device.
7. Android app requests location permission.
8. Sharer explicitly enables sharing.

### 5.2 Location Upload Flow

1. Android app requests a location update.
2. App receives latitude, longitude, accuracy, timestamp, and optional battery info.
3. App sends the payload to `POST /api/location`.
4. Backend validates auth and device ownership.
5. Backend stores the location and updates a materialized latest-location record.
6. Viewer polling endpoint returns the newest record.
7. Web page updates the marker on the map.

### 5.3 Stop Sharing Flow

1. Sharer turns off sharing in the app.
2. Android foreground location service stops.
3. Backend marks the share relationship as inactive if needed.
4. Viewer sees a status like `sharing paused` or `last updated at ...`.

## 6. Android App Design

### 6.1 Recommended Stack

- Language: Kotlin
- Minimum approach: Android native app
- Location API: Android native `LocationManager` (no GMS dependency, supports Chinese market devices)
- Local persistence: Room
- Background orchestration: Foreground Service plus WorkManager for retry
- Networking: Retrofit or Ktor client
- JSON: Kotlin serialization or Moshi

### 6.2 Permissions

Minimum initial permissions:

- `ACCESS_COARSE_LOCATION`
- `ACCESS_FINE_LOCATION`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS` on newer Android versions if required for notification UX

Only request background location if the product truly requires continuous sharing when the app is not visible:

- `ACCESS_BACKGROUND_LOCATION`

Permission strategy:

- First request while-in-use location
- Explain why sharing needs continuous access
- Only then request background location if the user enables persistent sharing

Do not request every permission at startup.

### 6.3 Foreground Service Requirement

If the product shares location continuously, use a foreground service with a persistent notification.

Notification text should be explicit, for example:

- `Location sharing active`
- `Last sent 1 minute ago`

This is both a platform requirement and a consent/safety requirement.

### 6.4 Android App Screens

Recommended first-version screens:

1. Welcome / Sign In
2. Pair Device
3. Permission Request
4. Sharing Home
5. Settings / Privacy

Sharing Home should show:

- sharing status
- paired viewer name or account
- last successful upload time
- current accuracy if available
- start sharing button
- stop sharing button

### 6.5 App State Model

Suggested states:

- unpaired
- paired_but_permission_missing
- ready_but_sharing_off
- sharing_active
- upload_degraded
- sharing_paused

### 6.6 Location Collection Strategy

For MVP:

- Request balanced location updates
- Upload every 30 to 60 seconds when moving
- Reduce to every 5 to 15 minutes if device is stationary

Suggested upload payload:

```json
{
  "deviceId": "dev_123",
  "lat": 35.681236,
  "lng": 139.767125,
  "accuracyMeters": 18.5,
  "capturedAt": "2026-04-06T10:15:00Z",
  "batteryPercent": 72,
  "isCharging": false,
  "speedMps": 0.0
}
```

Important design rule:

The device should upload raw coordinates.
Do not make the address string the primary stored format.

Reason:

- map rendering needs coordinates
- navigation deep links need coordinates
- reverse geocoding can fail or vary by provider
- address text can be added later as derived data

### 6.7 Offline And Retry Behavior

Use Room to queue unsent location records.

Behavior:

- if upload fails, mark record as pending
- retry with WorkManager
- keep only a bounded queue, for example the last 500 records
- mark records as uploaded once acknowledged

This avoids data loss during short connectivity gaps.

### 6.8 Battery And Data Usage Controls

Controls to include:

- sharing interval mode: normal / battery saver
- upload only if accuracy is acceptable
- reduce frequency when speed is near zero
- allow manual one-time refresh

MVP can start with a fixed interval and later add adaptive logic.

### 6.9 Android Security Requirements

- Store tokens in EncryptedSharedPreferences or another secure storage mechanism
- Use HTTPS only
- Do not log access tokens or raw personal data in release logs
- Add token refresh behavior if using session tokens
- Allow remote revocation from backend

## 7. Web Viewer Design

### 7.1 Recommended Stack

- Framework: Next.js
- Hosting: Vercel
- Auth: NextAuth or Supabase Auth or Clerk
- Map: MapLibre GL JS

### 7.2 Core Viewer Screens

1. Login
2. Device List
3. Device Detail Map Page
4. Share Management

### 7.3 Device Detail Map Page

This is the main page.

Page elements:

- map centered on latest location
- current marker
- optional accuracy circle
- device/share status
- last updated timestamp
- battery status
- refresh button
- open in Google Maps button
- optional recent history list

### 7.4 Map Rendering Choice

Recommendation:

- Use MapLibre for embedded display
- Use OpenStreetMap-compatible tiles or a commercial tile provider

Reason:

- lower startup complexity
- no immediate Google Maps JS API billing requirement for simple map display

### 7.5 Google Maps Public Transit Button

Use a standard URL format:

```text
https://www.google.com/maps/dir/?api=1&destination={lat},{lng}&travelmode=transit
```

If the viewer's browser or device has a current location, Google Maps will typically use that as the origin.

If the product later wants an explicit origin:

```text
https://www.google.com/maps/dir/?api=1&origin={originLat},{originLng}&destination={lat},{lng}&travelmode=transit
```

This keeps routing logic out of the product and delegates transit planning to Google Maps.

### 7.6 Viewer Refresh Model

MVP approach:

- Poll `GET /api/devices/{deviceId}/latest` every 10 to 15 seconds

Display states:

- fresh: updated within 2 minutes
- stale: updated within 2 to 15 minutes
- offline or paused: older than 15 minutes or share disabled

## 8. Backend Design

### 8.1 Backend Responsibilities

The backend is required.

It is responsible for:

- authenticating users
- authorizing who can view which device
- receiving location uploads
- storing history
- serving latest location
- revoking access
- recording audit events

Even if hosted with Vercel serverless functions, this is still the backend.

### 8.2 Why A Backend Is Needed

Without a backend:

- Android app would have nowhere reliable to upload data
- access control would be weak or absent
- history would be hard to preserve
- viewing logic would become insecure
- secrets and tokens would be exposed

### 8.3 API Style

Use a small REST API first.

Recommended initial endpoints:

#### Auth

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `POST /api/auth/refresh`

If using hosted auth, some of these may be provided by the auth provider instead.

#### Pairing And Shares

- `POST /api/pairing/create`
- `POST /api/pairing/consume`
- `POST /api/shares/{shareId}/pause`
- `POST /api/shares/{shareId}/resume`
- `POST /api/shares/{shareId}/revoke`

#### Devices

- `POST /api/devices/register`
- `GET /api/devices`
- `GET /api/devices/{deviceId}`

#### Locations

- `POST /api/location`
- `GET /api/devices/{deviceId}/latest`
- `GET /api/devices/{deviceId}/history?from=...&to=...&limit=...`

#### Health

- `GET /api/health`

Current repo status:

- already implemented: `GET /api/health`, `GET /api/devices`, `GET /api/devices/{deviceId}/latest`, `GET /api/devices/{deviceId}/history`, `POST /api/location`
- still pending: auth endpoints, pairing token create/consume endpoints, share pause/resume/revoke endpoints, and device registration endpoint

### 8.4 Example API Contracts

#### `POST /api/location`

Request:

```json
{
  "deviceId": "dev_123",
  "lat": 35.681236,
  "lng": 139.767125,
  "accuracyMeters": 18.5,
  "capturedAt": "2026-04-06T10:15:00Z",
  "batteryPercent": 72,
  "isCharging": false,
  "speedMps": 0.0
}
```

Response:

```json
{
  "ok": true,
  "receivedAt": "2026-04-06T10:15:02Z"
}
```

Validation rules:

- coordinates must be within valid latitude/longitude ranges
- `capturedAt` must be parseable and not wildly in the future
- authenticated caller must own the device or hold a valid device token

#### `GET /api/devices/{deviceId}/latest`

Response:

```json
{
  "deviceId": "dev_123",
  "shareStatus": "active",
  "location": {
    "lat": 35.681236,
    "lng": 139.767125,
    "accuracyMeters": 18.5,
    "capturedAt": "2026-04-06T10:15:00Z",
    "batteryPercent": 72,
    "isCharging": false
  }
}
```

### 8.5 Authentication Options

Good MVP choices:

- Supabase Auth
- Clerk
- NextAuth with email or OAuth providers

Recommendation:

- Use a hosted auth provider to reduce security surface area

### 8.6 Authorization Model

Separate authentication from authorization.

Authentication answers:

- who is this user

Authorization answers:

- can this user view this device
- can this device upload to this share

Rules:

- sharer can own one or more devices
- viewer can be granted access to one or more devices
- each share can be active, paused, revoked, or expired

### 8.7 Serverless Suitability

Vercel is suitable for:

- standard API routes
- auth callbacks
- latest-location retrieval
- historical location queries
- admin and pairing flows

Do not over-design around long-running custom socket servers for MVP.

## 9. Database Design

### 9.1 Recommended Database

- Supabase Postgres for the current implementation

Reason:

- works well with Vercel-hosted apps
- managed backups and connectivity
- SQL is a good fit for relational authorization and history storage
- the current free-tier constraint is handled by reusing the existing `chinese2000` project and isolating this app in the `location` schema

### 9.2 Core Tables

The current SQL implementation in `infra/db/schema.sql` uses the following table names.

#### `app_users`

Purpose:

- stores application users

Fields:

- `id`
- `email`
- `name`
- `created_at`
- `auth_provider`

#### `devices`

Purpose:

- stores Android device registrations

Fields:

- `id`
- `owner_user_id`
- `external_device_id`
- `platform`
- `device_name`
- `device_token_hash`
- `created_at`
- `last_seen_at`
- `is_active`

#### `shares`

Purpose:

- stores permission relationships between a device and a viewer

Fields:

- `id`
- `device_id`
- `viewer_user_id`
- `status`
- `created_at`
- `paused_at`
- `revoked_at`
- `expires_at`

#### `pairing_tokens`

Purpose:

- temporary pairing or invite codes

Fields:

- `id`
- `created_by_user_id`
- `token_hash`
- `expires_at`
- `consumed_at`
- `device_id`

#### `locations`

Purpose:

- historical location events

Fields:

- `id`
- `device_id`
- `lat`
- `lng`
- `accuracy_meters`
- `captured_at`
- `received_at`
- `battery_percent`
- `is_charging`
- `speed_mps`

Recommended indexes:

- `(device_id, captured_at desc)`
- `(device_id, received_at desc)`

#### `device_latest_locations`

Purpose:

- fast read model for latest map display

Fields:

- `device_id`
- `location_id`
- `lat`
- `lng`
- `accuracy_meters`
- `captured_at`
- `received_at`
- `battery_percent`
- `is_charging`

#### `audit_logs`

Purpose:

- records consent and security events

Fields:

- `id`
- `actor_user_id`
- `device_id`
- `action`
- `metadata_json`
- `created_at`

### 9.3 Data Retention

MVP decision needed:

- keep full location history for 30 days
- or 90 days

Recommendation:

- start with 30 days
- aggregate or delete older data unless there is a clear product need

Reason:

- reduces privacy risk
- controls database growth

## 10. Deployment Design

### 10.1 Source Control

Use GitHub as the single source of truth.

Recommended repositories:

Option A, one monorepo:

- `apps/android`
- `apps/web`
- `packages/api-types`
- `packages/shared`

Option B, separate repos:

- `location-android`
- `location-web`

Recommendation:

- start with a monorepo if one developer or a small team is building both sides

### 10.2 Vercel Deployment

Deploy the Next.js web app to Vercel.

Vercel responsibilities:

- host viewer web UI
- host API routes
- manage environment variables
- preview deployments for pull requests

Environment variables likely needed:

- `DATABASE_URL`
- `AUTH_SECRET`
- `NEXTAUTH_URL` or provider-specific variables
- `MAP_TILE_URL` if using a tile provider
- `GOOGLE_MAPS_LINK_BASE` optional

### 10.3 Database Deployment

Use:

- existing Supabase project `chinese2000`

Implementation rule:

- keep all tables for this product in the dedicated `location` schema instead of creating a separate Supabase project for now

Responsibilities:

- managed Postgres
- backups
- connection management
- SQL migrations

### 10.4 Android Distribution

For MVP, there are three realistic options:

1. Android Studio debug install for internal testing
2. Direct APK distribution for trusted testers
3. Google Play internal testing

Recommendation:

- start with direct APK or Play internal testing

Do not assume a user can install via a normal website without explicit Android package installation steps.

## 11. Suggested Repository Structure

```text
location/
  DESIGN.md
  apps/
    android/
      app/
      build.gradle.kts
      README.md
    web/
      app/
      components/
      lib/
      public/
      package.json
      README.md
  packages/
    api-types/
    shared/
  infra/
    db/
      migrations/
      schema.sql
    docs/
```

## 12. API Security Design

### 12.1 Authentication Model

Two acceptable patterns:

Pattern A:

- user login for web viewer
- device-specific token for Android uploader

Pattern B:

- user login for both app and web

Recommendation:

- use user login for both plus a registered device identity underneath

This is easier to reason about for consent and revocation.

### 12.2 Token Handling

- short-lived access tokens
- refresh tokens if using a session-based auth provider
- hash device tokens in database if static tokens are used
- rotate tokens when a device is re-paired

Current internal MVP note:

- Android uploads currently use a temporary device token sent in the `X-Device-Token` header
- build-time local configuration is read from `apps/android/local.properties`
- this is a developer bootstrap only and should be replaced by a real device registration flow before broader use

### 12.3 Authorization Checks

Every location read must verify:

- requesting user is authorized on an active share

Every location write must verify:

- uploading device belongs to the authenticated owner
- or uploading device holds a valid upload credential

### 12.4 Abuse Prevention

- rate limit location ingestion per device
- rate limit pairing token generation
- log failed auth attempts
- lock or revoke suspicious tokens

## 13. Privacy And Consent Requirements

This section is mandatory, not optional.

### 13.1 Consent Rules

- location sharing must be explicit
- share target must be visible to the sharer
- stop sharing control must be easy to find
- persistent notification should remain visible during active continuous sharing

### 13.2 User-Facing Notices

The Android app should include:

- why location is collected
- who can view it
- how often it may be uploaded
- how to stop sharing
- how long history is stored

### 13.3 Revocation

Both sides should be able to trigger revocation depending on product policy.

At minimum:

- sharer can stop sharing immediately
- viewer can remove a device from their list

## 14. Mapping And Address Strategy

### 14.1 Primary Storage Format

Primary format:

- latitude
- longitude
- accuracy
- timestamps

### 14.2 Reverse Geocoding

Address text is optional derived data.

If added later, reverse geocoding should be used only for display convenience.

Do not make the system dependent on address strings for core logic.

### 14.3 Accuracy Presentation

Viewer page should show:

- marker for estimated point
- optional circle for accuracy radius

This prevents overclaiming precision.

## 15. Observability And Operations

### 15.1 Logging

Log:

- login success/failure
- pairing creation and consumption
- share start/stop/revoke
- location upload success/failure summary
- stale device detection

Do not log:

- raw auth secrets
- full sensitive personal payloads in plaintext unless operationally necessary

### 15.2 Monitoring

Minimum monitors:

- API error rate
- location ingestion volume
- database connection failures
- stale devices with no updates

### 15.3 Admin Diagnostics

Useful admin data:

- last upload timestamp per device
- last auth event
- current share status
- upload error count

## 16. Failure Modes And Handling

### 16.1 Android App Killed By System

Handling:

- foreground service for active sharing
- app startup recovery checks
- WorkManager for deferred retries

### 16.2 Poor GPS Accuracy

Handling:

- show accuracy radius
- reject or mark low-quality readings above a threshold
- allow manual refresh

### 16.3 No Network

Handling:

- queue recent locations locally
- retry later
- show `upload pending` in app

### 16.4 Viewer Sees Stale Data

Handling:

- show explicit stale label
- display exact last update time
- avoid implying the user is still at that location now

## 17. MVP Milestones

### Phase 1: Technical Skeleton

Status:

- completed for the current baseline

- create monorepo
- scaffold Android app
- scaffold Next.js app
- connect web app to local and deployable Next.js runtime
- provision Postgres or reuse existing Supabase project
- define schema and migrations
- replace the original mock web data path with real database access

Exit criteria met for the current baseline:

- web app runs locally and builds successfully
- health endpoint works
- database connection works

### Phase 2: Auth And Pairing

Status:

- not started

- implement user auth
- implement pairing token flow
- implement device registration
- build basic share management UI

Exit criteria:

- viewer can pair one Android device
- share state is stored in DB

### Phase 3: Android Location Upload

Status:

- substantially complete

- Android runtime permission request flow exists
- Android foreground service exists
- current-location capture through native `LocationManager` exists (replaced `FusedLocationProviderClient` to support devices without Google Play Services)
- `POST /api/location` exists and now enforces device token authentication
- local build configuration exists through `apps/android/local.properties`
- end-to-end verified on a real HONOR device uploading to the public Vercel endpoint
- still needed: real device registration, local retry queue, and broader failure handling

Exit criteria met:

- Android app can send valid location payloads
- backend stores them

### Phase 4: Viewer Map

Status:

- substantially complete for a local demo baseline

- build device detail page
- render latest marker on map
- show timestamps and accuracy
- add Google Maps transit button
- connect viewer to real database data instead of the original mock store

Remaining work inside this phase:

- add polling or refresh UX for live updates
- add optional accuracy circle and history presentation
- add auth-gated access instead of open local demo flow

Exit criteria met for the local demo baseline:

- viewer can see a marker and open navigation

### Phase 5: Hardening

Status:

- not started

- add rate limiting
- add audit logs
- improve stale-state UX
- improve battery strategy
- write tests

Exit criteria:

- system is safe enough for broader testing

## 18. Testing Strategy

### 18.1 Android Tests

- permission flow tests
- location payload serialization tests
- retry queue tests
- service lifecycle tests where practical

### 18.2 Backend Tests

- auth and authorization tests
- input validation tests
- location ingestion tests
- latest-location query tests

### 18.3 Web Tests

- map page render tests
- polling behavior tests
- button link generation tests

### 18.4 Manual End-To-End Test

Critical scenario:

1. Pair device
2. Start sharing
3. Move to a different location
4. Confirm web page updates within expected delay
5. Click transit button
6. Confirm Google Maps opens with destination coordinates

## 19. Performance And Cost Expectations

### 19.1 MVP Load Model

For a small user base, the system load is modest.

Example:

- 50 devices
- each uploads once per minute
- about 72,000 uploads per day

This is manageable for a simple API and Postgres-backed design if queries and indexes are reasonable.

### 19.2 Cost Drivers

Main cost drivers:

- database size from location history
- map tile usage
- auth provider pricing if usage grows
- function invocations on Vercel

### 19.3 Cost Control

- limit history retention
- prefer explicit manual refresh in the viewer unless there is a strong need for automatic polling
- store latest location separately for fast reads
- avoid reverse geocoding every write unless necessary

## 20. Open Decisions

Resolved decisions:

1. Database provider for the current build: Supabase
2. Free-tier strategy: reuse the existing `chinese2000` project and isolate this app in the `location` schema
3. Primary data shape: coordinate-first storage, not address-first storage
4. Schema support for one sharer to share with multiple viewers: yes
5. Schema support for one viewer to track multiple devices: yes

Still-open decisions:

1. Auth provider choice
2. Whether background location is required for MVP
3. History retention period
4. Final Android upload credential model after removing the temporary device-token bootstrap
5. Whether direct APK distribution is acceptable or Play internal testing is required
6. Whether viewer polling alone is enough after MVP or if lightweight realtime should be added later

## 21. Final Recommendations

If the goal is to get a solid first version shipped quickly, the recommended implementation is:

- Android app in Kotlin
- Next.js web app on Vercel
- Supabase Postgres
- MapLibre for embedded map
- Google Maps URL for transit navigation
- polling instead of custom real-time sockets
- explicit consent and persistent notification for continuous sharing

## 22. Immediate Next Build Order

Build from the current repo state in this order:

1. ~~Run the current Android internal MVP on a real device and confirm live uploads reach the viewer~~ **Done** — verified on HONOR LSA-AN00, uploads reach the public Vercel endpoint and display on the web viewer
2. Simplify the web entry: remove the dashboard device-list page (which shows paused/active status selection) and redirect the root URL directly to the active device map view — for single-device MVP there is no need for a device picker
3. Fix the `Latest Android Update` staleness issue by making continuous Android uploads reliable while the app is backgrounded and unplugged
4. Add a proper device registration flow to replace the temporary local-properties device token bootstrap
5. Implement auth in the web app and settle the Android-compatible session strategy
6. Implement pairing token create and consume flows plus share management actions
7. Add upload credential hardening, rate limiting, and stronger audit coverage
8. Add automated tests for API contracts, repository logic, and viewer flows

This order minimizes wasted work and keeps the first usable version small.

## 23. Reference Links

Official Android location and background work references:

- Android location permissions: https://developer.android.com/develop/sensors-and-location/location/permissions
- Android current location: https://developer.android.com/develop/sensors-and-location/location/retrieve-current
- Android location updates: https://developer.android.com/develop/sensors-and-location/location/request-updates
- Android background location: https://developer.android.com/develop/sensors-and-location/location/background
- Android foreground services: https://developer.android.com/develop/background-work/services/foreground-services

Deployment and navigation references:

- Vercel Functions: https://vercel.com/docs/functions
- Vercel Postgres docs: https://vercel.com/docs/postgres
- Google Maps URLs: https://developers.google.com/maps/documentation/urls/get-started
- Google Maps JavaScript API overview: https://developers.google.com/maps/documentation/javascript/overview

## 24. Short Implementation Summary

The product should not be built as `address text upload + address display`.
It should be built as `coordinate upload + secure backend + map display + navigation handoff`.

That is the simplest architecture that remains technically correct, scalable enough for MVP, and aligned with Android platform constraints.
