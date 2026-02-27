import { useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";
import type { ProviderInfo, ModelInfo } from "@/types/shadow";

/**
 * Fetches the list of connected AI providers.
 */
export function useProviders() {
  return useQuery<ProviderInfo[]>({
    queryKey: ["shadowProviders"],
    queryFn: () => api.get<ProviderInfo[]>("/api/v1/shadow/providers"),
  });
}

/**
 * Fetches available models for a specific provider.
 * Only enabled when providerName is truthy.
 */
export function useModels(providerName: string | undefined) {
  return useQuery<ModelInfo[]>({
    queryKey: ["shadowModels", providerName],
    queryFn: () =>
      api.get<ModelInfo[]>(`/api/v1/shadow/providers/${providerName}/models`),
    enabled: !!providerName,
  });
}
