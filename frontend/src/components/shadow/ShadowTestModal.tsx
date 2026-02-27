import { useState, useRef } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useProviders, useModels } from "@/hooks/useModels";
import { useExecuteShadowTest } from "@/hooks/useShadowResults";
import ShadowComparisonView from "@/components/shadow/ShadowComparisonView";
import type { ShadowResult } from "@/types/shadow";
import { FlaskConical, Loader2 } from "lucide-react";

/** Props for the ShadowTestModal component. */
interface ShadowTestModalProps {
  /** Whether the dialog is open. */
  open: boolean;
  /** Callback to close the dialog. */
  onClose: () => void;
  /** The span ID to run the shadow test on. */
  spanId: string;
  /** The original response text for comparison display. */
  originalResponse: string | null;
  /** The original latency in ms for comparison display. */
  originalLatencyMs: number | null;
}

/**
 * Modal dialog for executing an ad-hoc shadow test on a specific span.
 * Allows selection of provider + model, temperature, and max tokens.
 * Shows loading state during execution, then displays the result
 * using ShadowComparisonView.
 */
function ShadowTestModal({
  open,
  onClose,
  spanId,
  originalResponse,
  originalLatencyMs,
}: ShadowTestModalProps) {
  const executeMutation = useExecuteShadowTest();
  const { data: providers, isLoading: providersLoading } = useProviders();

  const [providerName, setProviderName] = useState("");
  const [modelId, setModelId] = useState("");
  const [temperature, setTemperature] = useState(0.7);
  const [maxTokens, setMaxTokens] = useState(2048);
  const [result, setResult] = useState<ShadowResult | null>(null);

  /** Tracks the last provider so we can reset modelId on provider change. */
  const lastProviderRef = useRef(providerName);

  const { data: models, isLoading: modelsLoading } = useModels(
    providerName || undefined,
  );

  function handleProviderChange(newProvider: string) {
    setProviderName(newProvider);
    if (newProvider !== lastProviderRef.current) {
      setModelId("");
      lastProviderRef.current = newProvider;
    }
  }

  function handleExecute() {
    executeMutation.mutate(
      {
        spanId,
        providerName,
        modelId,
        modelParams: {
          temperature,
          maxTokens,
        },
      },
      {
        onSuccess: (data) => {
          setResult(data);
        },
      },
    );
  }

  function handleClose() {
    setResult(null);
    onClose();
  }

  const isValid = providerName !== "" && modelId !== "";

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && handleClose()}>
      <DialogContent
        className={
          result !== null
            ? "sm:max-w-4xl max-h-[85vh] overflow-y-auto"
            : "sm:max-w-lg"
        }
      >
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FlaskConical className="size-5" />
            Shadow Test
          </DialogTitle>
          <DialogDescription>
            {result !== null
              ? "Shadow test completed. Compare the results below."
              : "Execute a shadow test against an alternative model for this span."}
          </DialogDescription>
        </DialogHeader>

        {/* Result view */}
        {result !== null && (
          <ShadowComparisonView
            originalResponse={originalResponse}
            originalLatencyMs={originalLatencyMs}
            shadowResult={result}
          />
        )}

        {/* Loading state */}
        {result === null && executeMutation.isPending && (
          <div className="flex flex-col items-center gap-3 py-8">
            <Loader2 className="size-8 animate-spin text-muted-foreground" />
            <p className="text-sm text-muted-foreground">
              Executing shadow test...
            </p>
            <Skeleton className="h-4 w-48" />
          </div>
        )}

        {/* Error state */}
        {result === null && executeMutation.isError && (
          <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4">
            <p className="text-sm text-destructive">
              Shadow test failed:{" "}
              {executeMutation.error instanceof Error
                ? executeMutation.error.message
                : "Unknown error"}
            </p>
          </div>
        )}

        {/* Configuration form — shown only before execution */}
        {result === null && !executeMutation.isPending && (
          <div className="grid gap-4 py-2">
            {/* Provider */}
            <div className="grid gap-2">
              <Label>Provider</Label>
              {!providersLoading &&
              providers?.filter((p) => p.enabled).length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  No providers configured. Enable a provider in
                  application-local.yml and restart.
                </p>
              ) : (
                <Select
                  value={providerName}
                  onValueChange={handleProviderChange}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue
                      placeholder={
                        providersLoading
                          ? "Loading providers..."
                          : "Select provider"
                      }
                    />
                  </SelectTrigger>
                  <SelectContent>
                    {providers
                      ?.filter((p) => p.enabled)
                      .map((provider) => (
                        <SelectItem key={provider.name} value={provider.name}>
                          {provider.name}
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
              )}
            </div>

            {/* Model */}
            <div className="grid gap-2">
              <Label>Model</Label>
              <Select
                value={modelId}
                onValueChange={setModelId}
                disabled={!providerName}
              >
                <SelectTrigger className="w-full">
                  <SelectValue
                    placeholder={
                      !providerName
                        ? "Select a provider first"
                        : modelsLoading
                          ? "Loading models..."
                          : "Select model"
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {models?.map((model) => (
                    <SelectItem key={model.id} value={model.id}>
                      <span>{model.name}</span>
                      {model.contextLength && (
                        <span className="text-muted-foreground ml-2 text-xs">
                          ({Math.round(model.contextLength / 1000)}k ctx)
                        </span>
                      )}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Temperature */}
            <div className="grid gap-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="shadow-temperature">Temperature</Label>
                <span className="text-sm text-muted-foreground tabular-nums">
                  {temperature.toFixed(1)}
                </span>
              </div>
              <Input
                id="shadow-temperature"
                type="number"
                min={0}
                max={2}
                step={0.1}
                value={temperature}
                onChange={(e) =>
                  setTemperature(parseFloat(e.target.value) || 0)
                }
              />
            </div>

            {/* Max Tokens */}
            <div className="grid gap-2">
              <Label htmlFor="shadow-maxTokens">Max Tokens</Label>
              <Input
                id="shadow-maxTokens"
                type="number"
                min={1}
                max={128000}
                step={256}
                value={maxTokens}
                onChange={(e) =>
                  setMaxTokens(parseInt(e.target.value, 10) || 2048)
                }
              />
            </div>
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>
            {result !== null ? "Close" : "Cancel"}
          </Button>
          {result === null && (
            <Button
              onClick={handleExecute}
              disabled={!isValid || executeMutation.isPending}
            >
              {executeMutation.isPending ? "Running..." : "Execute Shadow Test"}
            </Button>
          )}
          {result !== null && (
            <Button
              variant="outline"
              onClick={() => {
                setResult(null);
                executeMutation.reset();
              }}
            >
              Run Another
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default ShadowTestModal;
