import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useTraces } from "@/hooks/useTraces";
import TracesTable from "@/components/traces/TracesTable";
import TraceDetailPanel from "@/components/traces/TraceDetailPanel";
import { Button } from "@/components/ui/button";

/** Default page size for the traces list. */
const DEFAULT_PAGE_SIZE = 20;

/**
 * Traces page with Langfuse-style navigation.
 * /traces — full-width table.
 * /traces/:traceId — full-width trace detail view.
 */
function TracesPage() {
  const { traceId } = useParams<{ traceId: string }>();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);

  const { data, isLoading } = useTraces(page, DEFAULT_PAGE_SIZE);

  if (traceId !== undefined) {
    return (
      <div className="flex flex-col h-full">
        <div className="mb-4 shrink-0 flex items-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={() => navigate("/traces")}
          >
            Back to Traces
          </Button>
          <span className="text-sm text-muted-foreground font-mono">
            {traceId}
          </span>
        </div>

        <div className="flex-1 min-h-0 border border-border overflow-hidden">
          <TraceDetailPanel traceId={traceId} />
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      <div className="mb-4 shrink-0">
        <h2 className="text-2xl font-bold tracking-tight">Traces</h2>
        <p className="text-sm text-muted-foreground">Agent trace explorer</p>
      </div>

      <div className="flex-1 min-h-0 border border-border">
        <TracesTable
          data={data}
          isLoading={isLoading}
          page={page}
          onPageChange={setPage}
          activeTraceId={undefined}
        />
      </div>
    </div>
  );
}

export default TracesPage;
