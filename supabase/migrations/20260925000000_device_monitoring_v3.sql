-- Maipu Call Sync V3 monitoring. This migration deliberately does not alter
-- call_recordings, Storage, or any existing synchronization policy.

CREATE TABLE public.devices (
    device_id uuid PRIMARY KEY,
    seller_name text NOT NULL,
    corporate_phone text NOT NULL,
    device_model text,
    android_version text,
    app_version text,
    active boolean NOT NULL DEFAULT true,
    last_heartbeat timestamptz,
    last_successful_sync timestamptz,
    last_scan_received_at timestamptz,
    calls_detected bigint,
    calls_synced bigint,
    calls_pending bigint,
    calls_error bigint,
    permissions_ok boolean,
    recording_folder_accessible boolean,
    health_status text
        CHECK (health_status IN ('OK', 'WARNING', 'PERMISSION_ERROR', 'SYNC_ERROR', 'STORAGE_ERROR', 'HEARTBEAT_MISSING')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT devices_counts_nonnegative CHECK (
        (calls_detected IS NULL OR calls_detected >= 0)
        AND (calls_synced IS NULL OR calls_synced >= 0)
        AND (calls_pending IS NULL OR calls_pending >= 0)
        AND (calls_error IS NULL OR calls_error >= 0)
    ),
    CONSTRAINT devices_counts_all_or_none CHECK (
        (calls_detected IS NULL AND calls_synced IS NULL AND calls_pending IS NULL AND calls_error IS NULL)
        OR (calls_detected IS NOT NULL AND calls_synced IS NOT NULL AND calls_pending IS NOT NULL AND calls_error IS NOT NULL)
    ),
    CONSTRAINT devices_count_total_consistent CHECK (
        calls_detected IS NULL
        OR calls_detected = calls_synced + calls_pending + calls_error
    )
);

CREATE TABLE public.device_monitor_credentials (
    device_id uuid PRIMARY KEY REFERENCES public.devices(device_id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz
);

CREATE INDEX device_monitor_credentials_active_device_idx
    ON public.device_monitor_credentials (device_id)
    WHERE revoked_at IS NULL;

CREATE TABLE public.device_daily_stats (
    device_id uuid NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    report_date date NOT NULL,
    timezone_name text NOT NULL,
    calls_detected bigint NOT NULL,
    calls_synced bigint NOT NULL,
    calls_pending bigint NOT NULL,
    calls_error bigint NOT NULL,
    valid_duration_count bigint NOT NULL,
    valid_duration_ms bigint NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (device_id, report_date),
    CONSTRAINT device_daily_stats_counts_nonnegative CHECK (
        (calls_detected IS NULL OR calls_detected >= 0)
        AND (calls_synced IS NULL OR calls_synced >= 0)
        AND (calls_pending IS NULL OR calls_pending >= 0)
        AND (calls_error IS NULL OR calls_error >= 0)
        AND (valid_duration_count IS NULL OR valid_duration_count >= 0)
        AND (valid_duration_ms IS NULL OR valid_duration_ms >= 0)
    ),
    CONSTRAINT device_daily_stats_count_total_consistent CHECK (
        calls_detected = calls_synced + calls_pending + calls_error
    )
);

CREATE TABLE public.device_report_days (
    device_id uuid NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    report_date date NOT NULL,
    first_report_at timestamptz NOT NULL,
    last_report_at timestamptz NOT NULL,
    PRIMARY KEY (device_id, report_date),
    CONSTRAINT device_report_days_timestamp_order CHECK (last_report_at >= first_report_at)
);

CREATE TABLE public.device_alerts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id uuid NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    alert_type text NOT NULL CHECK (
        alert_type IN ('PERMISSION_ERROR', 'STORAGE_ERROR', 'SYNC_ERROR', 'HEARTBEAT_MISSING')
    ),
    severity text NOT NULL CHECK (severity IN ('warning', 'error', 'critical')),
    detected_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz,
    occurrence_count bigint NOT NULL DEFAULT 1 CHECK (occurrence_count > 0),
    description text NOT NULL,
    snapshots jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX device_alerts_one_open_per_device_type
    ON public.device_alerts (device_id, alert_type)
    WHERE resolved_at IS NULL;

CREATE INDEX device_alerts_device_history_idx
    ON public.device_alerts (device_id, detected_at DESC);

CREATE TABLE public.device_email_recipients (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_type text NOT NULL CHECK (
        recipient_type IN ('daily_summary', 'alert', 'critical_alert')
    ),
    email text NOT NULL CHECK (
        length(email) <= 320 AND email ~* '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
    ),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (recipient_type, email)
);

CREATE TABLE public.device_daily_report_settings (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    timezone_name text NOT NULL DEFAULT 'America/Argentina/Cordoba',
    local_send_time time NOT NULL DEFAULT '09:00',
    daily_summary_enabled boolean NOT NULL DEFAULT true,
    email_delivery_enabled boolean NOT NULL DEFAULT false,
    external_provider_approved boolean NOT NULL DEFAULT false,
    heartbeat_missing_enabled boolean NOT NULL DEFAULT false,
    heartbeat_missing_threshold_minutes integer,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT daily_report_settings_timezone_valid CHECK (
        timezone_name = 'America/Argentina/Cordoba'
    ),
    CONSTRAINT heartbeat_missing_threshold_policy CHECK (
        (heartbeat_missing_enabled
            AND heartbeat_missing_threshold_minutes IS NOT NULL
            AND heartbeat_missing_threshold_minutes > 0)
        OR (NOT heartbeat_missing_enabled AND heartbeat_missing_threshold_minutes IS NULL)
    )
);

INSERT INTO public.device_daily_report_settings (singleton)
VALUES (true)
ON CONFLICT (singleton) DO NOTHING;

CREATE TABLE public.device_email_outbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email_type text NOT NULL CHECK (
        email_type IN ('alert_opened', 'alert_resolved', 'daily_summary')
    ),
    recipient_email text NOT NULL CHECK (
        length(recipient_email) <= 320
        AND recipient_email ~* '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
    ),
    subject text NOT NULL CHECK (length(subject) BETWEEN 1 AND 200),
    body_text text NOT NULL CHECK (length(body_text) BETWEEN 1 AND 12000),
    body_html text NOT NULL CHECK (length(body_html) BETWEEN 1 AND 24000),
    created_at timestamptz NOT NULL DEFAULT now(),
    alert_id uuid REFERENCES public.device_alerts(id),
    report_date date,
    delivery_status text NOT NULL DEFAULT 'disabled'
        CHECK (delivery_status IN ('queued', 'disabled', 'sent', 'failed')),
    provider_message_id text,
    error_message text,
    dedupe_key text NOT NULL UNIQUE,
    CONSTRAINT device_email_outbox_related_record CHECK (
        (email_type IN ('alert_opened', 'alert_resolved') AND alert_id IS NOT NULL)
        OR (email_type = 'daily_summary' AND report_date IS NOT NULL)
    )
);

CREATE INDEX device_email_outbox_created_idx
    ON public.device_email_outbox (created_at DESC);

CREATE OR REPLACE FUNCTION public.escape_device_email_html(p_value text)
RETURNS text
LANGUAGE sql
IMMUTABLE
STRICT
SET search_path = public, pg_temp
AS $$
    SELECT replace(
        replace(
            replace(
                replace(
                    replace(p_value, '&', '&amp;'),
                    '<', '&lt;'
                ),
                '>', '&gt;'
            ),
            '"', '&quot;'
        ),
        chr(39), '&#39;'
    );
$$;

CREATE OR REPLACE FUNCTION public.prevent_device_email_snapshot_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.subject IS DISTINCT FROM OLD.subject
       OR NEW.body_text IS DISTINCT FROM OLD.body_text
       OR NEW.body_html IS DISTINCT FROM OLD.body_html
       OR NEW.id IS DISTINCT FROM OLD.id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.email_type IS DISTINCT FROM OLD.email_type
       OR NEW.recipient_email IS DISTINCT FROM OLD.recipient_email
       OR NEW.alert_id IS DISTINCT FROM OLD.alert_id
       OR NEW.report_date IS DISTINCT FROM OLD.report_date
       OR NEW.dedupe_key IS DISTINCT FROM OLD.dedupe_key THEN
        RAISE EXCEPTION 'Email outbox snapshots and identity fields are immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER device_email_outbox_snapshot_immutable
    BEFORE UPDATE ON public.device_email_outbox
    FOR EACH ROW
    EXECUTE FUNCTION public.prevent_device_email_snapshot_mutation();

CREATE OR REPLACE FUNCTION public.record_device_report_day(
    p_device_id uuid,
    p_report_date date,
    p_report_at timestamptz
)
RETURNS TABLE (first_report_at timestamptz, last_report_at timestamptz)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    INSERT INTO public.device_report_days AS existing_receipt (
        device_id, report_date, first_report_at, last_report_at
    )
    VALUES (p_device_id, p_report_date, p_report_at, p_report_at)
    ON CONFLICT (device_id, report_date)
    DO UPDATE SET last_report_at = GREATEST(existing_receipt.last_report_at, EXCLUDED.last_report_at)
    RETURNING existing_receipt.first_report_at, existing_receipt.last_report_at;
$$;

-- Atomic lifecycle operation: repeated active observations update the one
-- open event; an inactive observation resolves it; later re-occurrence opens
-- a new event. No heartbeat-missing evaluator calls this yet.
CREATE OR REPLACE FUNCTION public.apply_device_alert_condition(
    p_device_id uuid,
    p_alert_type text,
    p_is_active boolean,
    p_severity text,
    p_description text,
    p_snapshots jsonb DEFAULT '{}'::jsonb
)
RETURNS TABLE (alert_id uuid, opened boolean, resolved boolean)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_id uuid;
    v_opened boolean := false;
    v_resolved boolean := false;
    v_email_type text;
    v_delivery_enabled boolean := false;
    v_provider_approved boolean := false;
    v_event public.device_alerts%ROWTYPE;
    v_device public.devices%ROWTYPE;
    v_subject text;
    v_body_text text;
    v_body_html text;
    v_detected_local text;
    v_last_heartbeat_local text;
    v_last_sync_local text;
BEGIN
    IF p_alert_type NOT IN ('PERMISSION_ERROR', 'STORAGE_ERROR', 'SYNC_ERROR', 'HEARTBEAT_MISSING') THEN
        RAISE EXCEPTION 'Unsupported alert type';
    END IF;
    IF p_severity NOT IN ('warning', 'error', 'critical') THEN
        RAISE EXCEPTION 'Unsupported alert severity';
    END IF;

    IF p_is_active THEN
        INSERT INTO public.device_alerts AS current_alert (
            device_id, alert_type, severity, detected_at, last_seen_at,
            occurrence_count, description, snapshots
        )
        VALUES (
            p_device_id, p_alert_type, p_severity, now(), now(), 1,
            p_description, COALESCE(p_snapshots, '{}'::jsonb)
        )
        ON CONFLICT (device_id, alert_type) WHERE resolved_at IS NULL
        DO UPDATE SET
            last_seen_at = now(),
            occurrence_count = current_alert.occurrence_count + 1,
            severity = EXCLUDED.severity,
            description = EXCLUDED.description,
            snapshots = EXCLUDED.snapshots
        RETURNING current_alert.id, (xmax = 0)
        INTO v_id, v_opened;
        IF v_opened THEN
            v_email_type := 'alert_opened';
        END IF;
    ELSE
        UPDATE public.device_alerts AS current_alert
           SET resolved_at = now(),
               last_seen_at = now(),
               description = p_description,
               snapshots = COALESCE(p_snapshots, '{}'::jsonb)
         WHERE current_alert.device_id = p_device_id
           AND current_alert.alert_type = p_alert_type
           AND current_alert.resolved_at IS NULL
        RETURNING current_alert.id INTO v_id;
        v_resolved := FOUND;
        IF v_resolved THEN
            v_email_type := 'alert_resolved';
        END IF;
    END IF;

    -- Record the email intent in the same database transaction as the alert
    -- transition. A failed outbox write rolls back the transition; retries
    -- therefore cannot leave an opened/resolved alert without its deduped row.
    IF v_email_type IS NOT NULL THEN
        SELECT settings.email_delivery_enabled, settings.external_provider_approved
          INTO v_delivery_enabled, v_provider_approved
          FROM public.device_daily_report_settings AS settings
         WHERE settings.singleton = true;
        v_delivery_enabled := COALESCE(v_delivery_enabled, false);
        v_provider_approved := COALESCE(v_provider_approved, false);

        SELECT event_row.* INTO v_event
          FROM public.device_alerts AS event_row
         WHERE event_row.id = v_id;
        SELECT device_row.* INTO v_device
          FROM public.devices AS device_row
         WHERE device_row.device_id = p_device_id;

        v_subject := CASE
            WHEN v_email_type = 'alert_opened' THEN 'Maipú Call Sync | Alerta de dispositivo'
            ELSE 'Maipú Call Sync | Incidencia resuelta'
        END;
        v_detected_local := to_char(
            v_event.detected_at AT TIME ZONE 'America/Argentina/Cordoba',
            'DD/MM/YYYY HH24:MI'
        );
        v_last_heartbeat_local := COALESCE(
            to_char(v_device.last_heartbeat AT TIME ZONE 'America/Argentina/Cordoba', 'DD/MM/YYYY HH24:MI'),
            'Sin dato'
        );
        v_last_sync_local := COALESCE(
            to_char(v_device.last_successful_sync AT TIME ZONE 'America/Argentina/Cordoba', 'DD/MM/YYYY HH24:MI'),
            'Sin dato'
        );
        v_body_text :=
            CASE
                WHEN v_email_type = 'alert_opened'
                    THEN 'Se detectó una condición que puede afectar la sincronización.'
                ELSE 'La condición informada para este dispositivo se resolvió.'
            END
            || E'\n\nVendedor: ' || left(v_device.seller_name, 120)
            || E'\nLínea: ' || left(v_device.corporate_phone, 40)
            || E'\nDispositivo: ' || COALESCE(left(v_device.device_model, 100), 'Sin dato')
            || E'\nEstado: ' || v_event.alert_type
            || E'\nDetalle: ' || left(v_event.description, 200)
            || E'\nSeveridad: ' || v_event.severity
            || E'\nDetectado: ' || v_detected_local
            || E'\nÚltimo heartbeat: ' || v_last_heartbeat_local
            || E'\nÚltima sincronización: ' || v_last_sync_local;
        v_body_html :=
            '<div style="font-family:Arial,sans-serif;color:#25252a;max-width:600px">'
            || '<h2 style="color:#c21870">MAIPÚ CALL SYNC</h2><p>'
            || CASE
                WHEN v_email_type = 'alert_opened'
                    THEN 'Se detectó una condición que puede afectar la sincronización.'
                ELSE 'La condición informada para este dispositivo se resolvió.'
            END
            || '</p><p><strong>Vendedor:</strong> '
            || public.escape_device_email_html(left(v_device.seller_name, 120))
            || '<br><strong>Línea:</strong> '
            || public.escape_device_email_html(left(v_device.corporate_phone, 40))
            || '<br><strong>Dispositivo:</strong> '
            || public.escape_device_email_html(COALESCE(left(v_device.device_model, 100), 'Sin dato'))
            || '<br><strong>Estado:</strong> '
            || public.escape_device_email_html(v_event.alert_type)
            || '<br><strong>Detalle:</strong> '
            || public.escape_device_email_html(left(v_event.description, 200))
            || '<br><strong>Severidad:</strong> '
            || public.escape_device_email_html(v_event.severity)
            || '<br><strong>Detectado:</strong> '
            || public.escape_device_email_html(v_detected_local)
            || '<br><strong>Último heartbeat:</strong> '
            || public.escape_device_email_html(v_last_heartbeat_local)
            || '<br><strong>Última sincronización:</strong> '
            || public.escape_device_email_html(v_last_sync_local)
            || '</p></div>';

        INSERT INTO public.device_email_outbox (
            email_type,
            recipient_email,
            alert_id,
            subject,
            body_text,
            body_html,
            delivery_status,
            dedupe_key
        )
        SELECT
            v_email_type,
            recipients.email,
            v_id,
            v_subject,
            v_body_text,
            v_body_html,
            CASE
                WHEN v_delivery_enabled AND v_provider_approved THEN 'queued'
                ELSE 'disabled'
            END,
            'alert:' || v_id::text || ':' || v_email_type || ':' || recipients.email
        FROM (
            SELECT DISTINCT lower(recipient.email) AS email
              FROM public.device_email_recipients AS recipient
             WHERE recipient.is_active
               AND (
                   recipient.recipient_type = 'alert'
                   OR (
                       p_severity = 'critical'
                       AND recipient.recipient_type = 'critical_alert'
                   )
               )
        ) AS recipients
        ON CONFLICT (dedupe_key) DO NOTHING;
    END IF;

    IF p_is_active THEN
        RETURN QUERY SELECT v_id, v_opened, false;
        RETURN;
    END IF;

    RETURN QUERY SELECT v_id, false, v_resolved;
END;
$$;

ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_monitor_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_daily_stats ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_report_days ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_email_recipients ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_daily_report_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_email_outbox ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.devices FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.device_monitor_credentials FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.device_daily_stats FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.device_report_days FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.device_alerts FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.device_email_recipients FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.device_daily_report_settings FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.device_email_outbox FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.devices TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.device_monitor_credentials TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.device_daily_stats TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.device_report_days TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.device_alerts TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.device_email_recipients TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.device_daily_report_settings TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.device_email_outbox TO service_role;

DROP POLICY IF EXISTS "service role manages device monitoring" ON public.devices;
CREATE POLICY "service role manages device monitoring" ON public.devices
    FOR ALL TO service_role USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS "service role manages device monitor credentials" ON public.device_monitor_credentials;
CREATE POLICY "service role manages device monitor credentials" ON public.device_monitor_credentials
    FOR ALL TO service_role USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS "service role manages device daily stats" ON public.device_daily_stats;
CREATE POLICY "service role manages device daily stats" ON public.device_daily_stats
    FOR ALL TO service_role USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS "service role manages device report days" ON public.device_report_days;
CREATE POLICY "service role manages device report days" ON public.device_report_days
    FOR ALL TO service_role USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS "service role manages device alerts" ON public.device_alerts;
CREATE POLICY "service role manages device alerts" ON public.device_alerts
    FOR ALL TO service_role USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS "service role manages device email recipients" ON public.device_email_recipients;
CREATE POLICY "service role manages device email recipients" ON public.device_email_recipients
    FOR ALL TO service_role USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS "service role manages device daily report settings" ON public.device_daily_report_settings;
CREATE POLICY "service role manages device daily report settings" ON public.device_daily_report_settings
    FOR ALL TO service_role USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS "service role manages device email outbox" ON public.device_email_outbox;
CREATE POLICY "service role manages device email outbox" ON public.device_email_outbox
    FOR ALL TO service_role USING (true) WITH CHECK (true);

REVOKE ALL ON FUNCTION public.apply_device_alert_condition(uuid, text, boolean, text, text, jsonb)
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.apply_device_alert_condition(uuid, text, boolean, text, text, jsonb)
    TO service_role;
REVOKE ALL ON FUNCTION public.record_device_report_day(uuid, date, timestamptz)
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.record_device_report_day(uuid, date, timestamptz)
    TO service_role;
REVOKE ALL ON FUNCTION public.escape_device_email_html(text)
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.escape_device_email_html(text)
    TO service_role;
REVOKE ALL ON FUNCTION public.prevent_device_email_snapshot_mutation()
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.prevent_device_email_snapshot_mutation()
    TO service_role;

COMMENT ON TABLE public.device_monitor_credentials IS
    'Stores only SHA-256 hashes of 64-character lowercase-hex per-installation monitoring tokens. Never store the raw token.';
COMMENT ON TABLE public.device_email_outbox IS
    'Immutable, bounded email content snapshots and audit state. No provider or sender is configured; default rows are disabled.';
COMMENT ON TABLE public.device_daily_report_settings IS
    'Central report/email settings. No cron job is installed; heartbeat-missing is disabled with no threshold.';