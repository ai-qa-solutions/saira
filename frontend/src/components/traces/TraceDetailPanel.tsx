import { useState, useMemo, useCallback } from "react";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { TooltipProvider } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { useTraceDetail } from "@/hooks/useTraces";
import { useShadowResultsForTrace } from "@/hooks/useShadowResults";
import type { SpanDetail } from "@/types/trace";
import type { ShadowResult } from "@/types/shadow";
import StatusBadge from "@/components/traces/StatusBadge";
import SpanTreeNode, {
  classifySpan,
  CLASSIFICATION_STYLES,
} from "@/components/traces/SpanTreeNode";
import ChatView from "@/components/traces/ChatView";

/** Props for the TraceDetailPanel component. */
interface TraceDetailPanelProps {
  /** The trace ID to load and display. */
  traceId: string;
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

/**
 * Finds the maximum duration across all spans for proportional bar rendering.
 */
function findMaxDuration(spans: SpanDetail[]): number {
  if (spans.length === 0) return 0;
  return Math.max(...spans.map((s) => s.durationMicros));
}

/**
 * Pretty-prints a JSON string. Returns original if parsing fails.
 */
function formatJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

/**
 * Full-page detail panel showing trace metadata, span list, and span detail overlay.
 */
function TraceDetailPanel({ traceId }: TraceDetailPanelProps) {
  const { data: trace, isLoading, isError, error } = useTraceDetail(traceId);
  const { data: shadowResults } = useShadowResultsForTrace(traceId);
  const [selectedSpan, setSelectedSpan] = useState<SpanDetail | null>(null);

  const shadowResultsMap = useMemo(() => {
    if (!shadowResults || shadowResults.length === 0) return undefined;
    const map = new Map<string, ShadowResult[]>();
    for (const result of shadowResults) {
      const existing = map.get(result.sourceSpanId);
      if (existing) {
        existing.push(result);
      } else {
        map.set(result.sourceSpanId, [result]);
      }
    }
    return map;
  }, [shadowResults]);

  const handleSelect = useCallback((span: SpanDetail) => {
    setSelectedSpan((prev) => (prev?.spanId === span.spanId ? null : span));
  }, []);

  const handleClose = useCallback(() => {
    setSelectedSpan(null);
  }, []);

  if (isLoading) {
    return (
      <div className="p-6 space-y-4">
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-4 w-32" />
        <Separator />
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      </div>
    );
  }

  if (isError) {
    const message =
      error instanceof Error ? error.message : "Failed to load trace details.";
    return (
      <div className="p-6">
        <p className="text-sm text-destructive">{message}</p>
      </div>
    );
  }

  if (!trace) {
    return (
      <div className="p-6">
        <p className="text-sm text-muted-foreground">
          No trace data available.
        </p>
      </div>
    );
  }

  const maxDuration = findMaxDuration(trace.spans);

  return (
    <div className="relative flex flex-col h-full">
      {/* Header */}
      <div className="p-4 space-y-2 shrink-0">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-semibold">Trace Detail</h3>
          <StatusBadge status={trace.status} />
        </div>
        <div className="flex flex-wrap gap-x-6 gap-y-1 text-xs">
          <div className="flex gap-2">
            <span className="text-muted-foreground">Service</span>
            <span>{trace.serviceName}</span>
          </div>
          <div className="flex gap-2">
            <span className="text-muted-foreground">Started</span>
            <span className="tabular-nums">
              {formatDateTime(trace.startedAt)}
            </span>
          </div>
          {trace.endedAt !== null && (
            <div className="flex gap-2">
              <span className="text-muted-foreground">Ended</span>
              <span className="tabular-nums">
                {formatDateTime(trace.endedAt)}
              </span>
            </div>
          )}
          <div className="flex gap-2">
            <span className="text-muted-foreground">Spans</span>
            <span className="tabular-nums">{trace.spanCount}</span>
          </div>
        </div>
      </div>

      <Separator />

      {/* Tabs: Spans / Chat */}
      <Tabs defaultValue="spans" className="flex-1 flex flex-col min-h-0">
        <TabsList className="mx-4 mt-2">
          <TabsTrigger value="spans">Spans</TabsTrigger>
          <TabsTrigger value="chat">Chat</TabsTrigger>
        </TabsList>

        <TabsContent value="spans" className="flex-1 overflow-y-auto min-h-0">
          <TooltipProvider>
            <div className="min-w-0">
              {trace.spans.length === 0 ? (
                <div className="p-4 text-center text-sm text-muted-foreground">
                  No spans found.
                </div>
              ) : (
                trace.spans.map((span) => (
                  <SpanTreeNode
                    key={span.spanId}
                    span={span}
                    maxDuration={maxDuration}
                    isSelected={selectedSpan?.spanId === span.spanId}
                    onSelect={handleSelect}
                  />
                ))
              )}
            </div>
          </TooltipProvider>
        </TabsContent>

        <TabsContent value="chat" className="flex-1 overflow-y-auto min-h-0">
          <ChatView spans={trace.spans} shadowResults={shadowResultsMap} />
        </TabsContent>
      </Tabs>

      {/* Span detail overlay */}
      {selectedSpan !== null && (
        <SpanDetailOverlay span={selectedSpan} onClose={handleClose} />
      )}
    </div>
  );
}

/** Extracted model call parameters from the request body. */
interface ModelParams {
  model: string | null;
  temperature: number | null;
  maxTokens: number | null;
  stream: boolean | null;
  functionCall: string | null;
  functions: string[];
}

/**
 * Extracts model call parameters from the raw request body JSON.
 */
function extractModelParams(requestBody: string | null): ModelParams | null {
  if (requestBody === null) return null;
  try {
    const req = JSON.parse(requestBody);
    if (typeof req !== "object" || req === null) return null;

    const functions: string[] = [];
    if (Array.isArray(req.functions)) {
      for (const fn of req.functions) {
        if (fn.name) functions.push(fn.name);
      }
    }
    if (Array.isArray(req.tools)) {
      for (const tool of req.tools) {
        if (tool.function?.name) functions.push(tool.function.name);
      }
    }

    return {
      model: req.model ?? null,
      temperature: req.temperature ?? null,
      maxTokens: req.max_tokens ?? null,
      stream: typeof req.stream === "boolean" ? req.stream : null,
      functionCall: req.function_call ?? req.tool_choice ?? null,
      functions,
    };
  } catch {
    return null;
  }
}

/** Props for the SpanDetailOverlay. */
interface SpanDetailOverlayProps {
  span: SpanDetail;
  onClose: () => void;
}

/**
 * Overlay panel that appears on top of the span list.
 * Shows model params, classification, and two-column Request | Response.
 */
function SpanDetailOverlay({ span, onClose }: SpanDetailOverlayProps) {
  const classification = useMemo(
    () => classifySpan(span.requestBody, span.responseBody),
    [span.requestBody, span.responseBody],
  );

  const modelParams = useMemo(
    () => extractModelParams(span.requestBody),
    [span.requestBody],
  );

  const classStyle = classification
    ? CLASSIFICATION_STYLES[classification]
    : null;

  return (
    <div className="absolute inset-0 top-0 bg-background flex flex-col border-t border-border z-10">
      {/* Overlay header */}
      <div className="flex items-center gap-3 px-4 py-3 shrink-0 border-b border-border">
        <Button variant="outline" size="sm" onClick={onClose}>
          Close
        </Button>

        {classStyle !== null && (
          <Badge
            variant="outline"
            className={cn("text-xs font-mono", classStyle.className)}
          >
            {classStyle.label}
          </Badge>
        )}

        {span.httpStatus !== null && (
          <Badge variant="outline" className="text-xs font-mono">
            {span.httpStatus}
          </Badge>
        )}

        <StatusBadge status={span.statusCode ?? span.outcome} />

        <span className="text-xs text-muted-foreground tabular-nums">
          {(span.durationMicros / 1000).toFixed(0)} ms
        </span>
      </div>

      {/* Model params row */}
      {modelParams !== null && (
        <div className="px-4 py-2 text-xs border-b border-border shrink-0 flex flex-wrap items-center gap-x-4 gap-y-1">
          {modelParams.model !== null && (
            <div className="flex items-center gap-1.5">
              <span className="text-muted-foreground">Model</span>
              <span className="font-mono font-medium">{modelParams.model}</span>
            </div>
          )}
          {modelParams.temperature !== null && (
            <div className="flex items-center gap-1.5">
              <span className="text-muted-foreground">Temperature</span>
              <span className="font-mono">{modelParams.temperature}</span>
            </div>
          )}
          {modelParams.maxTokens !== null && (
            <div className="flex items-center gap-1.5">
              <span className="text-muted-foreground">Max Tokens</span>
              <span className="font-mono">{modelParams.maxTokens}</span>
            </div>
          )}
          {modelParams.stream !== null && (
            <div className="flex items-center gap-1.5">
              <span className="text-muted-foreground">Stream</span>
              <span className="font-mono">
                {modelParams.stream ? "true" : "false"}
              </span>
            </div>
          )}
          {modelParams.functionCall !== null && (
            <div className="flex items-center gap-1.5">
              <span className="text-muted-foreground">Tool Choice</span>
              <span className="font-mono">
                {typeof modelParams.functionCall === "string"
                  ? modelParams.functionCall
                  : JSON.stringify(modelParams.functionCall)}
              </span>
            </div>
          )}
          {modelParams.functions.length > 0 && (
            <div className="flex items-center gap-1.5">
              <span className="text-muted-foreground">Tools</span>
              <span className="font-mono">
                {modelParams.functions.join(", ")}
              </span>
            </div>
          )}
        </div>
      )}

      {/* URL row (compact) */}
      {span.httpUrl !== null && (
        <div className="px-4 py-1.5 text-[11px] border-b border-border flex items-center gap-2 shrink-0 text-muted-foreground">
          <span className="font-mono truncate">{span.httpUrl}</span>
        </div>
      )}

      {/* Two-column: Request | Response — each column scrolls independently */}
      <div className="flex-1 min-h-0 grid grid-cols-2 gap-0 overflow-hidden">
        {/* Request column */}
        <div className="flex flex-col min-w-0 min-h-0 border-r border-border">
          <div className="px-4 py-2 border-b border-border shrink-0">
            <span className="text-xs font-medium text-muted-foreground">
              Request
            </span>
          </div>
          <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden p-4">
            {span.requestBody !== null ? (
              <pre className="font-mono text-xs whitespace-pre-wrap break-words">
                {formatJson(span.requestBody)}
              </pre>
            ) : (
              <p className="text-sm text-muted-foreground italic">No body</p>
            )}
          </div>
        </div>

        {/* Response column */}
        <div className="flex flex-col min-w-0 min-h-0">
          <div className="px-4 py-2 border-b border-border shrink-0">
            <span className="text-xs font-medium text-muted-foreground">
              Response
            </span>
          </div>
          <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden p-4">
            {span.responseBody !== null ? (
              <pre className="font-mono text-xs whitespace-pre-wrap break-words">
                {formatJson(span.responseBody)}
              </pre>
            ) : (
              <p className="text-sm text-muted-foreground italic">No body</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default TraceDetailPanel;
