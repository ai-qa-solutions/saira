-- Allow shadow_config_id to be NULL for ad-hoc shadow tests (no associated config)
ALTER TABLE shadow_result ALTER COLUMN shadow_config_id DROP NOT NULL;
