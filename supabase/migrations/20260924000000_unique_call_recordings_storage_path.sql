-- Required for idempotent PostgREST inserts from the Android client.
-- Check for existing duplicate non-null storage_path values before applying.
CREATE UNIQUE INDEX IF NOT EXISTS call_recordings_storage_path_uidx
    ON public.call_recordings (storage_path);