import { Badge } from "@/components/ui/badge";

/** Props for the StatusBadge component. */
interface StatusBadgeProps {
  /** The status or outcome string to display (e.g. OK, ERROR, RECEIVED). */
  status: string | null;
}

/**
 * Renders a monochrome status badge based on span status or outcome.
 * OK/SUCCESS get secondary green, ERROR gets destructive, everything else is outline.
 */
/** Statuses that carry no useful information and should be hidden. */
const HIDDEN_STATUSES = new Set(["STATUS_CODE_UNSET", "UNSET", "UNKNOWN"]);

function StatusBadge({ status }: StatusBadgeProps) {
  const normalized = (status ?? "").toUpperCase();

  if (normalized === "" || HIDDEN_STATUSES.has(normalized)) {
    return null;
  }

  if (normalized === "OK" || normalized === "SUCCESS") {
    return (
      <Badge variant="secondary" className="text-green-700 dark:text-green-400">
        {normalized}
      </Badge>
    );
  }

  if (normalized === "ERROR") {
    return <Badge variant="destructive">{normalized}</Badge>;
  }

  return <Badge variant="outline">{normalized}</Badge>;
}

export default StatusBadge;
