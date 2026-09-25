import { isRecord } from "./http.ts";

export const monitorCountFields = [
  "calls_detected",
  "calls_synced",
  "calls_pending",
  "calls_error",
] as const;

export const isDeviceUuid = (value: unknown): value is string =>
  typeof value === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);

export const validCount = (value: unknown): value is number | null =>
  value === null ||
  (typeof value === "number" && Number.isSafeInteger(value) && value >= 0);

export const validDate = (value: unknown): value is string =>
  typeof value === "string" &&
  /^\d{4}-\d{2}-\d{2}$/.test(value) &&
  !Number.isNaN(Date.parse(`${value}T00:00:00Z`)) &&
  new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;

export const validInstant = (value: unknown): value is string | null =>
  value === null ||
  (typeof value === "string" &&
    /^\d{4}-\d{2}-\d{2}T/.test(value) &&
    !Number.isNaN(Date.parse(value)));

export const validateCounts = (
  value: Record<string, unknown>,
): string | null => {
  for (const field of monitorCountFields) {
    if (!(field in value) || !validCount(value[field])) {
      return `${field} must be a non-negative integer or null`;
    }
  }
  const counts = monitorCountFields.map((field) => value[field] as number | null);
  if (counts.every((count) => count !== null)) {
    const [detected, synced, pending, error] = counts as number[];
    if (detected !== synced + pending + error) {
      return "calls_detected must equal calls_synced + calls_pending + calls_error";
    }
  }
  return null;
};

export const validTimezone = (value: unknown): value is string => {
  if (typeof value !== "string" || value.length > 80) return false;
  try {
    new Intl.DateTimeFormat("en", { timeZone: value });
    return true;
  } catch {
    return false;
  }
};

export const getActiveAlertTypes = (
  report: Pick<Record<string, unknown>, "permissions_ok" | "recording_folder_accessible" | "health_status">,
): string[] => {
  const activeTypes = new Set<string>();
  if (report.permissions_ok === false || report.health_status === "PERMISSION_ERROR") {
    activeTypes.add("PERMISSION_ERROR");
  }
  if (report.recording_folder_accessible === false || report.health_status === "STORAGE_ERROR") {
    activeTypes.add("STORAGE_ERROR");
  }
  if (report.health_status === "SYNC_ERROR") activeTypes.add("SYNC_ERROR");
  return [...activeTypes];
};

export const scanTimestampForReport = (
  hasCompleteCounts: boolean,
  receivedAt: string,
  previousScanAt: string | null,
): string | null => hasCompleteCounts ? receivedAt : previousScanAt;

export const parseDailyStatsShape = (
  input: unknown,
): { entries?: Record<string, unknown>[]; error?: string } => {
  if (!Array.isArray(input) || input.length > 40) {
    return { error: "daily_stats must be an array with at most 40 entries" };
  }
  const entries: Record<string, unknown>[] = [];
  const seen = new Set<string>();
  for (const item of input) {
    if (!isRecord(item)) return { error: "Each daily_stats entry must be an object" };
    if (!validDate(item.report_date)) return { error: "Invalid daily_stats.report_date" };
    if (!validTimezone(item.timezone_name)) return { error: "Invalid daily_stats.timezone_name" };
    if (seen.has(item.report_date)) return { error: "daily_stats cannot repeat a report_date" };
    seen.add(item.report_date);
    const countsError = validateCounts(item);
    if (countsError) return { error: `daily_stats: ${countsError}` };
    for (const field of ["valid_duration_count", "valid_duration_ms"] as const) {
      if (!(field in item) || !validCount(item[field])) {
        return { error: `${field} must be a non-negative integer or null` };
      }
    }
    entries.push(item);
  }
  return { entries };
};