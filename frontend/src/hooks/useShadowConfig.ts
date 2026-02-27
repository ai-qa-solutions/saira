import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import type { ShadowConfig, ShadowConfigRequest } from "@/types/shadow";

/**
 * Fetches all shadow configurations.
 */
export function useShadowConfigs() {
  return useQuery<ShadowConfig[]>({
    queryKey: ["shadowConfigs"],
    queryFn: () => api.get<ShadowConfig[]>("/api/v1/shadow/configs"),
  });
}

/**
 * Fetches a single shadow configuration by ID.
 * Only enabled when id is defined.
 */
export function useShadowConfig(id: number | undefined) {
  return useQuery<ShadowConfig>({
    queryKey: ["shadowConfig", id],
    queryFn: () => api.get<ShadowConfig>(`/api/v1/shadow/configs/${id}`),
    enabled: id !== undefined,
  });
}

/**
 * Creates a new shadow configuration.
 * Invalidates the shadow configs list on success.
 */
export function useCreateShadowConfig() {
  const queryClient = useQueryClient();

  return useMutation<ShadowConfig, unknown, ShadowConfigRequest>({
    mutationFn: (request: ShadowConfigRequest) =>
      api.post<ShadowConfig>("/api/v1/shadow/configs", request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shadowConfigs"] });
    },
  });
}

/**
 * Updates an existing shadow configuration.
 * Invalidates both the list and the specific config on success.
 */
export function useUpdateShadowConfig() {
  const queryClient = useQueryClient();

  return useMutation<
    ShadowConfig,
    unknown,
    { id: number; request: ShadowConfigRequest }
  >({
    mutationFn: ({ id, request }) =>
      api.put<ShadowConfig>(`/api/v1/shadow/configs/${id}`, request),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ["shadowConfigs"] });
      queryClient.invalidateQueries({
        queryKey: ["shadowConfig", variables.id],
      });
    },
  });
}

/**
 * Deletes a shadow configuration.
 * Invalidates the shadow configs list on success.
 */
export function useDeleteShadowConfig() {
  const queryClient = useQueryClient();

  return useMutation<void, unknown, number>({
    mutationFn: (id: number) =>
      api.delete<void>(`/api/v1/shadow/configs/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shadowConfigs"] });
    },
  });
}

/**
 * Toggles a shadow configuration status between ACTIVE and DISABLED.
 * Uses the update endpoint with the current config data and toggled status.
 */
export function useToggleShadowConfig() {
  const queryClient = useQueryClient();

  return useMutation<ShadowConfig, unknown, { config: ShadowConfig }>({
    mutationFn: ({ config }) => {
      const newStatus = config.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
      const request: ShadowConfigRequest = {
        serviceName: config.serviceName,
        providerName: config.providerName,
        modelId: config.modelId,
        modelParams: config.modelParams ?? undefined,
        samplingRate: config.samplingRate,
      };
      return api.put<ShadowConfig>(
        `/api/v1/shadow/configs/${config.id}?status=${newStatus}`,
        request,
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shadowConfigs"] });
    },
  });
}
