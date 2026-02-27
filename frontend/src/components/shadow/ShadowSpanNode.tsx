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

/** Props for the ShadowSpanNode component. */
interface ShadowSpanNodeProps {
  /** The shadow result to render. */
  result: ShadowResult;
  /** The original span's response text for comparison. */
  originalResponse: string | null;
  /** The original span's latency in ms for comparison. */
  originalLatencyMs: number | null;
}

/**
 * Returns a score-based color class for visual feedback.
 */
function scoreColorClass(score: number | null): string {
  if (score === null) return "border-border";
  if (score >= 0.8)
    return "border-green-500/40 bg-green-500/5 hover:bg-green-500/10";
  if (score >= 0.5)
    return "border-yellow-500/40 bg-yellow-500/5 hover:bg-yellow-500/10";
  return "border-red-500/40 bg-red-500/5 hover:bg-red-500/10";
}

/**
 * Returns the best available score from a shadow result.
 * Prefers semantic similarity, falls back to correctness score.
 */
function bestScore(result: ShadowResult): number | null {
  return result.semanticSimilarity ?? result.correctnessScore ?? null;
}

/**
 * Virtual span node rendered in a span tree to represent a shadow result.
 * Displays a SHADOW badge, model name, provider, and a score indicator.
 * Expandable to show response text, latency, token count, cost, and similarity.
 */
function ShadowSpanNode({
  result,
  originalResponse,
  originalLatencyMs,
}: ShadowSpanNodeProps) {
  const [expanded, setExpanded] = useState(false);
  const [compareOpen, setCompareOpen] = useState(false);

  const score = bestScore(result);
  const colorClass = scoreColorClass(score);
  const totalTokens = (result.inputTokens ?? 0) + (result.outputTokens ?? 0);

  const truncatedResponse =
    result.responseBody && result.responseBody.length > 200
      ? result.responseBody.slice(0, 200) + "..."
      : result.responseBody;

  return (
    <>
      <div
        className={cn("ml-6 rounded-lg border transition-colors", colorClass)}
      >
        {/* Header row */}
        <button
          type="button"
          className="flex w-full items-center gap-2 px-3 py-2 text-left"
          onClick={() => setExpanded(!expanded)}
        >
          {expanded ? (
            <ChevronDown className="size-3.5 shrink-0 text-indigo-600 dark:text-indigo-400" />
          ) : (
            <ChevronRight className="size-3.5 shrink-0 text-indigo-600 dark:text-indigo-400" />
          )}
          <Layers className="size-3.5 shrink-0 text-indigo-600 dark:text-indigo-400" />
          <Badge
            variant="outline"
            className="border-indigo-500/40 bg-indigo-500/10 text-indigo-700 dark:text-indigo-400 text-[10px]"
          >
            SHADOW
          </Badge>
          <span className="text-xs font-mono font-medium truncate">
            {result.modelId}
          </span>
          {score !== null && (
            <span
              className={cn(
                "ml-auto text-xs font-medium tabular-nums",
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
          <div className="border-t px-3 pb-3 pt-2 space-y-2">
            {/* Quick metrics */}
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs">
              {result.latencyMs !== null && (
                <div className="flex gap-1.5">
                  <span className="text-muted-foreground">Latency</span>
                  <span className="font-mono tabular-nums">
                    {result.latencyMs} ms
                  </span>
                </div>
              )}
              {totalTokens > 0 && (
                <div className="flex gap-1.5">
                  <span className="text-muted-foreground">Tokens</span>
                  <span className="font-mono tabular-nums">{totalTokens}</span>
                </div>
              )}
              {result.costUsd !== null && (
                <div className="flex gap-1.5">
                  <span className="text-muted-foreground">Cost</span>
                  <span className="font-mono tabular-nums">
                    ${result.costUsd.toFixed(4)}
                  </span>
                </div>
              )}
              {result.semanticSimilarity !== null && (
                <div className="flex gap-1.5">
                  <span className="text-muted-foreground">Similarity</span>
                  <span className="font-mono tabular-nums">
                    {(result.semanticSimilarity * 100).toFixed(0)}%
                  </span>
                </div>
              )}
              {result.correctnessScore !== null && (
                <div className="flex gap-1.5">
                  <span className="text-muted-foreground">Correctness</span>
                  <span className="font-mono tabular-nums">
                    {(result.correctnessScore * 100).toFixed(0)}%
                  </span>
                </div>
              )}
            </div>

            {/* Response preview */}
            {truncatedResponse && (
              <pre className="text-xs font-mono whitespace-pre-wrap break-words text-muted-foreground rounded-md bg-muted/50 p-2">
                {truncatedResponse}
              </pre>
            )}

            {/* Error message */}
            {result.errorMessage && (
              <p className="text-xs text-destructive">{result.errorMessage}</p>
            )}

            {/* Compare button */}
            <Button
              variant="outline"
              size="xs"
              onClick={() => setCompareOpen(true)}
            >
              Compare Full Response
            </Button>
          </div>
        )}
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

export default ShadowSpanNode;
