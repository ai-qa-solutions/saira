import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import type { ShadowResult } from "@/types/shadow";
import { ArrowRight, Clock, Coins, Hash, BarChart3 } from "lucide-react";

/** Props for the ShadowComparisonView component. */
interface ShadowComparisonViewProps {
  /** The original span's response text. */
  originalResponse: string | null;
  /** The original span's latency in ms. */
  originalLatencyMs: number | null;
  /** The shadow result to compare against. */
  shadowResult: ShadowResult;
}

/** Renders a labeled metric value in a compact card. */
function MetricCard({
  icon,
  label,
  value,
  subtitle,
  className,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  subtitle?: string;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex items-center gap-2.5 rounded-lg border px-3 py-2",
        className,
      )}
    >
      <div className="flex size-8 items-center justify-center rounded-md bg-muted">
        {icon}
      </div>
      <div className="min-w-0">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className="text-sm font-medium tabular-nums">{value}</p>
        {subtitle && (
          <p className="text-[11px] text-muted-foreground">{subtitle}</p>
        )}
      </div>
    </div>
  );
}

/** Formats a number as a score with color coding. */
function ScoreBadge({ label, score }: { label: string; score: number | null }) {
  if (score === null) {
    return (
      <Badge variant="outline" className="text-muted-foreground">
        {label}: N/A
      </Badge>
    );
  }

  const color =
    score >= 0.8
      ? "text-green-700 dark:text-green-400 border-green-500/40 bg-green-500/5"
      : score >= 0.5
        ? "text-yellow-700 dark:text-yellow-400 border-yellow-500/40 bg-yellow-500/5"
        : "text-red-700 dark:text-red-400 border-red-500/40 bg-red-500/5";

  return (
    <Badge variant="outline" className={color}>
      {label}: {(score * 100).toFixed(0)}%
    </Badge>
  );
}

/**
 * Side-by-side comparison view of the original response vs a shadow response.
 * Displays metrics: latency, tokens, cost, semantic similarity, correctness.
 */
function ShadowComparisonView({
  originalResponse,
  originalLatencyMs,
  shadowResult,
}: ShadowComparisonViewProps) {
  const totalTokens =
    (shadowResult.inputTokens ?? 0) + (shadowResult.outputTokens ?? 0);

  return (
    <div className="flex flex-col gap-4">
      {/* Metrics row */}
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
        <MetricCard
          icon={<Clock className="size-4 text-muted-foreground" />}
          label="Latency"
          value={
            shadowResult.latencyMs !== null
              ? `${shadowResult.latencyMs} ms`
              : "N/A"
          }
          subtitle={
            originalLatencyMs !== null && shadowResult.latencyMs !== null
              ? `Original: ${originalLatencyMs} ms`
              : undefined
          }
        />
        <MetricCard
          icon={<Hash className="size-4 text-muted-foreground" />}
          label="Tokens"
          value={totalTokens > 0 ? String(totalTokens) : "N/A"}
          subtitle={
            shadowResult.inputTokens !== null &&
            shadowResult.outputTokens !== null
              ? `In: ${shadowResult.inputTokens} / Out: ${shadowResult.outputTokens}`
              : undefined
          }
        />
        <MetricCard
          icon={<Coins className="size-4 text-muted-foreground" />}
          label="Cost"
          value={
            shadowResult.costUsd !== null
              ? `$${shadowResult.costUsd.toFixed(4)}`
              : "N/A"
          }
        />
        <MetricCard
          icon={<BarChart3 className="size-4 text-muted-foreground" />}
          label="Similarity"
          value={
            shadowResult.semanticSimilarity !== null
              ? `${(shadowResult.semanticSimilarity * 100).toFixed(0)}%`
              : "N/A"
          }
          className={
            shadowResult.semanticSimilarity !== null
              ? shadowResult.semanticSimilarity >= 0.8
                ? "border-green-500/30"
                : shadowResult.semanticSimilarity >= 0.5
                  ? "border-yellow-500/30"
                  : "border-red-500/30"
              : undefined
          }
        />
        <MetricCard
          icon={<BarChart3 className="size-4 text-muted-foreground" />}
          label="Correctness"
          value={
            shadowResult.correctnessScore !== null
              ? `${(shadowResult.correctnessScore * 100).toFixed(0)}%`
              : "N/A"
          }
          className={
            shadowResult.correctnessScore !== null
              ? shadowResult.correctnessScore >= 0.8
                ? "border-green-500/30"
                : shadowResult.correctnessScore >= 0.5
                  ? "border-yellow-500/30"
                  : "border-red-500/30"
              : undefined
          }
        />
      </div>

      {/* Score badges */}
      <div className="flex flex-wrap gap-2">
        <ScoreBadge
          label="Semantic Similarity"
          score={shadowResult.semanticSimilarity}
        />
        <ScoreBadge label="Correctness" score={shadowResult.correctnessScore} />
        <Badge variant="outline" className="font-mono text-xs">
          {shadowResult.modelId}
        </Badge>
        <Badge
          variant={
            shadowResult.status === "COMPLETED" ? "secondary" : "destructive"
          }
          className="text-xs"
        >
          {shadowResult.status}
        </Badge>
      </div>

      {/* Side-by-side response panels */}
      <div className="grid min-h-0 grid-cols-1 gap-4 md:grid-cols-2">
        {/* Original response */}
        <div className="flex flex-col rounded-lg border">
          <div className="flex items-center gap-2 border-b px-3 py-2">
            <span className="text-xs font-medium text-muted-foreground">
              Original Response
            </span>
          </div>
          <div className="max-h-80 overflow-y-auto p-3">
            {originalResponse ? (
              <pre className="whitespace-pre-wrap break-words font-mono text-xs">
                {originalResponse}
              </pre>
            ) : (
              <p className="text-sm italic text-muted-foreground">
                No response available
              </p>
            )}
          </div>
        </div>

        {/* Shadow response */}
        <div className="flex flex-col rounded-lg border border-indigo-500/30 bg-indigo-500/5">
          <div className="flex items-center gap-2 border-b border-indigo-500/20 px-3 py-2">
            <ArrowRight className="size-3.5 text-indigo-600 dark:text-indigo-400" />
            <span className="text-xs font-medium text-indigo-700 dark:text-indigo-400">
              Shadow Response ({shadowResult.modelId})
            </span>
          </div>
          <div className="max-h-80 overflow-y-auto p-3">
            {shadowResult.responseBody ? (
              <pre className="whitespace-pre-wrap break-words font-mono text-xs">
                {shadowResult.responseBody}
              </pre>
            ) : shadowResult.errorMessage ? (
              <p className="text-sm text-destructive">
                {shadowResult.errorMessage}
              </p>
            ) : (
              <p className="text-sm italic text-muted-foreground">
                No response available
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default ShadowComparisonView;
