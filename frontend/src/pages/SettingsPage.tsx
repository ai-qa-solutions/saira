import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import ShadowConfigList from "@/components/shadow/ShadowConfigList";
import ShadowConfigForm from "@/components/shadow/ShadowConfigForm";
import type { ShadowConfig } from "@/types/shadow";

/**
 * Settings page with shadow configuration management.
 * Displays a list of shadow rules and provides create/edit functionality
 * through a modal dialog form.
 */
function SettingsPage() {
  const [formOpen, setFormOpen] = useState(false);
  const [editConfig, setEditConfig] = useState<ShadowConfig | undefined>(
    undefined,
  );

  function handleAdd() {
    setEditConfig(undefined);
    setFormOpen(true);
  }

  function handleEdit(config: ShadowConfig) {
    setEditConfig(config);
    setFormOpen(true);
  }

  function handleClose() {
    setFormOpen(false);
    setEditConfig(undefined);
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight">Settings</h2>
        <p className="text-sm text-muted-foreground">
          Configure shadow testing rules and integrations.
        </p>
      </div>

      <Separator />

      {/* Shadow Configuration Section */}
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-semibold">Shadow Configuration</h3>
            <p className="text-sm text-muted-foreground">
              Define rules to shadow-test LLM requests against alternative
              models.
            </p>
          </div>
          <Button onClick={handleAdd}>Add Shadow Rule</Button>
        </div>

        <ShadowConfigList onEdit={handleEdit} />
      </div>

      {/* Form dialog — keyed by editConfig id to remount and reset state */}
      <ShadowConfigForm
        key={editConfig?.id ?? "new"}
        open={formOpen}
        onClose={handleClose}
        editConfig={editConfig}
      />
    </div>
  );
}

export default SettingsPage;
