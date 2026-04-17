import Link from "next/link";
import { notFound } from "next/navigation";
import { LocationMap } from "../../../components/location-map";
import { StatusPill } from "../../../components/status-pill";
import { buildTransitDirectionsUrl } from "../../../lib/google-maps";
import { formatDateTime } from "../../../lib/format";
import { getLatestLocation } from "../../../lib/location-repository";

export const dynamic = "force-dynamic";

interface DevicePageProps {
  params: Promise<{
    deviceId: string;
  }>;
}

export default async function DevicePage({ params }: DevicePageProps) {
  const { deviceId } = await params;
  const latest = await getLatestLocation(deviceId);

  if (!latest) {
    notFound();
  }

  const location = latest.location;
  const transitUrl = location
    ? buildTransitDirectionsUrl(location.lat, location.lng)
    : "#";

  return (
    <main className="shell">
      <section className="hero">
        <Link href="/" className="muted">
          Back to dashboard
        </Link>
        <h1>{latest.deviceName}</h1>
        <p>
          Current map view, latest capture details, and a direct handoff to
          Google Maps transit directions.
        </p>
      </section>

      <section className="grid detail-grid">
        <div className="card map-card">
          <div className="meta-row" style={{ marginBottom: "1rem" }}>
            <StatusPill status={latest.shareStatus} />
            <span>Last received {formatDateTime(latest.lastReceivedAt)}</span>
          </div>
          <LocationMap point={location} />
        </div>

        <aside className="card sidebar-card">
          <div className="stat-block">
            <h3>Latest Coordinates</h3>
            <p>
              {location
                ? `${location.lat.toFixed(5)}, ${location.lng.toFixed(5)}`
                : "No location yet"}
            </p>
          </div>

          <div className="stat-block">
            <h3>Captured At</h3>
            <p>{formatDateTime(location?.capturedAt ?? null)}</p>
          </div>

          <div className="stat-block">
            <h3>Accuracy</h3>
            <p>{location ? `${location.accuracyMeters} m` : "--"}</p>
          </div>

          <div className="stat-block">
            <h3>Battery</h3>
            <p>
              {location?.batteryPercent != null
                ? `${location.batteryPercent}%`
                : "--"}
            </p>
          </div>

          <div className="button-row">
            <Link href={transitUrl} className="button button-primary">
              Open Transit In Google Maps
            </Link>
            <Link href="/api/health" className="button button-secondary">
              API Health
            </Link>
          </div>
        </aside>
      </section>
    </main>
  );
}
