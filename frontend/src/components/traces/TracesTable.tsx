import { useNavigate } from "react-router-dom";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import type { TraceListItem, PageResponse } from "@/types/trace";
import StatusBadge from "@/components/traces/StatusBadge";

/** Props for the TracesTable component. */
interface TracesTableProps {
  /** Page response containing trace items. Undefined while loading. */
  data: PageResponse<TraceListItem> | undefined;
  /** Whether the data is currently loading. */
  isLoading: boolean;
  /** Current page number (0-indexed). */
  page: number;
  /** Callback to change the current page. */
  onPageChange: (page: number) => void;
  /** Currently selected trace ID for highlighting. */
  activeTraceId: string | undefined;
}

/**
 * Formats an ISO date string as "YYYY-MM-DD HH:mm:ss".
 */
function formatDateTime(iso: string): string {
  const date = new Date(iso);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  const seconds = String(date.getSeconds()).padStart(2, "0");
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

/** Number of skeleton rows to display while loading. */
const SKELETON_ROW_COUNT = 8;

/**
 * Table displaying a paginated list of traces.
 * Clicking a row navigates to the trace detail view.
 */
function TracesTable({
  data,
  isLoading,
  page,
  onPageChange,
  activeTraceId,
}: TracesTableProps) {
  const navigate = useNavigate();

  return (
    <div className="flex flex-col h-full">
      <div className="flex-1 min-h-0 overflow-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Trace ID</TableHead>
              <TableHead>Service</TableHead>
              <TableHead className="text-right">Spans</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Started</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading &&
              Array.from({ length: SKELETON_ROW_COUNT }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell>
                    <Skeleton className="h-4 w-24" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-20" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-8 ml-auto" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-5 w-16" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-32" />
                  </TableCell>
                </TableRow>
              ))}

            {!isLoading && data?.empty && (
              <TableRow>
                <TableCell colSpan={5} className="h-32 text-center">
                  <span className="text-muted-foreground text-sm">
                    No traces found.
                  </span>
                </TableCell>
              </TableRow>
            )}

            {!isLoading &&
              data?.content.map((trace) => {
                const isActive = trace.traceId === activeTraceId;

                return (
                  <TableRow
                    key={trace.traceId}
                    className={cn("cursor-pointer", isActive && "bg-muted")}
                    onClick={() => navigate(`/traces/${trace.traceId}`)}
                  >
                    <TableCell className="font-mono text-xs">
                      {trace.traceId}
                    </TableCell>
                    <TableCell className="text-sm">
                      {trace.serviceName}
                    </TableCell>
                    <TableCell className="text-right tabular-nums text-sm">
                      {trace.spanCount}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={trace.status} />
                    </TableCell>
                    <TableCell className="tabular-nums text-sm">
                      {formatDateTime(trace.startedAt)}
                    </TableCell>
                  </TableRow>
                );
              })}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {data && !data.empty && (
        <div className="flex items-center justify-between border-t border-border px-4 py-3 shrink-0">
          <Button
            variant="outline"
            size="sm"
            disabled={data.first}
            onClick={() => onPageChange(page - 1)}
          >
            Previous
          </Button>
          <span className="text-sm text-muted-foreground tabular-nums">
            Page {data.number + 1} of {data.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={data.last}
            onClick={() => onPageChange(page + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}

export default TracesTable;
