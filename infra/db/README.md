# Database Notes

`schema.sql` is the authoritative starting schema for the project.

Current repo decision:

- Reuse one existing Supabase project instead of creating a third Supabase project.
- Create all shared-location tables inside a dedicated `location` schema.
- Keep the app on server-side Postgres access first; do not depend on exposing this schema to the browser or Android client.

Recommended workflow for the current setup:

1. Open the existing Supabase project, for example `chinese2000`.
2. Run [`schema.sql`](D:/Project/location/infra/db/schema.sql) in the Supabase SQL Editor.
3. Confirm that the `location` schema now contains:
   `app_users`, `devices`, `pairing_tokens`, `shares`, `locations`, `device_latest_locations`, `audit_logs`.
4. Copy the project's Postgres connection string into your local `.env.local` as `DATABASE_URL`.
5. Replace the web mock store with server-side queries against `location.*` tables.
6. Add a migration workflow later if the project standardizes on Prisma, Drizzle, Supabase CLI migrations, or raw SQL migrations.

Demo data:

- Run [`seed_demo.sql`](D:/Project/location/infra/db/seed_demo.sql) to create a repeatable UI/demo dataset.
- The seed creates one viewer, two sharers, two Android devices, one `active` share, one `paused` share, recent location history, and matching `device_latest_locations` rows.
- The seed only touches the fixed demo emails and external device ids defined in that file, so rerunning it refreshes the same demo records instead of creating unbounded duplicates.

Notes:

- This is a Postgres schema namespace, not a separate Supabase project.
- Supabase Auth, Storage, Functions, quotas, and API keys remain project-level resources.
- If the project later needs to query this schema through Supabase's generated Data API, you must explicitly expose the schema and grant permissions. That is not required for the current server-side integration path.
