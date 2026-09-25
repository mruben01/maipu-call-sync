export type DeviceReportDay = {
  device_id: string;
  report_date: string;
};

export const reportedDeviceIdsForDate = (
  receipts: DeviceReportDay[],
  reportDate: string,
): Set<string> =>
  new Set(
    receipts
      .filter((receipt) => receipt.report_date === reportDate)
      .map((receipt) => receipt.device_id),
  );

export const localCalendarDate = (date: Date, timeZone: string): string => {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
};