import { NextResponse } from "next/server";
import { checkDatabaseHealth } from "../../../lib/location-repository";

export async function GET() {
  const health = await checkDatabaseHealth();

  return NextResponse.json({
    service: "location-web",
    ...health
  });
}
