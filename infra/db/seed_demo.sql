BEGIN;

-- Repeatable demo seed for local development and UI verification.
-- Safe to rerun: it only targets the fixed demo emails and external device ids below.

INSERT INTO location.app_users (email, name, auth_provider)
VALUES
  ('viewer.demo@location.test', 'Jerry Viewer', 'demo-seed'),
  ('sharer.tokyo@location.test', 'Mika Tokyo', 'demo-seed'),
  ('sharer.shibuya@location.test', 'Ren Shibuya', 'demo-seed')
ON CONFLICT (email) DO UPDATE
SET
  name = EXCLUDED.name,
  auth_provider = EXCLUDED.auth_provider;

INSERT INTO location.devices (
  owner_user_id,
  external_device_id,
  platform,
  device_name,
  device_token_hash,
  is_active,
  last_seen_at
)
SELECT
  u.id,
  v.external_device_id,
  'android',
  v.device_name,
  v.device_token_hash,
  TRUE,
  NOW()
FROM (
  VALUES
    ('sharer.tokyo@location.test', 'demo-tokyo-android', 'Tokyo Demo Phone', 'demo-token-tokyo'),
    ('sharer.shibuya@location.test', 'demo-shibuya-android', 'Shibuya Demo Phone', 'demo-token-shibuya')
) AS v(email, external_device_id, device_name, device_token_hash)
JOIN location.app_users u
  ON u.email = v.email
ON CONFLICT (external_device_id) DO UPDATE
SET
  owner_user_id = EXCLUDED.owner_user_id,
  platform = EXCLUDED.platform,
  device_name = EXCLUDED.device_name,
  device_token_hash = EXCLUDED.device_token_hash,
  is_active = EXCLUDED.is_active,
  last_seen_at = EXCLUDED.last_seen_at;

INSERT INTO location.shares (
  device_id,
  viewer_user_id,
  status,
  created_at,
  paused_at,
  revoked_at,
  expires_at
)
SELECT
  d.id,
  viewer.id,
  v.status,
  NOW() - INTERVAL '3 days',
  CASE
    WHEN v.status = 'paused' THEN NOW() - INTERVAL '25 minutes'
    ELSE NULL
  END,
  NULL,
  NULL
FROM (
  VALUES
    ('demo-tokyo-android', 'active'),
    ('demo-shibuya-android', 'paused')
) AS v(external_device_id, status)
JOIN location.devices d
  ON d.external_device_id = v.external_device_id
JOIN location.app_users viewer
  ON viewer.email = 'viewer.demo@location.test'
ON CONFLICT (device_id, viewer_user_id) DO UPDATE
SET
  status = EXCLUDED.status,
  paused_at = EXCLUDED.paused_at,
  revoked_at = NULL,
  expires_at = NULL;

DELETE FROM location.device_latest_locations
WHERE device_id IN (
  SELECT id
  FROM location.devices
  WHERE external_device_id IN ('demo-tokyo-android', 'demo-shibuya-android')
);

DELETE FROM location.locations
WHERE device_id IN (
  SELECT id
  FROM location.devices
  WHERE external_device_id IN ('demo-tokyo-android', 'demo-shibuya-android')
);

WITH inserted_locations AS (
  INSERT INTO location.locations (
    device_id,
    lat,
    lng,
    accuracy_meters,
    captured_at,
    received_at,
    battery_percent,
    is_charging,
    speed_mps
  )
  SELECT
    d.id,
    s.lat,
    s.lng,
    s.accuracy_meters,
    s.captured_at,
    s.received_at,
    s.battery_percent,
    s.is_charging,
    s.speed_mps
  FROM location.devices d
  JOIN (
    VALUES
      ('demo-tokyo-android', 35.68040, 139.76902, 22.0, NOW() - INTERVAL '18 minutes', NOW() - INTERVAL '18 minutes', 88, TRUE, 0.0),
      ('demo-tokyo-android', 35.68085, 139.76845, 16.0, NOW() - INTERVAL '7 minutes',  NOW() - INTERVAL '7 minutes', 86, TRUE, 0.4),
      ('demo-tokyo-android', 35.68124, 139.76712, 12.0, NOW() - INTERVAL '2 minutes',  NOW() - INTERVAL '2 minutes', 84, TRUE, 0.2),
      ('demo-shibuya-android', 35.65910, 139.70010, 28.0, NOW() - INTERVAL '55 minutes', NOW() - INTERVAL '55 minutes', 47, FALSE, 0.0),
      ('demo-shibuya-android', 35.65950, 139.70050, 31.0, NOW() - INTERVAL '27 minutes', NOW() - INTERVAL '27 minutes', 45, FALSE, 0.0)
  ) AS s(
    external_device_id,
    lat,
    lng,
    accuracy_meters,
    captured_at,
    received_at,
    battery_percent,
    is_charging,
    speed_mps
  )
    ON d.external_device_id = s.external_device_id
  RETURNING
    id,
    device_id,
    lat,
    lng,
    accuracy_meters,
    captured_at,
    received_at,
    battery_percent,
    is_charging
),
latest_per_device AS (
  SELECT DISTINCT ON (device_id)
    device_id,
    id AS location_id,
    lat,
    lng,
    accuracy_meters,
    captured_at,
    received_at,
    battery_percent,
    is_charging
  FROM inserted_locations
  ORDER BY device_id, captured_at DESC, received_at DESC
)
INSERT INTO location.device_latest_locations (
  device_id,
  location_id,
  lat,
  lng,
  accuracy_meters,
  captured_at,
  received_at,
  battery_percent,
  is_charging
)
SELECT
  device_id,
  location_id,
  lat,
  lng,
  accuracy_meters,
  captured_at,
  received_at,
  battery_percent,
  is_charging
FROM latest_per_device;

UPDATE location.devices
SET last_seen_at = latest.captured_at
FROM location.device_latest_locations latest
WHERE latest.device_id = location.devices.id
  AND location.devices.external_device_id IN ('demo-tokyo-android', 'demo-shibuya-android');

DELETE FROM location.audit_logs
WHERE action = 'demo_seed'
  AND device_id IN (
    SELECT id
    FROM location.devices
    WHERE external_device_id IN ('demo-tokyo-android', 'demo-shibuya-android')
  );

INSERT INTO location.audit_logs (actor_user_id, device_id, action, metadata_json)
SELECT
  viewer.id,
  d.id,
  'demo_seed',
  jsonb_build_object(
    'label', v.label,
    'seeded_at', NOW(),
    'status', v.status
  )
FROM (
  VALUES
    ('demo-tokyo-android', 'Tokyo Station demo path', 'active'),
    ('demo-shibuya-android', 'Shibuya Station paused demo', 'paused')
) AS v(external_device_id, label, status)
JOIN location.devices d
  ON d.external_device_id = v.external_device_id
JOIN location.app_users viewer
  ON viewer.email = 'viewer.demo@location.test';

COMMIT;
