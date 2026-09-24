-- Required for idempotent PostgREST inserts from the Android client.
-- Fail explicitly instead of deleting or rewriting existing rows.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.call_recordings
        GROUP BY storage_path
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate storage_path values exist; resolve them before migration.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.call_recordings
        WHERE storage_path IS NULL
    ) THEN
        RAISE EXCEPTION
            'NULL storage_path values exist; populate them before migration.';
    END IF;

    ALTER TABLE public.call_recordings
        ALTER COLUMN storage_path SET NOT NULL;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.call_recordings'::regclass
          AND conname = 'call_recordings_storage_path_key'
          AND contype = 'u'
    ) THEN
        ALTER TABLE public.call_recordings
            ADD CONSTRAINT call_recordings_storage_path_key
            UNIQUE (storage_path);
    END IF;
END
$$;
