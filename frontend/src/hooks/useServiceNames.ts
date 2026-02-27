import { useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";

/**
 * Fetches the list of distinct service names from ingested traces.
 */
export function useServiceNames() {
  return useQuery<string[]>({
    queryKey: ["serviceNames"],
    queryFn: () => api.get<string[]>("/api/v1/ingest/service-names"),
  });
}
