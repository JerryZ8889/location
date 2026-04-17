CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Shared-location tables live in a dedicated schema so this app can share one
-- Supabase project with other products without mixing table namespaces.
CREATE SCHEMA IF NOT EXISTS location;

CREATE TABLE IF NOT EXISTS location.app_users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT NOT NULL UNIQUE,
  name TEXT,
  auth_provider TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS location.devices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id UUID NOT NULL REFERENCES location.app_users(id) ON DELETE CASCADE,
  external_device_id TEXT NOT NULL UNIQUE,
  platform TEXT NOT NULL CHECK (platform IN ('android')),
  device_name TEXT NOT NULL,
  device_token_hash TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS location.pairing_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_by_user_id UUID NOT NULL REFERENCES location.app_users(id) ON DELETE CASCADE,
  device_id UUID REFERENCES location.devices(id) ON DELETE SET NULL,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS location.shares (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id UUID NOT NULL REFERENCES location.devices(id) ON DELETE CASCADE,
  viewer_user_id UUID NOT NULL REFERENCES location.app_users(id) ON DELETE CASCADE,
  status TEXT NOT NULL CHECK (status IN ('active', 'paused', 'pending', 'revoked')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  paused_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ,
  UNIQUE (device_id, viewer_user_id)
);

CREATE TABLE IF NOT EXISTS location.locations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id UUID NOT NULL REFERENCES location.devices(id) ON DELETE CASCADE,
  lat DOUBLE PRECISION NOT NULL CHECK (lat >= -90 AND lat <= 90),
  lng DOUBLE PRECISION NOT NULL CHECK (lng >= -180 AND lng <= 180),
  accuracy_meters DOUBLE PRECISION NOT NULL CHECK (accuracy_meters >= 0),
  captured_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  battery_percent INTEGER CHECK (battery_percent >= 0 AND battery_percent <= 100),
  is_charging BOOLEAN,
  speed_mps DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS location.device_latest_locations (
  device_id UUID PRIMARY KEY REFERENCES location.devices(id) ON DELETE CASCADE,
  location_id UUID NOT NULL REFERENCES location.locations(id) ON DELETE CASCADE,
  lat DOUBLE PRECISION NOT NULL,
  lng DOUBLE PRECISION NOT NULL,
  accuracy_meters DOUBLE PRECISION NOT NULL,
  captured_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  battery_percent INTEGER,
  is_charging BOOLEAN
);

CREATE TABLE IF NOT EXISTS location.audit_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id UUID REFERENCES location.app_users(id) ON DELETE SET NULL,
  device_id UUID REFERENCES location.devices(id) ON DELETE SET NULL,
  action TEXT NOT NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_devices_owner_user_id
  ON location.devices(owner_user_id);

CREATE INDEX IF NOT EXISTS idx_pairing_tokens_expires_at
  ON location.pairing_tokens(expires_at);

CREATE INDEX IF NOT EXISTS idx_shares_viewer_user_id
  ON location.shares(viewer_user_id);

CREATE INDEX IF NOT EXISTS idx_locations_device_captured_at_desc
  ON location.locations(device_id, captured_at DESC);

CREATE INDEX IF NOT EXISTS idx_locations_device_received_at_desc
  ON location.locations(device_id, received_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_device_created_at_desc
  ON location.audit_logs(device_id, created_at DESC);
