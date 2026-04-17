export function buildTransitDirectionsUrl(lat: number, lng: number) {
  const destination = encodeURIComponent(`${lat},${lng}`);

  return `https://www.google.com/maps/dir/?api=1&destination=${destination}&travelmode=transit`;
}

