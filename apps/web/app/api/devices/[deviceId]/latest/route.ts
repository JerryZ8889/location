import { NextResponse } from "next/server";
import { getLatestLocation } from "../../../../../lib/location-repository";

interface RouteContext {
  params: Promise<{
    deviceId: string;
  }>;
}

export async function GET(_request: Request, context: RouteContext) {
  const { deviceId } = await context.params;
  const latest = await getLatestLocation(deviceId);

  if (!latest) {
    return NextResponse.json({ error: "Device not found" }, { status: 404 });
  }

  return NextResponse.json(latest);
}
