import { createHash } from "node:crypto";
import type {
  DeviceSummary,
  LatestLocationResponse,
  LocationPoint,
  LocationUploadPayload,
  ShareStatus
} from "@location/api-types";
import { buildSelectQuery, supabaseRequest } from "./supabase-rest";

interface DbUser {
  id: string;
  email: string;
  name: string | null;
}

interface DbDevice {
  id: string;
  device_name: string;
  external_device_id: string;
  owner_user_id: string;
  device_token_hash: string | null;
  is_active: boolean;
  last_seen_at: string | null;
}

interface DbShare {
  device_id: string;
  status: ShareStatus;
  created_at: string;
}

interface DbLatestLocation {
  device_id: string;
  lat: number;
  lng: number;
  accuracy_meters: number;
  captured_at: string;
  received_at: string;
  battery_percent: number | null;
  is_charging: boolean | null;
}

interface DbLocationRow extends DbLatestLocation {
  id: string;
  speed_mps: number | null;
}

function toLocationPoint(
  row: Pick<
    DbLatestLocation,
    "accuracy_meters" | "battery_percent" | "captured_at" | "is_charging" | "lat" | "lng"
  > & { speed_mps?: number | null }
): LocationPoint {
  return {
    lat: row.lat,
    lng: row.lng,
    accuracyMeters: row.accuracy_meters,
    capturedAt: row.captured_at,
    batteryPercent: row.battery_percent,
    isCharging: row.is_charging,
    speedMps: row.speed_mps ?? null
  };
}

function pickShareStatus(deviceId: string, shares: DbShare[]): ShareStatus {
  const deviceStatuses = shares
    .filter((share) => share.device_id === deviceId)
    .map((share) => share.status);

  if (deviceStatuses.includes("active")) {
    return "active";
  }

  if (deviceStatuses.includes("pending")) {
    return "pending";
  }

  if (deviceStatuses.includes("paused")) {
    return "paused";
  }

  if (deviceStatuses.includes("revoked")) {
    return "revoked";
  }

  return "pending";
}

async function listUsers() {
  return supabaseRequest<DbUser[]>(
    "app_users",
    { query: buildSelectQuery("id,email,name") }
  );
}

async function listShares() {
  return supabaseRequest<DbShare[]>(
    "shares",
    { query: buildSelectQuery("device_id,status,created_at") }
  );
}

async function listLatestLocations() {
  return supabaseRequest<DbLatestLocation[]>(
    "device_latest_locations",
    {
      query: buildSelectQuery(
        "device_id,lat,lng,accuracy_meters,captured_at,received_at,battery_percent,is_charging"
      )
    }
  );
}

export async function listDevices() {
  const [devices, users, latestLocations, shares] = await Promise.all([
    supabaseRequest<DbDevice[]>(
      "devices",
      {
        query: buildSelectQuery(
          "id,device_name,external_device_id,owner_user_id,device_token_hash,is_active,last_seen_at",
          { order: "created_at.desc" }
        )
      }
    ),
    listUsers(),
    listLatestLocations(),
    listShares()
  ]);

  const userById = new Map(users.map((user) => [user.id, user]));
  const latestByDeviceId = new Map(
    latestLocations.map((location) => [location.device_id, location])
  );

  return devices.map<DeviceSummary>((device) => {
    const owner = userById.get(device.owner_user_id);
    const latest = latestByDeviceId.get(device.id);

    return {
      id: device.external_device_id,
      name: device.device_name,
      ownerDisplayName: owner?.name ?? owner?.email ?? "Unknown sharer",
      shareStatus: pickShareStatus(device.id, shares),
      lastCapturedAt: latest?.captured_at ?? null,
      batteryPercent: latest?.battery_percent ?? null,
      lastKnownLat: latest?.lat ?? null,
      lastKnownLng: latest?.lng ?? null
    };
  });
}

async function getDeviceByExternalId(externalDeviceId: string) {
  const rows = await supabaseRequest<DbDevice[]>(
    "devices",
    {
      query: buildSelectQuery(
        "id,device_name,external_device_id,owner_user_id,device_token_hash,is_active,last_seen_at",
        {
          external_device_id: `eq.${externalDeviceId}`,
          limit: "1"
        }
      )
    }
  );

  return rows[0] ?? null;
}

function hashDeviceToken(token: string) {
  return createHash("sha256").update(token, "utf8").digest("hex");
}

