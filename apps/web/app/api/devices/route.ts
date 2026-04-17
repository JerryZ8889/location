import { NextResponse } from "next/server";
import { listDevices } from "../../../lib/location-repository";

export async function GET() {
  return NextResponse.json({
    devices: await listDevices()
  });
}
