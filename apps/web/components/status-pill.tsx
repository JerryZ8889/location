import clsx from "clsx";
import type { ShareStatus } from "@location/api-types";

export function StatusPill({ status }: { status: ShareStatus }) {
  return (
    <span className={clsx("status-pill", status)}>
      {status.charAt(0).toUpperCase() + status.slice(1)}
    </span>
  );
}

