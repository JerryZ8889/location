import { redirect } from "next/navigation";

export const dynamic = "force-dynamic";

export default async function HomePage() {
  redirect("/devices/demo-tokyo-android");
}
