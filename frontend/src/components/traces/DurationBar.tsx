import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";

/** Props for the DurationBar component. */
interface DurationBarProps {
  /** Duration of this span in microseconds. */
  durationMicros: number;
  /** Maximum duration among sibling spans in microseconds, used for proportional width. */
  maxDuration: number;
}

/**
 * Horizontal bar proportional to duration relative to maxDuration.
 * Tooltip shows the exact duration in milliseconds.
 */
function DurationBar({ durationMicros, maxDuration }: DurationBarProps) {
  const ms = durationMicros / 1000;
  const percentage = maxDuration > 0 ? (durationMicros / maxDuration) * 100 : 0;

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div className="flex items-center gap-2 min-w-[100px]">
          <div className="flex-1 h-2 bg-muted rounded-sm overflow-hidden">
            <div
              className="h-full bg-foreground/30 rounded-sm"
              style={{ width: `${Math.max(percentage, 2)}%` }}
            />
          </div>
          <span className="text-xs text-muted-foreground tabular-nums w-16 text-right">
            {ms.toFixed(1)} ms
          </span>
        </div>
      </TooltipTrigger>
      <TooltipContent>{ms.toFixed(3)} ms</TooltipContent>
    </Tooltip>
  );
}

export default DurationBar;
