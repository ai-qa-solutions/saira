import { useState } from "react";
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
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { useShadowTraces } from "@/hooks/useShadowResults";
import { Layers, ArrowRight, ChevronLeft, ChevronRight } from "lucide-react";

/** Default page size for the shadow traces list. */
const DEFAULT_PAGE_SIZE = 20;

/** Number of skeleton rows to display while loading. */
const SKELETON_ROW_COUNT = 6;

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

/**
 * Returns a color class based on a score value.
 */
function scoreColorClass(score: number | null): string {
  if (score === null) return "text-muted-foreground";
  if (score >= 0.8) return "text-green-700 dark:text-green-400";
  if (score >= 0.5) return "text-yellow-700 dark:text-yellow-400";
  return "text-red-700 dark:text-red-400";
}

/**
 * Shadow Tests page.
 * Displays a list of traces that have shadow test results,
 * with summary information including shadow count and latest evaluation score.
 */
function ShadowTestsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);

  const { data, isLoading } = useShadowTraces(page, DEFAULT_PAGE_SIZE);

  return (
    <div className="flex flex-col h-full">
      <div className="mb-4 shrink-0">
        <div className="flex items-center gap-2">
          <Layers className="size-6 text-indigo-600 dark:text-indigo-400" />
          <h2 className="text-2xl font-bold tracking-tight">Shadow Tests</h2>
        </div>
        <p className="text-sm text-muted-foreground">
          Compare model versions without affecting production. View traces with
          shadow test results.
        </p>
      </div>

      <div className="flex-1 min-h-0 border border-border rounded-md">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Trace ID</TableHead>
              <TableHead>Service</TableHead>
              <TableHead className="text-right">Shadow Count</TableHead>
              <TableHead>Latest Model</TableHead>
              <TableHead className="text-right">Score</TableHead>
              <TableHead>Last Executed</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {/* Loading skeletons */}
            {isLoading &&
              Array.from({ length: SKELETON_ROW_COUNT }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell>
                    <Skeleton className="h-4 w-40" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-24" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-8 ml-auto" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-32" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-12 ml-auto" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-28" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-8 w-16 ml-auto" />
                  </TableCell>
                </TableRow>
              ))}

            {/* Empty state */}
            {!isLoading && (!data || data.content.length === 0) && (
              <TableRow>
                <TableCell colSpan={7} className="h-48 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <Layers className="size-10 text-muted-foreground/40" />
                    <span className="text-muted-foreground text-sm">
                      No shadow test results yet.
                    </span>
                    <span className="text-muted-foreground text-xs">
                      Configure shadow rules in Settings to start comparing
                      models.
                    </span>
                    <Button
                      variant="outline"
                      size="sm"
                      className="mt-2"
                      onClick={() => navigate("/settings")}
                    >
                      Go to Settings
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            )}

            {/* Data rows */}
            {!isLoading &&
              data?.content.map((item) => (
                <TableRow
                  key={item.traceId}
                  className="cursor-pointer"
                  onClick={() => navigate(`/traces/${item.traceId}`)}
                >
                  <TableCell className="font-mono text-xs max-w-[200px] truncate">
                    {item.traceId}
                  </TableCell>
                  <TableCell className="text-sm">{item.serviceName}</TableCell>
                  <TableCell className="text-right">
                    <Badge variant="secondary" className="tabular-nums">
                      {item.shadowCount}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-sm font-mono">
                    {item.latestModelId ?? (
                      <span className="text-muted-foreground">-</span>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <span
                      className={`text-sm font-medium tabular-nums ${scoreColorClass(item.latestScore)}`}
                    >
                      {item.latestScore !== null
                        ? `${(item.latestScore * 100).toFixed(0)}%`
                        : "-"}
                    </span>
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground tabular-nums">
                    {item.latestExecutedAt
                      ? formatDateTime(item.latestExecutedAt)
                      : "-"}
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="xs"
                      onClick={(e) => {
                        e.stopPropagation();
                        navigate(`/traces/${item.traceId}`);
                      }}
                    >
                      <ArrowRight className="size-3.5" />
                      View
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between py-3 shrink-0">
          <span className="text-xs text-muted-foreground">
            Page {data.number + 1} of {data.totalPages} ({data.totalElements}{" "}
            total)
          </span>
          <div className="flex gap-1">
            <Button
              variant="outline"
              size="xs"
              disabled={data.first}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <ChevronLeft className="size-3.5" />
              Previous
            </Button>
            <Button
              variant="outline"
              size="xs"
              disabled={data.last}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
              <ChevronRight className="size-3.5" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

export default ShadowTestsPage;