export async function authenticateDeviceUpload(
  externalDeviceId: string,
  rawDeviceToken: string
) {
  const device = await getDeviceByExternalId(externalDeviceId);

  if (!device) {
    return {
      ok: false as const,
      reason: "device_not_found" as const
    };
  }

  if (!device.is_active) {
    return {
      ok: false as const,
      reason: "device_inactive" as const
    };
  }

  if (!device.device_token_hash) {
    return {
      ok: false as const,
      reason: "device_token_missing" as const
    };
  }

  const hashedToken = hashDeviceToken(rawDeviceToken);
  const matchesToken =
    device.device_token_hash === hashedToken ||
    device.device_token_hash === rawDeviceToken;

  if (!matchesToken) {
    return {
      ok: false as const,
      reason: "invalid_device_token" as const
    };
  }

  return {
    ok: true as const,
    device
  };
}

export async function getLatestLocation(externalDeviceId: string) {
  const device = await getDeviceByExternalId(externalDeviceId);

  if (!device) {
    return null;
  }

  const [users, latestRows, shares] = await Promise.all([
    listUsers(),
    supabaseRequest<DbLatestLocation[]>(
      "device_latest_locations",
      {
        query: buildSelectQuery(
          "device_id,lat,lng,accuracy_meters,captured_at,received_at,battery_percent,is_charging",
          {
            device_id: `eq.${device.id}`,
            limit: "1"
          }
        )
      }
    ),
    listShares()
  ]);

  const latest = latestRows[0] ?? null;
  const owner = users.find((user) => user.id === device.owner_user_id);

  const response: LatestLocationResponse = {
    deviceId: device.external_device_id,
    deviceName: device.device_name,
    shareStatus: pickShareStatus(device.id, shares),
    lastReceivedAt: latest?.received_at ?? null,
    location: latest ? toLocationPoint(latest) : null
  };

  if (!owner && response.deviceName.trim().length === 0) {
    response.deviceName = device.external_device_id;
  }

  return response;
}

export async function getLocationHistory(externalDeviceId: string, limit = 50) {
  const device = await getDeviceByExternalId(externalDeviceId);

  if (!device) {
    return null;
  }

  const rows = await supabaseRequest<DbLocationRow[]>(
    "locations",
    {
      query: buildSelectQuery(
        "id,device_id,lat,lng,accuracy_meters,captured_at,received_at,battery_percent,is_charging,speed_mps",
        {
          device_id: `eq.${device.id}`,
          order: "captured_at.desc",
          limit: String(limit)
        }
      )
    }
  );

  return rows.map((row) => toLocationPoint(row));
}

export async function insertLocation(payload: LocationUploadPayload) {
  const device = await getDeviceByExternalId(payload.deviceId);

  if (!device) {
    return null;
  }

  const insertedRows = await supabaseRequest<DbLocationRow[]>(
    "locations",
    {
      body: [
        {
          device_id: device.id,
          lat: payload.lat,
          lng: payload.lng,
          accuracy_meters: payload.accuracyMeters,
          captured_at: payload.capturedAt,
          battery_percent: payload.batteryPercent ?? null,
          is_charging: payload.isCharging ?? null,
          speed_mps: payload.speedMps ?? null
        }
      ],
      method: "POST",
      prefer: "return=representation",
      query: buildSelectQuery(
        "id,device_id,lat,lng,accuracy_meters,captured_at,received_at,battery_percent,is_charging,speed_mps"
      )
    }
  );

  const inserted = insertedRows[0];

  await Promise.all([
    supabaseRequest(
      "device_latest_locations",
      {
        body: [
          {
            device_id: device.id,
            location_id: inserted.id,
            lat: inserted.lat,
            lng: inserted.lng,
            accuracy_meters: inserted.accuracy_meters,
            captured_at: inserted.captured_at,
            received_at: inserted.received_at,
            battery_percent: inserted.battery_percent,
            is_charging: inserted.is_charging
          }
        ],
        method: "POST",
        prefer: "resolution=merge-duplicates,return=minimal",
        query: new URLSearchParams({
          on_conflict: "device_id"
        })
      }
    ),
    supabaseRequest(
      "devices",
      {
        body: {
          last_seen_at: inserted.received_at
        },
        method: "PATCH",
        prefer: "return=minimal",
        query: new URLSearchParams({
          id: `eq.${device.id}`
        })
      }
    )
  ]);

  return getLatestLocation(payload.deviceId);
}

export async function checkDatabaseHealth() {
  const rows = await supabaseRequest<Array<{ id: string }>>(
    "devices",
    {
      query: buildSelectQuery("id", { limit: "1" })
    }
  );

  return {
    ok: true,
    checkedAt: new Date().toISOString(),
    sampleCount: rows.length
  };
}
