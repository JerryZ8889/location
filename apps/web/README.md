# Web Viewer App

This app is the browser-based location viewer.

Current state:

- Uses mock in-memory data
- Renders a device list and detail page
- Shows a MapLibre map centered on the latest coordinates
- Builds a Google Maps public transit link from the latest location

Next implementation steps:

1. Replace mock store calls in `lib/mock-store.ts` with real DB access.
2. Add authentication and authorization checks around the API routes.
3. Add polling or server push on the device detail page.
4. Add device history and share management screens.

