import { NextResponse } from "next/server";
import type { LocationUploadPayload } from "@location/api-types";
import {
  authenticateDeviceUpload,
  insertLocation
} from "../../../lib/location-repository";

export async function POST(request: Request) {
  const payload = (await request.json()) as Partial<LocationUploadPayload>;
  const deviceToken = request.headers.get("x-device-token");

  if (
    typeof payload.deviceId !== "string" ||
    typeof payload.lat !== "number" ||
    typeof payload.lng !== "number" ||
    typeof payload.accuracyMeters !== "number" ||
    typeof payload.capturedAt !== "string"
  ) {
    return NextResponse.json(
      { error: "Invalid location payload" },
      { status: 400 }
    );
  }

  if (!deviceToken || deviceToken.trim().length === 0) {
    return NextResponse.json(
      { error: "Missing device token header." },
      { status: 401 }
    );
  }

  const authResult = await authenticateDeviceUpload(
    payload.deviceId,
    deviceToken
  );

  if (!authResult.ok) {
    if (authResult.reason === "device_not_found") {
      return NextResponse.json(
        { error: "Device not found. Register the device before uploading locations." },
        { status: 404 }
      );
    }

    return NextResponse.json(
      { error: "Device upload is not authorized." },
      { status: 401 }
    );
  }

  const persisted = await insertLocation(payload as LocationUploadPayload);

  if (!persisted) {
    return NextResponse.json(
      { error: "Device not found. Register the device before uploading locations." },
      { status: 404 }
    );
  }

  return NextResponse.json({
    ok: true,
    receivedAt: new Date().toISOString(),
    location: persisted
  });
}
