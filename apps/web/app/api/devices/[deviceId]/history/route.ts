import { NextResponse } from "next/server";
import { getLocationHistory } from "../../../../../lib/location-repository";

interface RouteContext {
  params: Promise<{
    deviceId: string;
  }>;
}

export async function GET(_request: Request, context: RouteContext) {
  const { deviceId } = await context.params;
  const history = await getLocationHistory(deviceId);

  if (!history) {
    return NextResponse.json({ error: "Device not found" }, { status: 404 });
  }

  return NextResponse.json({
    deviceId,
    history
  });
}
