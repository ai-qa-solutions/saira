import { useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";
import type { TraceListItem, TraceDetail, PageResponse } from "@/types/trace";

/**
 * Fetches a paginated list of traces.
 */
export function useTraces(page: number, size: number) {
  return useQuery<PageResponse<TraceListItem>>({
    queryKey: ["traces", page, size],
    queryFn: () =>
      api.get<PageResponse<TraceListItem>>(
        `/api/v1/traces?page=${page}&size=${size}`,
      ),
    refetchInterval: 5000,
  });
}

/**
 * Fetches detail for a single trace including all spans.
 * Only enabled when traceId is defined.
 */
export function useTraceDetail(traceId: string | undefined) {
  return useQuery<TraceDetail>({
    queryKey: ["trace", traceId],
    queryFn: () => api.get<TraceDetail>(`/api/v1/traces/${traceId}`),
    enabled: traceId !== undefined,
    refetchInterval: 5000,
  });
}
