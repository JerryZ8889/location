"use client";

import { useCallback, useRef, useState } from "react";
import Link from "next/link";
import type { LatestLocationResponse } from "@location/api-types";
import { LocationMap } from "./location-map";
import { StatusPill } from "./status-pill";
import { buildTransitDirectionsUrl } from "../lib/google-maps";
import { formatDateTime } from "../lib/format";

interface DeviceLiveViewProps {
  deviceId: string;
  initialData: LatestLocationResponse;
}

async function fetchLatestLocation(deviceId: string) {
  const response = await fetch(`/api/devices/${encodeURIComponent(deviceId)}/latest`, {
    cache: "no-store"
  });

  if (!response.ok) {
    let detail = `HTTP ${response.status}`;
    const responseText = await response.text().catch(() => "");

    if (responseText.trim().length > 0) {
      try {
        const payload = JSON.parse(responseText) as { error?: string };
        if (payload.error) {
          detail = payload.error;
        } else {
          detail = responseText.trim();
        }
      } catch {
        detail = responseText.trim();
      }
    }

    throw new Error(detail);
  }

  return response.json() as Promise<LatestLocationResponse>;
}

export function DeviceLiveView({
  deviceId,
  initialData
}: DeviceLiveViewProps) {
  const [latest, setLatest] = useState(initialData);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const [viewerCheckedAt, setViewerCheckedAt] = useState(() =>
    new Date().toISOString()
  );
  const requestInFlightRef = useRef(false);

  const refreshLatest = useCallback(async (showSpinner = true) => {
    if (requestInFlightRef.current) {
      return;
    }

    requestInFlightRef.current = true;

    if (showSpinner) {
      setIsRefreshing(true);
    }

    try {
      const next = await fetchLatestLocation(deviceId);
      setLatest(next);
      setViewerCheckedAt(new Date().toISOString());
      setRefreshError(null);
    } catch (error) {
      const safeMessage =
        error instanceof Error ? error.message : "Unknown refresh failure";
      setRefreshError(`Refresh failed: ${safeMessage}`);
    } finally {
      requestInFlightRef.current = false;
      if (showSpinner) {
        setIsRefreshing(false);
      }
    }
  }, [deviceId]);

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
          <div className="meta-row detail-meta-row">
            <StatusPill status={latest.shareStatus} />
            <span>Android last received {formatDateTime(latest.lastReceivedAt)}</span>
            <span>Viewer checked {formatDateTime(viewerCheckedAt)}</span>
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
            <h3>Latest Android Update</h3>
            <p>{formatDateTime(latest.lastReceivedAt)}</p>
          </div>

          <div className="stat-block">
            <h3>Viewer Last Successful Check</h3>
            <p>{formatDateTime(viewerCheckedAt)}</p>
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

          <div className="stat-block">
            <h3>Refresh Status</h3>
            <p>
              {refreshError ?? "Click Refresh Now to query the latest server data."}
            </p>
          </div>

          <div className="button-row">
            <button
              type="button"
              className="button button-primary"
              disabled={isRefreshing}
              onClick={() => {
                void refreshLatest();
              }}
            >
              {isRefreshing ? "Refreshing..." : "Refresh Now"}
            </button>
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
