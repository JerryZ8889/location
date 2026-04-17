import Link from "next/link";
import { formatLastSeen } from "../lib/format";
import { StatusPill } from "../components/status-pill";
import { listDevices } from "../lib/location-repository";

export const dynamic = "force-dynamic";

export default async function HomePage() {
  const devices = await listDevices();

  return (
    <main className="shell">
      <section className="hero">
        <h1>Shared Location Dashboard</h1>
        <p>
          This viewer now reads from the shared <code>location</code> schema in
          Supabase and follows the project shape in <code>DESIGN.md</code>:
          coordinate-first storage, clear share status, and a Google Maps
          transit handoff.
        </p>
      </section>

      <section className="grid dashboard-grid">
        {devices.length === 0 ? (
          <article className="card">
            <h2>No Devices Yet</h2>
            <p className="muted">
              Register a device and upload a location record to populate the
              dashboard.
            </p>
          </article>
        ) : null}
        {devices.map((device) => (
          <Link
            key={device.id}
            href={`/devices/${device.id}`}
            className="card device-card"
          >
            <div>
              <StatusPill status={device.shareStatus} />
            </div>
            <div>
              <h2>{device.name}</h2>
              <p className="muted">Shared by {device.ownerDisplayName}</p>
            </div>
            <div className="meta-row">
              <span>Last seen {formatLastSeen(device.lastCapturedAt)}</span>
              <span>Battery {device.batteryPercent ?? "--"}%</span>
            </div>
            <div className="meta-row">
              <span>
                {device.lastKnownLat?.toFixed(5) ?? "--"},{" "}
                {device.lastKnownLng?.toFixed(5) ?? "--"}
              </span>
            </div>
          </Link>
        ))}
      </section>
    </main>
  );
}
