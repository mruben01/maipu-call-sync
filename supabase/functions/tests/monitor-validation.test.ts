import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  getActiveAlertTypes,
  isDeviceUuid,
  parseDailyStatsShape,
  scanTimestampForReport,
  validTimezone,
  validateCounts,
} from "../_shared/monitor-validation.ts";
import {
  dailySummaryDedupeKey,
  emailDeliveryStatus,
} from "../_shared/outbox.ts";
import {
  localCalendarDate,
  reportedDeviceIdsForDate,
} from "../_shared/report-days.ts";
import { renderDailyEmailSnapshot } from "../_shared/daily-email.ts";

test("count validation preserves unknown readings and checks complete totals", () => {
  assert.equal(validateCounts({
    calls_detected: null,
    calls_synced: null,
    calls_pending: null,
    calls_error: null,
  }), null);
  assert.equal(validateCounts({
    calls_detected: 10,
    calls_synced: 7,
    calls_pending: 2,
    calls_error: 1,
  }), null);
  assert.match(validateCounts({
    calls_detected: 10,
    calls_synced: 7,
    calls_pending: 1,
    calls_error: 1,
  }) ?? "", /must equal/);
  assert.match(validateCounts({
    calls_detected: -1,
    calls_synced: 0,
    calls_pending: 0,
    calls_error: 0,
  }) ?? "", /non-negative/);
});

test("device id and daily stat validation accept UUID and valid Buenos Aires timezone", () => {
  assert.equal(isDeviceUuid("7b95bd0a-6b22-4d21-aea8-fc22d8ce9288"), true);
  assert.equal(isDeviceUuid("not-a-uuid"), false);
  assert.equal(validTimezone("America/Argentina/Buenos_Aires"), true);
  const result = parseDailyStatsShape([{
    report_date: "2026-09-25",
    timezone_name: "America/Argentina/Buenos_Aires",
    calls_detected: 1,
    calls_synced: 1,
    calls_pending: 0,
    calls_error: 0,
    valid_duration_count: 1,
    valid_duration_ms: 120000,
  }]);
  assert.equal(result.error, undefined);
  assert.equal(result.entries?.length, 1);
  const zeroCalls = parseDailyStatsShape([{
    report_date: "2026-09-25",
    timezone_name: "America/Argentina/Buenos_Aires",
    calls_detected: 0,
    calls_synced: 0,
    calls_pending: 0,
    calls_error: 0,
    valid_duration_count: 0,
    valid_duration_ms: 0,
  }]);
  assert.equal(zeroCalls.error, undefined);
  assert.equal(zeroCalls.entries?.[0].calls_detected, 0);
});

test("alert condition mapping does not create heartbeat-missing alerts", () => {
  assert.deepEqual(getActiveAlertTypes({
    permissions_ok: false,
    recording_folder_accessible: false,
    health_status: "SYNC_ERROR",
  }), ["PERMISSION_ERROR", "STORAGE_ERROR", "SYNC_ERROR"]);
  assert.deepEqual(getActiveAlertTypes({
    permissions_ok: true,
    recording_folder_accessible: null,
    health_status: "WARNING",
  }), []);
});

test("daily email intent stays disabled without approval and dedupes address casing", () => {
  assert.equal(emailDeliveryStatus(false, false), "disabled");
  assert.equal(emailDeliveryStatus(true, false), "disabled");
  assert.equal(emailDeliveryStatus(true, true), "queued");
  assert.equal(
    dailySummaryDedupeKey("2026-09-25", "Ops@example.com"),
    dailySummaryDedupeKey("2026-09-25", "ops@example.com"),
  );
});

test("report-day membership is historical and not erased by a later report", () => {
  const receipts = [
    { device_id: "device-a", report_date: "2026-09-24" },
    { device_id: "device-a", report_date: "2026-09-25" },
    { device_id: "device-b", report_date: "2026-09-25" },
  ];
  assert.deepEqual(
    [...reportedDeviceIdsForDate(receipts, "2026-09-24")],
    ["device-a"],
  );
  assert.deepEqual(
    [...reportedDeviceIdsForDate(receipts, "2026-09-25")],
    ["device-a", "device-b"],
  );
  assert.equal(
    localCalendarDate(new Date("2026-09-26T02:00:00.000Z"), "America/Argentina/Cordoba"),
    "2026-09-25",
  );
});

test("last scan timestamp advances only for a complete count quartet", () => {
  assert.equal(
    scanTimestampForReport(false, "2026-09-25T12:00:00Z", "2026-09-24T09:00:00Z"),
    "2026-09-24T09:00:00Z",
  );
  assert.equal(
    scanTimestampForReport(false, "2026-09-25T12:00:00Z", null),
    null,
  );
  assert.equal(
    scanTimestampForReport(true, "2026-09-25T12:00:00Z", null),
    "2026-09-25T12:00:00Z",
  );
});

test("daily email snapshots are deterministic, bounded, and omit phone numbers", () => {
  const report = {
    report_date: "2026-09-25",
    devices: { monitored: 1, reported: 1, ok: 1, warnings: 0, errors: 0 },
    totals: {
      calls_detected: 0,
      calls_synced: 0,
      calls_pending: 0,
      calls_error: 0,
    },
    sync_percentage: null,
    attention: [{ seller_name: "Ana <Equipo>", reason: "0 llamadas pendientes" }],
  };
  const first = renderDailyEmailSnapshot(report);
  const second = renderDailyEmailSnapshot(report);
  assert.deepEqual(first, second);
  assert.match(first.body_text, /Llamadas detectadas: 0/);
  assert.match(first.body_html, /Ana &lt;Equipo&gt;/);
  assert.equal(first.body_text.includes("3515551234"), false);
  assert.ok(first.body_text.length <= 12000);
  assert.ok(first.body_html.length <= 24000);
});

test("outbox SQL snapshots are immutable and alert inserts use unique dedupe conflict handling", () => {
  const migration = readFileSync(
    new URL("../../migrations/20260925000000_device_monitoring_v3.sql", import.meta.url),
    "utf8",
  );
  assert.match(migration, /subject text NOT NULL CHECK/);
  assert.match(migration, /body_text text NOT NULL CHECK/);
  assert.match(migration, /body_html text NOT NULL CHECK/);
  assert.match(migration, /CREATE TRIGGER device_email_outbox_snapshot_immutable/);
  assert.match(migration, /ON CONFLICT \(dedupe_key\) DO NOTHING/);
  assert.match(migration, /v_body_text :=/);
  assert.match(migration, /v_body_html :=/);
});