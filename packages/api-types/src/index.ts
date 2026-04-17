export type ShareStatus = "active" | "paused" | "pending" | "revoked";

export interface LocationPoint {
  lat: number;
  lng: number;
  accuracyMeters: number;
  capturedAt: string;
  batteryPercent?: number | null;
  isCharging?: boolean | null;
  speedMps?: number | null;
}

export interface DeviceSummary {
  id: string;
  name: string;
  ownerDisplayName: string;
  shareStatus: ShareStatus;
  lastCapturedAt: string | null;
  batteryPercent: number | null;
  lastKnownLat: number | null;
  lastKnownLng: number | null;
}

export interface LatestLocationResponse {
  deviceId: string;
  deviceName: string;
  shareStatus: ShareStatus;
  lastReceivedAt: string | null;
  location: LocationPoint | null;
}

export interface LocationUploadPayload {
  deviceId: string;
  lat: number;
  lng: number;
  accuracyMeters: number;
  capturedAt: string;
  batteryPercent?: number | null;
  isCharging?: boolean | null;
  speedMps?: number | null;
}

