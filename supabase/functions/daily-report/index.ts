import { createClient } from "npm:@supabase/supabase-js@2";
import {
  bearerToken,
  isRecord,
  jsonResponse,
  safeEqual,
} from "../_shared/http.ts";
import {
  dailySummaryDedupeKey,
  emailDeliveryStatus,
} from "../_shared/outbox.ts";
import {
  localCalendarDate,
  reportedDeviceIdsForDate,
} from "../_shared/report-days.ts";
import {
  renderDailyEmailSnapshot,
  type DailyEmailReport,
} from "../_shared/daily-email.ts";

const centralReportTimezone = "America/Argentina/Cordoba";
const errorStatuses = new Set([
  "PERMISSION_ERROR",
  "STORAGE_ERROR",
  "SYNC_ERROR",
  "HEARTBEAT_MISSING",
]);
const metricFields = [
  "calls_detected",
  "calls_synced",
  "calls_pending",
  "calls_error",
  "valid_duration_count",
  "valid_duration_ms",
] as const;

type StatsRow = {
  device_id: string;
  calls_detected: number | null;
  calls_synced: number | null;
  calls_pending: number | null;
  calls_error: number | null;
  valid_duration_count: number | null;
  valid_duration_ms: number | null;
};

const isDate = (value: unknown): value is string =>
  typeof value === "string" &&
  /^\d{4}-\d{2}-\d{2}$/.test(value) &&
  !Number.isNaN(Date.parse(`${value}T00:00:00Z`)) &&
  new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;

