import { useMemo } from "react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { SpanDetail } from "@/types/trace";
import StatusBadge from "@/components/traces/StatusBadge";
import DurationBar from "@/components/traces/DurationBar";

/** Props for the SpanTreeNode component. */
interface SpanTreeNodeProps {
  /** The span to render. */
  span: SpanDetail;
  /** Maximum duration among all spans, used for proportional duration bars. */
  maxDuration: number;
  /** Whether this span is currently selected. */
  isSelected?: boolean;
  /** Callback when the span row is clicked. */
  onSelect: (span: SpanDetail) => void;
}

/** Classification of an LLM HTTP call based on input/output analysis. */
export type SpanClassification =
  | "User -> Response"
  | "User -> Tool Call"
  | "Tool Result -> Response"
  | "Tool Result -> Tool Call"
  | null;

/**
 * Classifies an HTTP span by analyzing request messages and response content.
 */
/**
 * Parses SSE (Server-Sent Events) response body into an object
 * compatible with the non-streaming format for classification.
 * Returns null if the body is not valid SSE.
 */
function parseSSEResponse(body: string): Record<string, unknown> | null {
  const lines = body.split("\n");
  let hasToolCalls = false;
  let hasFunctionCall = false;
  let finishReason: string | null = null;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed.startsWith("data: ") || trimmed === "data: [DONE]") continue;
    try {
      const chunk = JSON.parse(trimmed.slice(6));
      const delta = chunk?.choices?.[0]?.delta;
      if (delta) {
        if ("tool_calls" in delta) hasToolCalls = true;
        if ("function_call" in delta) hasFunctionCall = true;
      }
      const fr = chunk?.choices?.[0]?.finish_reason;
      if (fr) finishReason = fr;
    } catch {
      continue;
    }
  }

  if (finishReason === null) return null;

  if (finishReason === "tool_calls" || finishReason === "function_call") {
    hasToolCalls = true;
  }

  const message: Record<string, unknown> = { role: "assistant" };
  if (hasToolCalls) message.tool_calls = [];
  if (hasFunctionCall) message.function_call = {};

  return { choices: [{ message }] };
}

export function classifySpan(
  requestBody: string | null,
  responseBody: string | null,
): SpanClassification {
  if (requestBody === null || responseBody === null) return null;

  try {
    const req = JSON.parse(requestBody);
    if (!req.messages || !Array.isArray(req.messages)) return null;

    // Try parsing response as plain JSON first, then as SSE chunks
    let resp: Record<string, unknown> | null = null;
    try {
      resp = JSON.parse(responseBody);
    } catch {
      resp = parseSSEResponse(responseBody);
    }
    if (resp === null) return null;

    const choices = resp.choices;
    if (!Array.isArray(choices)) return null;

    let lastRole: string | null = null;
    for (let i = req.messages.length - 1; i >= 0; i--) {
      const role = req.messages[i].role;
      if (role !== "system") {
        lastRole = role;
        break;
      }
    }
    if (lastRole === null) return null;

    const hasToolInput = lastRole === "tool" || lastRole === "function";
    const respMsg = (choices[0] as Record<string, unknown>)?.message ?? {};
    const hasToolCalls =
      "tool_calls" in (respMsg as Record<string, unknown>) ||
      "function_call" in (respMsg as Record<string, unknown>);

    if (!hasToolInput && !hasToolCalls) return "User -> Response";
    if (!hasToolInput && hasToolCalls) return "User -> Tool Call";
    if (hasToolInput && !hasToolCalls) return "Tool Result -> Response";
    return "Tool Result -> Tool Call";
  } catch {
    return null;
  }
}

/** Badge style mapping for each classification. */
export const CLASSIFICATION_STYLES: Record<
  string,
  { label: string; className: string }
> = {
  "User -> Response": {
    label: "User -> Response",
    className: "border-blue-500/40 text-blue-600 dark:text-blue-400",
  },
  "User -> Tool Call": {
    label: "User -> Tool Call",
    className: "border-orange-500/40 text-orange-600 dark:text-orange-400",
  },
  "Tool Result -> Response": {
    label: "Tool Result -> Response",
    className: "border-green-500/40 text-green-600 dark:text-green-400",
  },
  "Tool Result -> Tool Call": {
    label: "Tool Result -> Tool Call",
    className: "border-purple-500/40 text-purple-600 dark:text-purple-400",
  },
};

/**
 * Truncates a URL string to the given maxLength with an ellipsis.
 */
function truncateUrl(url: string, maxLength: number): string {
  if (url.length <= maxLength) return url;
  return url.slice(0, maxLength) + "...";
}

/**
 * Clickable span row in the trace list.
 * Shows classification badge, operation name, HTTP method, status, duration.
 * Clicking opens the detail overlay.
 */
function SpanTreeNode({
  span,
  maxDuration,
  isSelected,
  onSelect,
}: SpanTreeNodeProps) {
  const classification = useMemo(
    () => classifySpan(span.requestBody, span.responseBody),
    [span.requestBody, span.responseBody],
  );

  const classStyle = classification
    ? CLASSIFICATION_STYLES[classification]
    : null;

  return (
    <button
      type="button"
      onClick={() => onSelect(span)}
      className={cn(
        "w-full flex items-center gap-2 px-3 py-2 text-left text-sm border-b border-border hover:bg-muted/50 transition-colors",
        isSelected && "bg-muted/40 border-l-2 border-l-foreground",
      )}
    >
      {/* Classification column — fixed width */}
      <span className="w-[170px] shrink-0 text-left">
        {classStyle !== null && (
          <Badge
            variant="outline"
            className={cn(
              "text-[10px] font-mono px-1.5 py-0",
              classStyle.className,
            )}
          >
            {classStyle.label}
          </Badge>
        )}
      </span>

      {/* HTTP method badge or operation name */}
      {span.httpMethod !== null ? (
        <Badge variant="outline" className="text-xs font-mono shrink-0">
          {span.httpMethod}
        </Badge>
      ) : (
        <span className="font-mono text-xs truncate min-w-0 shrink">
          {span.operationName}
        </span>
      )}

      {/* URL truncated */}
      {span.httpUrl !== null && (
        <span className="text-xs text-muted-foreground truncate min-w-0">
          {truncateUrl(span.httpUrl, 60)}
        </span>
      )}

      {/* Spacer */}
      <span className="flex-1" />

      {/* HTTP status */}
      {span.httpStatus !== null && (
        <Badge variant="outline" className="text-xs font-mono shrink-0">
          {span.httpStatus}
        </Badge>
      )}

      {/* Status badge */}
      <StatusBadge status={span.statusCode ?? span.outcome} />

      {/* Duration bar */}
      <div className="w-[160px] shrink-0">
        <DurationBar
          durationMicros={span.durationMicros}
          maxDuration={maxDuration}
        />
      </div>
    </button>
  );
}

export default SpanTreeNode;
