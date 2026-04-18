import { notFound } from "next/navigation";
import { DeviceLiveView } from "../../../components/device-live-view";
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

  return <DeviceLiveView deviceId={deviceId} initialData={latest} />;
}
