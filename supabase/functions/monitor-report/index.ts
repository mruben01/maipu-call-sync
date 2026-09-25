import { createClient } from "npm:@supabase/supabase-js@2";
import { bearerToken, isRecord, jsonResponse, sha256Hex } from "../_shared/http.ts";
import {
  getActiveAlertTypes,
  isDeviceUuid,
  monitorCountFields as countFields,
  parseDailyStatsShape,
  scanTimestampForReport,
  validInstant,
  validateCounts,
} from "../_shared/monitor-validation.ts";
import { localCalendarDate } from "../_shared/report-days.ts";

const statuses = new Set([
  "OK",
  "WARNING",
  "PERMISSION_ERROR",
  "SYNC_ERROR",
  "STORAGE_ERROR",
  "HEARTBEAT_MISSING",
]);
const maxBodyBytes = 256 * 1024;
const reportTimezone = "America/Argentina/Cordoba";

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }
  const rawToken = bearerToken(request);
  if (!rawToken || !/^[0-9a-f]{64}$/.test(rawToken)) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }
  const contentLength = Number(request.headers.get("content-length") ?? 0);
  if (contentLength > maxBodyBytes) {
    return jsonResponse({ error: "Request body too large" }, 413);
  }

  let body: unknown;
  try {
    const rawBody = await request.text();
    if (new TextEncoder().encode(rawBody).byteLength > maxBodyBytes) {
      return jsonResponse({ error: "Request body too large" }, 413);
    }
    body = JSON.parse(rawBody);
  } catch {
    return jsonResponse({ error: "Request body must be valid JSON" }, 400);
  }
  if (!isRecord(body)) return jsonResponse({ error: "Request body must be an object" }, 400);

  const deviceId = body.device_id;
  if (!isDeviceUuid(deviceId)) {
    return jsonResponse({ error: "Invalid device_id" }, 400);
  }
  if (typeof body.device_model !== "string" || !body.device_model.trim() ||
    body.device_model.length > 160) {
    return jsonResponse({ error: "Invalid device_model" }, 400);
  }
  if (typeof body.android_version !== "string" || body.android_version.length > 80) {
    return jsonResponse({ error: "Invalid android_version" }, 400);
  }
  if (typeof body.app_version !== "string" || body.app_version.length > 80) {
    return jsonResponse({ error: "Invalid app_version" }, 400);
  }
  if (typeof body.permissions_ok !== "boolean") {
    return jsonResponse({ error: "permissions_ok must be boolean" }, 400);
  }
  if (body.recording_folder_accessible !== null &&
    typeof body.recording_folder_accessible !== "boolean") {
    return jsonResponse({ error: "recording_folder_accessible must be boolean or null" }, 400);
  }
  if (!validInstant(body.last_successful_sync)) {
    return jsonResponse({ error: "last_successful_sync must be an ISO timestamp or null" }, 400);
  }
  if (typeof body.health_status !== "string" || !statuses.has(body.health_status)) {
    return jsonResponse({ error: "Unsupported health_status" }, 400);
  }
  const countsError = validateCounts(body);
  if (countsError) return jsonResponse({ error: countsError }, 400);
  const parsedStats = parseDailyStatsShape(body.daily_stats);
  if (parsedStats.error) return jsonResponse({ error: parsedStats.error }, 400);

  const serviceUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!serviceUrl || !serviceKey) {
    return jsonResponse({ error: "Monitoring service is not configured" }, 500);
  }
  const supabase = createClient(serviceUrl, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: credential, error: credentialError } = await supabase
    .from("device_monitor_credentials")
    .select("device_id")
    .eq("device_id", deviceId.toLowerCase())
    .eq("token_hash", await sha256Hex(rawToken))
    .is("revoked_at", null)
    .maybeSingle();
  if (credentialError) {
    console.error("Credential lookup failed", credentialError.message);
    return jsonResponse({ error: "Authentication service unavailable" }, 500);
  }
  if (!credential || credential.device_id !== deviceId.toLowerCase()) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  // The device registry is provisioned centrally; reports cannot create identities
  // or alter seller/line/active status.
  const { data: device, error: deviceError } = await supabase
    .from("devices")
    .select("device_id,seller_name,corporate_phone,active,last_successful_sync,last_scan_received_at")
    .eq("device_id", deviceId.toLowerCase())
    .maybeSingle();
  if (deviceError) {
    console.error("Device lookup failed", deviceError.message);
    return jsonResponse({ error: "Monitoring service unavailable" }, 500);
  }
  if (!device || !device.active) {
    return jsonResponse({ error: "Device is not active" }, 403);
  }

  const reportedSync = body.last_successful_sync as string | null;
  const savedSync = device.last_successful_sync as string | null;
  const lastSync = reportedSync && (!savedSync || Date.parse(reportedSync) > Date.parse(savedSync))
    ? reportedSync
    : savedSync;
  const hasAllCounts = countFields.every((field) => body[field] !== null);
  const counts = Object.fromEntries(
    countFields.map((field) => [field, hasAllCounts ? body[field] : null]),
  );
  const now = new Date().toISOString();
  const { error: updateError } = await supabase.from("devices").update({
    device_model: body.device_model,
    android_version: body.android_version,
    app_version: body.app_version,
    last_heartbeat: now,
    last_scan_received_at: scanTimestampForReport(
      hasAllCounts,
      now,
      device.last_scan_received_at,
    ),
    last_successful_sync: lastSync,
    ...counts,
    permissions_ok: body.permissions_ok,
    recording_folder_accessible: body.recording_folder_accessible,
    health_status: body.health_status,
    updated_at: now,
  }).eq("device_id", deviceId.toLowerCase());
  if (updateError) {
    console.error("Device report update failed", updateError.message);
    return jsonResponse({ error: "Could not save device report" }, 500);
  }

  const stats = (parsedStats.entries ?? []).map((row) => ({
    report_date: row.report_date as string,
    timezone_name: row.timezone_name as string,
    calls_detected: row.calls_detected as number | null,
    calls_synced: row.calls_synced as number | null,
    calls_pending: row.calls_pending as number | null,
    calls_error: row.calls_error as number | null,
    valid_duration_count: row.valid_duration_count as number | null,
    valid_duration_ms: row.valid_duration_ms as number | null,
  })).filter((row) =>
    countFields.every((field) => row[field] !== null) &&
    row.valid_duration_count !== null &&
    row.valid_duration_ms !== null
  );
  if (stats.length) {
    const { error } = await supabase.from("device_daily_stats").upsert(
      stats.map((row) => ({ device_id: deviceId.toLowerCase(), ...row, updated_at: now })),
      { onConflict: "device_id,report_date" },
    );
    if (error) {
      console.error("Daily stats write failed", error.message);
      return jsonResponse({ error: "Could not save daily statistics" }, 500);
    }
  }

  const activeTypes = new Set(getActiveAlertTypes(body));

  const alertDescriptions: Record<string, string> = {
    PERMISSION_ERROR: "Se detectó un problema con los permisos requeridos.",
    STORAGE_ERROR: "No se pudo confirmar el acceso a la carpeta de grabaciones.",
    SYNC_ERROR: "La aplicación reportó un error de sincronización.",
  };
  for (const type of ["PERMISSION_ERROR", "STORAGE_ERROR", "SYNC_ERROR"]) {
    const active = activeTypes.has(type);
    const severity = type === "STORAGE_ERROR" ? "critical" : "error";
    const { error } = await supabase.rpc("apply_device_alert_condition", {
      p_device_id: deviceId.toLowerCase(),
      p_alert_type: type,
      p_is_active: active,
      p_severity: severity,
      p_description: active ? alertDescriptions[type] : `La condición ${type} ya no se informa.`,
      p_snapshots: {
        health_status: body.health_status,
        permissions_ok: body.permissions_ok,
        recording_folder_accessible: body.recording_folder_accessible,
        calls_detected: counts.calls_detected,
        calls_synced: counts.calls_synced,
        calls_pending: counts.calls_pending,
        calls_error: counts.calls_error,
      },
    });
    if (error) {
      console.error("Alert lifecycle update failed", error.message);
      return jsonResponse({ error: "Could not update device alerts" }, 500);
    }
  }

  const { error: receiptError } = await supabase.rpc("record_device_report_day", {
    p_device_id: deviceId.toLowerCase(),
    p_report_date: localCalendarDate(new Date(now), reportTimezone),
    p_report_at: now,
  });
  if (receiptError) {
    console.error("Report-day receipt write failed", receiptError.message);
    return jsonResponse({ error: "Could not record report receipt" }, 500);
  }

  return jsonResponse({
    ok: true,
    assigned_name: device.seller_name,
    assigned_line: device.corporate_phone,
  });
});