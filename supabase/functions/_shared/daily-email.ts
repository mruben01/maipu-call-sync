type DeviceTotals = {
  monitored: number;
  reported: number;
  ok: number;
  warnings: number;
  errors: number;
};

type AttentionItem = {
  seller_name: string;
  reason: string;
};

export type DailyEmailReport = {
  report_date: string;
  devices: DeviceTotals;
  totals: {
    calls_detected: number | null;
    calls_synced: number | null;
    calls_pending: number | null;
    calls_error: number | null;
  };
  sync_percentage: number | null;
  attention: AttentionItem[];
};

const display = (value: number | null): string =>
  value === null ? "Sin datos" : new Intl.NumberFormat("es-AR").format(value);

const escapeHtml = (value: unknown): string => {
  const escaped: Record<string, string> = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  };
  return String(value ?? "").replace(/[&<>"']/g, (character) => escaped[character]);
};

const bounded = (value: string, maxLength: number): string =>
  value.length <= maxLength ? value : `${value.slice(0, maxLength - 1)}…`;

export const renderDailyEmailSnapshot = (
  report: DailyEmailReport,
): { subject: string; body_text: string; body_html: string } => {
  const subject = `Maipú Call Sync | Resumen diario ${report.report_date}`;
  const visibleAttention = report.attention.slice(0, 10).map((item) => ({
    seller_name: bounded(item.seller_name, 80),
    reason: bounded(item.reason, 160),
  }));
  const omittedCount = Math.max(0, report.attention.length - visibleAttention.length);
  const lines = [
    "MAIPÚ CALL SYNC",
    `Resumen diario · ${report.report_date}`,
    "",
    `Dispositivos monitoreados: ${report.devices.monitored}`,
    `Dispositivos que reportaron: ${report.devices.reported}`,
    `Llamadas detectadas: ${display(report.totals.calls_detected)}`,
    `Sincronizadas: ${display(report.totals.calls_synced)}`,
    `Pendientes: ${display(report.totals.calls_pending)}`,
    `Errores: ${display(report.totals.calls_error)}`,
    `Sincronización: ${report.sync_percentage === null ? "Sin datos" : `${report.sync_percentage}%`}`,
    `Dispositivos OK: ${report.devices.ok}`,
    `Dispositivos con advertencias: ${report.devices.warnings}`,
    `Dispositivos con errores: ${report.devices.errors}`,
    "",
    "DISPOSITIVOS QUE REQUIEREN ATENCIÓN",
  ];
  if (!visibleAttention.length) lines.push("No hay dispositivos que requieran atención.");
  for (const item of visibleAttention) {
    lines.push(item.seller_name, item.reason);
  }
  if (omittedCount > 0) {
    lines.push(`${omittedCount} dispositivos adicionales omitidos en este correo.`);
  }

  const rows: Array<[string, string | number]> = [
    ["Dispositivos monitoreados", report.devices.monitored],
    ["Dispositivos que reportaron", report.devices.reported],
    ["Llamadas detectadas", display(report.totals.calls_detected)],
    ["Sincronizadas", display(report.totals.calls_synced)],
    ["Pendientes", display(report.totals.calls_pending)],
    ["Errores", display(report.totals.calls_error)],
    ["Sincronización", report.sync_percentage === null ? "Sin datos" : `${report.sync_percentage}%`],
    ["Dispositivos OK", report.devices.ok],
    ["Dispositivos con advertencias", report.devices.warnings],
    ["Dispositivos con errores", report.devices.errors],
  ];
  const rowsHtml = rows.map(([label, value]) =>
    `<tr><td style="padding:6px 12px 6px 0;color:#5b5b63">${escapeHtml(label)}</td><td style="padding:6px 0"><strong>${escapeHtml(value)}</strong></td></tr>`
  ).join("");
  const attentionHtml = visibleAttention.length
    ? `<ul>${visibleAttention.map((item) =>
      `<li><strong>${escapeHtml(item.seller_name)}</strong><br>${escapeHtml(item.reason)}</li>`
    ).join("")}</ul>`
    : "<p>No hay dispositivos que requieran atención.</p>";
  const omittedHtml = omittedCount > 0
    ? `<p>${omittedCount} dispositivos adicionales omitidos en este correo.</p>`
    : "";
  const body_html = `<div style="font-family:Arial,sans-serif;color:#25252a;max-width:600px"><h2 style="color:#c21870;margin-bottom:4px">MAIPÚ CALL SYNC</h2><p style="margin-top:0">Resumen diario · ${escapeHtml(report.report_date)}</p><table>${rowsHtml}</table><h3 style="margin-top:24px">DISPOSITIVOS QUE REQUIEREN ATENCIÓN</h3>${attentionHtml}${omittedHtml}<p style="font-size:12px;color:#777">La ausencia de un reporte reciente no permite determinar la causa.</p></div>`;

  return { subject, body_text: lines.join("\n"), body_html };
};