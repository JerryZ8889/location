import type {
  DeviceSummary,
  LatestLocationResponse,
  LocationPoint,
  LocationUploadPayload
} from "@location/api-types";

interface MockStore {
  devices: DeviceSummary[];
  latest: Record<string, LatestLocationResponse>;
  history: Record<string, LocationPoint[]>;
}

declare global {
  var __locationMockStore: MockStore | undefined;
}

function createInitialStore(): MockStore {
  const capturedAt = new Date().toISOString();

  const devices: DeviceSummary[] = [
    {
      id: "tokyo-demo",
      name: "Tokyo Demo Device",
      ownerDisplayName: "Sharer A",
      shareStatus: "active",
      lastCapturedAt: capturedAt,
      batteryPercent: 76,
      lastKnownLat: 35.681236,
      lastKnownLng: 139.767125
    },
    {
      id: "shibuya-demo",
      name: "Shibuya Demo Device",
      ownerDisplayName: "Sharer B",
      shareStatus: "paused",
      lastCapturedAt: capturedAt,
      batteryPercent: 54,
      lastKnownLat: 35.6595,
      lastKnownLng: 139.7005
    }
  ];

  const latest: Record<string, LatestLocationResponse> = {
    "tokyo-demo": {
      deviceId: "tokyo-demo",
      deviceName: "Tokyo Demo Device",
      shareStatus: "active",
      lastReceivedAt: capturedAt,
      location: {
        lat: 35.681236,
        lng: 139.767125,
        accuracyMeters: 18,
        capturedAt,
        batteryPercent: 76,
        isCharging: false,
        speedMps: 0
      }
    },
    "shibuya-demo": {
      deviceId: "shibuya-demo",
      deviceName: "Shibuya Demo Device",
      shareStatus: "paused",
      lastReceivedAt: capturedAt,
      location: {
        lat: 35.6595,
        lng: 139.7005,
        accuracyMeters: 32,
        capturedAt,
        batteryPercent: 54,
        isCharging: true,
        speedMps: 0
      }
    }
  };

  const history: Record<string, LocationPoint[]> = {
    "tokyo-demo": [latest["tokyo-demo"].location!],
    "shibuya-demo": [latest["shibuya-demo"].location!]
  };

  return {
    devices,
    latest,
    history
  };
}

function getStore() {
  if (!globalThis.__locationMockStore) {
    globalThis.__locationMockStore = createInitialStore();
  }

  return globalThis.__locationMockStore;
}

export function listMockDevices() {
  return getStore().devices;
}

export function getMockLatestLocation(deviceId: string) {
  return getStore().latest[deviceId] ?? null;
}

export function getMockLocationHistory(deviceId: string) {
  return getStore().history[deviceId] ?? [];
}

export function upsertMockLocation(payload: LocationUploadPayload) {
  const store = getStore();

  const nextPoint: LocationPoint = {
    lat: payload.lat,
    lng: payload.lng,
    accuracyMeters: payload.accuracyMeters,
    capturedAt: payload.capturedAt,
    batteryPercent: payload.batteryPercent ?? null,
    isCharging: payload.isCharging ?? null,
    speedMps: payload.speedMps ?? null
  };

  const existingDevice = store.devices.find((device) => device.id === payload.deviceId);

  if (existingDevice) {
    existingDevice.lastCapturedAt = payload.capturedAt;
    existingDevice.lastKnownLat = payload.lat;
    existingDevice.lastKnownLng = payload.lng;
    existingDevice.batteryPercent = payload.batteryPercent ?? null;
  } else {
    store.devices.unshift({
      id: payload.deviceId,
      name: payload.deviceId,
      ownerDisplayName: "Unknown sharer",
      shareStatus: "active",
      lastCapturedAt: payload.capturedAt,
      batteryPercent: payload.batteryPercent ?? null,
      lastKnownLat: payload.lat,
      lastKnownLng: payload.lng
    });
  }

  store.latest[payload.deviceId] = {
    deviceId: payload.deviceId,
    deviceName: existingDevice?.name ?? payload.deviceId,
    shareStatus: existingDevice?.shareStatus ?? "active",
    lastReceivedAt: new Date().toISOString(),
    location: nextPoint
  };

  if (!store.history[payload.deviceId]) {
    store.history[payload.deviceId] = [];
  }

  store.history[payload.deviceId].unshift(nextPoint);
  store.history[payload.deviceId] = store.history[payload.deviceId].slice(0, 50);

  return store.latest[payload.deviceId];
}

