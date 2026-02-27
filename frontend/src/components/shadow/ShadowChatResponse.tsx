import { useState } from "react";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { ShadowResult } from "@/types/shadow";
import ShadowComparisonView from "@/components/shadow/ShadowComparisonView";
import { Layers, ChevronDown, ChevronRight } from "lucide-react";

/** Props for the ShadowChatResponse component. */
interface ShadowChatResponseProps {
  /** The shadow result to display. */
  result: ShadowResult;
  /** The original assistant message text for comparison. */
  originalResponse: string | null;
  /** The original span's latency in ms for comparison. */
  originalLatencyMs: number | null;
}

/**
 * Rendered after an assistant message bubble in ChatView when shadow results exist.
 * Shows a collapsed indigo-themed card with model badge, similarity score, and latency.
 * Expands to show truncated response preview. "Compare" opens a full comparison dialog.
 */
function ShadowChatResponse({
  result,
  originalResponse,
  originalLatencyMs,
}: ShadowChatResponseProps) {
  const [expanded, setExpanded] = useState(false);
  const [compareOpen, setCompareOpen] = useState(false);

  const score = result.semanticSimilarity ?? result.correctnessScore ?? null;
  const truncatedResponse =
    result.responseBody && result.responseBody.length > 200
      ? result.responseBody.slice(0, 200) + "..."
      : result.responseBody;

  return (
    <>
      {/* Shadow response card — indented under the assistant bubble */}
      <div className="flex gap-2 px-4 pl-13">
        <div
          className={cn(
            "max-w-[80%] rounded-lg border-l-2 border-indigo-500/40 bg-indigo-500/5 transition-colors",
          )}
        >
          {/* Collapsed header */}
          <button
            type="button"
            className="flex w-full items-center gap-2 px-3 py-1.5 text-left"
            onClick={() => setExpanded(!expanded)}
          >
            {expanded ? (
              <ChevronDown className="size-3 shrink-0 text-indigo-600 dark:text-indigo-400" />
            ) : (
              <ChevronRight className="size-3 shrink-0 text-indigo-600 dark:text-indigo-400" />
            )}
            <Layers className="size-3 shrink-0 text-indigo-600 dark:text-indigo-400" />
            <Badge
              variant="outline"
              className="border-indigo-500/40 bg-indigo-500/10 text-indigo-700 dark:text-indigo-400 text-[10px] py-0"
            >
              {result.modelId}
            </Badge>
            {score !== null && (
              <span
                className={cn(
                  "text-[11px] font-medium tabular-nums",
                  score >= 0.8
                    ? "text-green-700 dark:text-green-400"
                    : score >= 0.5
                      ? "text-yellow-700 dark:text-yellow-400"
                      : "text-red-700 dark:text-red-400",
                )}
              >
                {(score * 100).toFixed(0)}%
              </span>
            )}
            {result.latencyMs !== null && (
              <span className="text-[11px] text-muted-foreground tabular-nums">
                {result.latencyMs} ms
              </span>
            )}
          </button>

          {/* Expanded content */}
          {expanded && (
            <div className="border-t border-indigo-500/20 px-3 pb-2 pt-1.5 space-y-1.5">
              {truncatedResponse && (
                <pre className="text-xs font-mono whitespace-pre-wrap break-words text-muted-foreground">
                  {truncatedResponse}
                </pre>
              )}
              {result.errorMessage && (
                <p className="text-xs text-destructive">
                  {result.errorMessage}
                </p>
              )}
              <div className="flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-muted-foreground">
                {result.semanticSimilarity !== null && (
                  <span>
                    Similarity: {(result.semanticSimilarity * 100).toFixed(0)}%
                  </span>
                )}
                {result.costUsd !== null && (
                  <span>Cost: ${result.costUsd.toFixed(4)}</span>
                )}
                {result.inputTokens !== null &&
                  result.outputTokens !== null && (
                    <span>
                      Tokens: {result.inputTokens + result.outputTokens}
                    </span>
                  )}
              </div>
              <Button
                variant="outline"
                size="xs"
                className="text-indigo-700 dark:text-indigo-400 border-indigo-500/40"
                onClick={() => setCompareOpen(true)}
              >
                Compare
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* Comparison dialog */}
      <Dialog open={compareOpen} onOpenChange={setCompareOpen}>
        <DialogContent className="sm:max-w-4xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Shadow Comparison: {result.modelId}</DialogTitle>
          </DialogHeader>
          <ShadowComparisonView
            originalResponse={originalResponse}
            originalLatencyMs={originalLatencyMs}
            shadowResult={result}
          />
        </DialogContent>
      </Dialog>
    </>
  );
}

export default ShadowChatResponse;
