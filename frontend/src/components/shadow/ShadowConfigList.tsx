import { useState } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useShadowConfigs,
  useDeleteShadowConfig,
  useToggleShadowConfig,
} from "@/hooks/useShadowConfig";
import type { ShadowConfig } from "@/types/shadow";

/** Props for the ShadowConfigList component. */
interface ShadowConfigListProps {
  /** Callback invoked when the user wants to edit a config. */
  onEdit: (config: ShadowConfig) => void;
}

/** Number of skeleton rows to display while loading. */
const SKELETON_ROW_COUNT = 4;

/**
 * Renders a table of shadow configurations with status badges,
 * sampling rate display, and action buttons for edit/delete/toggle.
 */
function ShadowConfigList({ onEdit }: ShadowConfigListProps) {
  const { data: configs, isLoading } = useShadowConfigs();
  const deleteMutation = useDeleteShadowConfig();
  const toggleMutation = useToggleShadowConfig();
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);

  function handleDelete(id: number) {
    if (confirmDeleteId === id) {
      deleteMutation.mutate(id);
      setConfirmDeleteId(null);
    } else {
      setConfirmDeleteId(id);
    }
  }

  function handleToggle(config: ShadowConfig) {
    toggleMutation.mutate({ config });
  }

  return (
    <div className="border border-border rounded-md">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Service</TableHead>
            <TableHead>Provider</TableHead>
            <TableHead>Model</TableHead>
            <TableHead className="text-right">Sampling %</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading &&
            Array.from({ length: SKELETON_ROW_COUNT }).map((_, i) => (
              <TableRow key={i}>
                <TableCell>
                  <Skeleton className="h-4 w-24" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-4 w-20" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-4 w-32" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-4 w-12 ml-auto" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-5 w-16" />
                </TableCell>
                <TableCell>
                  <Skeleton className="h-8 w-24 ml-auto" />
                </TableCell>
              </TableRow>
            ))}

          {!isLoading && (!configs || configs.length === 0) && (
            <TableRow>
              <TableCell colSpan={6} className="h-32 text-center">
                <span className="text-muted-foreground text-sm">
                  No shadow configurations found. Add one to start shadow
                  testing.
                </span>
              </TableCell>
            </TableRow>
          )}

          {!isLoading &&
            configs?.map((config) => (
              <TableRow key={config.id}>
                <TableCell className="text-sm font-medium">
                  {config.serviceName}
                </TableCell>
                <TableCell className="text-sm">{config.providerName}</TableCell>
                <TableCell className="text-sm font-mono">
                  {config.modelId}
                </TableCell>
                <TableCell className="text-right tabular-nums text-sm">
                  {Math.round(config.samplingRate * 100)}%
                </TableCell>
                <TableCell>
                  <StatusBadge status={config.status} />
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex items-center justify-end gap-1">
                    <Button
                      variant="ghost"
                      size="xs"
                      onClick={() => handleToggle(config)}
                      disabled={toggleMutation.isPending}
                    >
                      {config.status === "ACTIVE" ? "Disable" : "Enable"}
                    </Button>
                    <Button
                      variant="ghost"
                      size="xs"
                      onClick={() => onEdit(config)}
                    >
                      Edit
                    </Button>
                    <Button
                      variant={
                        confirmDeleteId === config.id ? "destructive" : "ghost"
                      }
                      size="xs"
                      onClick={() => handleDelete(config.id)}
                      onBlur={() => setConfirmDeleteId(null)}
                      disabled={deleteMutation.isPending}
                    >
                      {confirmDeleteId === config.id ? "Confirm" : "Delete"}
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
        </TableBody>
      </Table>
    </div>
  );
}

/** Renders a colored badge for shadow config status. */
function StatusBadge({ status }: { status: string }) {
  const normalized = status.toUpperCase();

  if (normalized === "ACTIVE") {
    return (
      <Badge variant="secondary" className="text-green-700 dark:text-green-400">
        ACTIVE
      </Badge>
    );
  }

  return (
    <Badge variant="secondary" className="text-muted-foreground">
      {normalized}
    </Badge>
  );
}

export default ShadowConfigList;
