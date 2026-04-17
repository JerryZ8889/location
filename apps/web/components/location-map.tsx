"use client";

import type { LocationPoint } from "@location/api-types";
import type { StyleSpecification } from "maplibre-gl";
import maplibregl from "maplibre-gl";
import { useEffect, useRef } from "react";

interface LocationMapProps {
  point: LocationPoint | null;
}

const rasterMapStyle: StyleSpecification = {
  version: 8,
  sources: {
    osm: {
      type: "raster",
      tiles: [
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      ],
      tileSize: 256,
      attribution: "© OpenStreetMap contributors"
    }
  },
  layers: [
    {
      id: "osm",
      type: "raster",
      source: "osm"
    }
  ]
};

export function LocationMap({ point }: LocationMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markerRef = useRef<maplibregl.Marker | null>(null);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) {
      return;
    }

    const initialLng = point?.lng ?? 139.767125;
    const initialLat = point?.lat ?? 35.681236;

    mapRef.current = new maplibregl.Map({
      container: containerRef.current,
      style: process.env.NEXT_PUBLIC_MAP_STYLE_URL ??
        rasterMapStyle,
      center: [initialLng, initialLat],
      zoom: point ? 13 : 9
    });

    mapRef.current.addControl(new maplibregl.NavigationControl(), "top-right");

    return () => {
      markerRef.current?.remove();
      mapRef.current?.remove();
      markerRef.current = null;
      mapRef.current = null;
    };
  }, [point]);

  useEffect(() => {
    if (!mapRef.current) {
      return;
    }

    if (!point) {
      markerRef.current?.remove();
      markerRef.current = null;
      return;
    }

    if (!markerRef.current) {
      markerRef.current = new maplibregl.Marker({
        color: "#00695c"
      })
        .setLngLat([point.lng, point.lat])
        .addTo(mapRef.current);

      return;
    }

    markerRef.current.setLngLat([point.lng, point.lat]);
    mapRef.current.easeTo({
      center: [point.lng, point.lat],
      zoom: 14,
      duration: 1200
    });
  }, [point]);

  return <div ref={containerRef} className="map-frame" />;
}
