import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import type { ShadowResult } from "@/types/shadow";
import type { PageResponse } from "@/types/trace";

/** Response type for paginated shadow traces. */
export interface ShadowTraceItem {
  traceId: string;
  serviceName: string;
  shadowCount: number;
  latestScore: number | null;
  latestModelId: string | null;
  latestExecutedAt: string | null;
}

/** Request payload for executing an ad-hoc shadow test. */
export interface ExecuteShadowRequest {
  spanId: string;
  providerName: string;
  modelId: string;
  modelParams?: Record<string, unknown>;
}

/**
 * Fetches shadow results for a specific span.
 * Only enabled when spanId is truthy.
 */
export function useShadowResultsForSpan(spanId: string | undefined) {
  return useQuery<ShadowResult[]>({
    queryKey: ["shadowResults", "span", spanId],
    queryFn: () =>
      api.get<ShadowResult[]>(`/api/v1/shadow/results/span/${spanId}`),
    enabled: !!spanId,
  });
}

/**
 * Fetches shadow results for an entire trace.
 * Only enabled when traceId is truthy.
 */
export function useShadowResultsForTrace(traceId: string | undefined) {
  return useQuery<ShadowResult[]>({
    queryKey: ["shadowResults", "trace", traceId],
    queryFn: () =>
      api.get<ShadowResult[]>(`/api/v1/shadow/results/trace/${traceId}`),
    enabled: !!traceId,
  });
}

/**
 * Fetches a paginated list of traces that have shadow results.
 */
export function useShadowTraces(page: number, size: number) {
  return useQuery<PageResponse<ShadowTraceItem>>({
    queryKey: ["shadowTraces", page, size],
    queryFn: () =>
      api.get<PageResponse<ShadowTraceItem>>(
        `/api/v1/shadow/traces?page=${page}&size=${size}`,
      ),
  });
}

/**
 * Mutation for executing an ad-hoc shadow test on a specific span.
 * Invalidates shadow results on success.
 */
export function useExecuteShadowTest() {
  const queryClient = useQueryClient();

  return useMutation<ShadowResult, unknown, ExecuteShadowRequest>({
    mutationFn: (request: ExecuteShadowRequest) =>
      api.post<ShadowResult>("/api/v1/shadow/execute", request),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ["shadowResults", "span", variables.spanId],
      });
      queryClient.invalidateQueries({
        queryKey: ["shadowResults", "trace"],
      });
      queryClient.invalidateQueries({
        queryKey: ["shadowTraces"],
      });
    },
  });
}
