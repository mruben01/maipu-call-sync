export const dailySummaryDedupeKey = (reportDate: string, email: string): string =>
  `daily:${reportDate}:${email.toLowerCase()}`;

export const emailDeliveryStatus = (
  enabled: boolean,
  providerApproved: boolean,
): "queued" | "disabled" =>
  enabled && providerApproved ? "queued" : "disabled";