const percent = (synced: number | null, detected: number | null): number | null =>
  synced === null || detected === null || detected === 0
    ? null
    : Math.round((synced / detected) * 1000) / 10;

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }
  const serviceUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const suppliedToken = bearerToken(request);
  if (!serviceUrl || !serviceKey) {
    return jsonResponse({ error: "Reporting service is not configured" }, 500);
  }
  if (!suppliedToken || !safeEqual(suppliedToken, serviceKey)) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  let requestBody: unknown = {};
  try {
    const text = await request.text();
    if (text.length > 16 * 1024) return jsonResponse({ error: "Request body too large" }, 413);
    if (text) requestBody = JSON.parse(text);
  } catch {
    return jsonResponse({ error: "Request body must be valid JSON" }, 400);
  }
  if (!isRecord(requestBody)) return jsonResponse({ error: "Request body must be an object" }, 400);
  if (requestBody.report_date !== undefined && !isDate(requestBody.report_date)) {
    return jsonResponse({ error: "report_date must be YYYY-MM-DD" }, 400);
  }

  const supabase = createClient(serviceUrl, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: settings, error: settingsError } = await supabase
    .from("device_daily_report_settings")
    .select("timezone_name,daily_summary_enabled,email_delivery_enabled,external_provider_approved")
    .eq("singleton", true)
    .single();
  if (settingsError) {
    console.error("Daily report settings lookup failed", settingsError.message);
    return jsonResponse({ error: "Could not load report settings" }, 500);
  }
  if (settings.timezone_name !== centralReportTimezone) {
    return jsonResponse({ error: "Unsupported report timezone configuration" }, 500);
  }

  const today = localCalendarDate(new Date(), settings.timezone_name);
  const yesterday = new Date(`${today}T00:00:00Z`);
  yesterday.setUTCDate(yesterday.getUTCDate() - 1);
  const effectiveDate = requestBody.report_date === undefined
    ? yesterday.toISOString().slice(0, 10)
    : requestBody.report_date as string;

  const [
    { data: deviceRows, error: devicesError },
    { data: statsRows, error: statsError },
    { data: reportDayRows, error: reportDaysError },
  ] = await Promise.all([
    supabase.from("devices")
      .select("device_id,seller_name,health_status,active")
      .eq("active", true)
      .order("seller_name", { ascending: true }),
    supabase.from("device_daily_stats")
      .select("device_id,calls_detected,calls_synced,calls_pending,calls_error,valid_duration_count,valid_duration_ms")
      .eq("report_date", effectiveDate),
    supabase.from("device_report_days")
      .select("device_id,report_date")
      .eq("report_date", effectiveDate),
  ]);
  if (devicesError || statsError || reportDaysError) {
    console.error(
      "Daily report query failed",
      devicesError?.message ?? statsError?.message ?? reportDaysError?.message,
    );
    return jsonResponse({ error: "Could not read central monitoring data" }, 500);
  }
  const devices = deviceRows ?? [];
  const activeDeviceIds = new Set(devices.map((device) => device.device_id));
  const stats = ((statsRows ?? []) as StatsRow[])
    .filter((row) => activeDeviceIds.has(row.device_id));
  const statsByDevice = new Map(stats.map((row) => [row.device_id, row]));
  const receiptIds = reportedDeviceIdsForDate(reportDayRows ?? [], effectiveDate);
  const reported = new Set(
    devices
      .filter((device) => receiptIds.has(device.device_id))
      .map((device) => device.device_id),
  );
  const totals = Object.fromEntries(
    metricFields.map((field) => {
      const values = devices.map((device) => statsByDevice.get(device.device_id)?.[field] ?? null);
      return [
        field,
        values.length > 0 && values.every((value) => value !== null)
          ? values.reduce((total, value) => total + (value ?? 0), 0)
          : null,
      ];
    }),
  ) as Record<(typeof metricFields)[number], number | null>;
  const okCount = devices.filter((device) => device.health_status === "OK").length;
  const warningCount = devices.filter((device) => device.health_status === "WARNING").length;
  const errorCount = devices.filter((device) => errorStatuses.has(device.health_status ?? "")).length;

  const attention = devices.flatMap((device) => {
    const daily = statsByDevice.get(device.device_id);
    const reasons: string[] = [];
    if (!reported.has(device.device_id)) {
      reasons.push("Sin reporte reciente");
    }
    if (device.health_status && errorStatuses.has(device.health_status)) {
      reasons.push(device.health_status);
    } else if (device.health_status === "WARNING") {
      reasons.push("Advertencia");
    }
    if (daily?.calls_pending && daily.calls_pending > 0) {
      reasons.push(`${display(daily.calls_pending)} llamadas pendientes`);
    }
    if (daily?.calls_error && daily.calls_error > 0) {
      reasons.push(`${display(daily.calls_error)} llamadas con error`);
    }
    if (!reasons.length) return [];
    return [{
      seller_name: device.seller_name,
      reason: [...new Set(reasons)].join(" · "),
    }];
  });

  const report: Record<string, unknown> = {
    report_date: effectiveDate,
    timezone_name: settings.timezone_name,
    generated_at: new Date().toISOString(),
    devices: {
      monitored: devices.length,
      reported: reported.size,
      ok: okCount,
      warnings: warningCount,
      errors: errorCount,
    },
    totals,
    sync_percentage: percent(totals.calls_synced, totals.calls_detected),
    attention,
  };
  const emailSnapshot = renderDailyEmailSnapshot(report as DailyEmailReport);
  let outboxRows = 0;
  if (settings.daily_summary_enabled) {
    const { data: recipients, error: recipientsError } = await supabase
      .from("device_email_recipients")
      .select("email")
      .eq("recipient_type", "daily_summary")
      .eq("is_active", true);
    if (recipientsError) {
      console.error("Daily report recipient lookup failed", recipientsError.message);
      return jsonResponse({ error: "Could not load report recipients" }, 500);
    }
    const uniqueEmails = [...new Set((recipients ?? []).map((item) => item.email.toLowerCase()))];
    if (uniqueEmails.length) {
      const { error: outboxError } = await supabase.from("device_email_outbox").upsert(
        uniqueEmails.map((email) => ({
          email_type: "daily_summary",
          recipient_email: email,
          report_date: effectiveDate,
          subject: emailSnapshot.subject,
          body_text: emailSnapshot.body_text,
          body_html: emailSnapshot.body_html,
          delivery_status: emailDeliveryStatus(
            settings.email_delivery_enabled,
            settings.external_provider_approved,
          ),
          dedupe_key: dailySummaryDedupeKey(effectiveDate, email),
        })),
        { onConflict: "dedupe_key", ignoreDuplicates: true },
      );
      if (outboxError) {
        console.error("Daily report outbox write failed", outboxError.message);
        return jsonResponse({ error: "Could not record report email intent" }, 500);
      }
      outboxRows = uniqueEmails.length;
    }
  }

  return jsonResponse({
    generated: true,
    report,
    email: {
      subject: emailSnapshot.subject,
      text: emailSnapshot.body_text,
      html: emailSnapshot.body_html,
      outbox_rows: outboxRows,
      delivery: "disabled_until_provider_approval",
    },
  });
});