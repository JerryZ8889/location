# Location Platform

This repository contains the initial project skeleton for a consent-based Android location sharing product.

Primary documentation:

- [DESIGN.md](D:/Project/location/DESIGN.md)

Database setup decision:

- Reuse an existing Supabase project when needed and place this app's tables in a dedicated `location` schema.
- Keep database access server-side first, instead of exposing the schema directly to clients.

Current workspace layout:

- `apps/web`: Next.js viewer app and API placeholders
- `apps/android`: Android sharer app skeleton
- `packages/api-types`: shared TypeScript contracts
- `infra/db`: Postgres schema and migration starting point

Recommended first implementation order:

1. Provision Postgres or reuse an existing Supabase project, then apply the SQL schema.
2. Install web dependencies and run the Next.js app.
3. Replace the mock API store with real database access against `location.*` tables.
4. Open the Android project in Android Studio and connect it to the backend.

## Web App

The web app currently includes:

- a dashboard listing mock devices
- a device detail page
- a MapLibre-based map component
- a Google Maps public transit deep link button
- placeholder API routes backed by an in-memory store

## Android App

The Android app currently includes:

- a Compose-based home screen
- start and stop sharing controls
- a foreground service skeleton
- manifest permissions and notification channel setup

It is not yet wired to a real backend or full location update pipeline.
