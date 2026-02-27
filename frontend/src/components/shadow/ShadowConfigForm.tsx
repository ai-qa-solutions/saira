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
import { Slider } from "@/components/ui/slider";
import { Combobox } from "@/components/ui/combobox";
import {
  useCreateShadowConfig,
  useUpdateShadowConfig,
} from "@/hooks/useShadowConfig";
import { useProviders, useModels } from "@/hooks/useModels";
import { useServiceNames } from "@/hooks/useServiceNames";
import type { ShadowConfig, ShadowConfigRequest } from "@/types/shadow";

/** Props for the ShadowConfigForm component. */
interface ShadowConfigFormProps {
  /** Whether the dialog is open. */
  open: boolean;
  /** Callback to close the dialog. */
  onClose: () => void;
  /** Existing config to edit. Undefined when creating a new config. */
  editConfig?: ShadowConfig;
}

/** Extracts initial temperature from model params, defaulting to 0.7. */
function getInitialTemperature(config: ShadowConfig | undefined): number {
  if (
    config?.modelParams &&
    typeof config.modelParams.temperature === "number"
  ) {
    return config.modelParams.temperature;
  }
  return 0.7;
}

/** Extracts initial max tokens from model params, defaulting to 2048. */
function getInitialMaxTokens(config: ShadowConfig | undefined): number {
  if (config?.modelParams && typeof config.modelParams.maxTokens === "number") {
    return config.modelParams.maxTokens;
  }
  return 2048;
}

/**
 * Modal dialog form for creating or editing a shadow configuration.
 * Fields: service name, provider, model, temperature, max tokens, sampling rate.
 * Provider and model dropdowns are populated from the API.
 *
 * State is initialized from editConfig props. The parent must use a React key
 * (e.g. `key={editConfig?.id ?? "new"}`) to remount when switching between
 * create / edit modes so that initial state is recomputed.
 */
function ShadowConfigForm({
  open,
  onClose,
  editConfig,
}: ShadowConfigFormProps) {
  const createMutation = useCreateShadowConfig();
  const updateMutation = useUpdateShadowConfig();
  const { data: providers, isLoading: providersLoading } = useProviders();
  const { data: serviceNames } = useServiceNames();
  const isEditing = editConfig !== undefined;

  const [serviceName, setServiceName] = useState(editConfig?.serviceName ?? "");
  const [providerName, setProviderName] = useState(
    editConfig?.providerName ?? "",
  );
  const [modelId, setModelId] = useState(editConfig?.modelId ?? "");
  const [temperature, setTemperature] = useState(
    getInitialTemperature(editConfig),
  );
  const [maxTokens, setMaxTokens] = useState(getInitialMaxTokens(editConfig));
  const [samplingRate, setSamplingRate] = useState(
    editConfig ? Math.round(editConfig.samplingRate) : 10,
  );

  /** Tracks the last provider so we can reset modelId on provider change. */
  const lastProviderRef = useRef(providerName);

  const { data: models, isLoading: modelsLoading } = useModels(
    providerName || undefined,
  );

  /**
   * Handles provider selection. Resets modelId when the provider actually
   * changes to avoid stale model references.
   */
  function handleProviderChange(newProvider: string) {
    setProviderName(newProvider);
    if (newProvider !== lastProviderRef.current) {
      setModelId("");
      lastProviderRef.current = newProvider;
    }
  }

  function handleSubmit() {
    const request: ShadowConfigRequest = {
      serviceName: serviceName.trim(),
      providerName,
      modelId,
      modelParams: {
        temperature,
        maxTokens,
      },
      samplingRate: samplingRate,
    };

    if (isEditing) {
      updateMutation.mutate(
        { id: editConfig.id, request },
        { onSuccess: () => onClose() },
      );
    } else {
      createMutation.mutate(request, { onSuccess: () => onClose() });
    }
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending;
  const isValid =
    serviceName.trim() !== "" && providerName !== "" && modelId !== "";

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {isEditing ? "Edit Shadow Rule" : "Add Shadow Rule"}
          </DialogTitle>
          <DialogDescription>
            Configure a shadow rule to duplicate LLM requests to an alternative
            model for comparison.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-2">
          {/* Service Name */}
          <div className="grid gap-2">
            <Label>Service Name</Label>
            <Combobox
              options={serviceNames ?? []}
              value={serviceName}
              onChange={setServiceName}
              placeholder="Select or type service name..."
              searchPlaceholder="Search services..."
              emptyText="No services found."
              allowCreate
            />
          </div>

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
              <Select value={providerName} onValueChange={handleProviderChange}>
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
              <Label htmlFor="temperature">Temperature</Label>
              <span className="text-sm text-muted-foreground tabular-nums">
                {temperature.toFixed(1)}
              </span>
            </div>
            <Input
              id="temperature"
              type="number"
              min={0}
              max={2}
              step={0.1}
              value={temperature}
              onChange={(e) => setTemperature(parseFloat(e.target.value) || 0)}
            />
          </div>

          {/* Max Tokens */}
          <div className="grid gap-2">
            <Label htmlFor="maxTokens">Max Tokens</Label>
            <Input
              id="maxTokens"
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

          {/* Sampling Rate */}
          <div className="grid gap-2">
            <div className="flex items-center justify-between">
              <Label>Sampling Rate</Label>
              <span className="text-sm font-medium tabular-nums">
                {samplingRate}%
              </span>
            </div>
            <Slider
              value={[samplingRate]}
              onValueChange={(values) => setSamplingRate(values[0])}
              min={0}
              max={100}
              step={1}
            />
            <p className="text-xs text-muted-foreground">
              Percentage of matching LLM requests to shadow-test.
            </p>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={!isValid || isSubmitting}>
            {isSubmitting
              ? "Saving..."
              : isEditing
                ? "Update Rule"
                : "Create Rule"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default ShadowConfigForm;
