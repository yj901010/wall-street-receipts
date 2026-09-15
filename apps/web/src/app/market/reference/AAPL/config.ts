export const WIDGET_SCRIPT = "https://vunelix.com/assets/bundles/js/widgets/charts/vunelix-symbol-overview.js?v=4.9.5";
export const WIDGET_DOCUMENTATION = "https://vunelix.com/widgets/symbol-overview";
export const PILOT_PATH = "/market/reference/AAPL";
export const SCRIPT_TIMEOUT_MS = 15_000;

export type WidgetMode = "disabled" | "enabled" | "invalid";

// Deliberately not a general symbol mapper or a market-data provider.
export const widgetAttributes = {
  "data-symbol": "NASDAQ:AAPL",
  "data-theme": "light",
  "data-lang": "en",
  "data-show-full-name": "true",
  "data-show-exchange": "true",
  "data-show-market-status": "true",
  "data-show-extended-hours": "false",
  "data-show-update-time": "true",
  "data-show-change": "true",
  "data-chart-type": "line",
  "data-chart-height": "300",
  "data-ranges": "1d,1m,1y",
  "data-default-range": "1d",
  "data-animation-mode": "none",
  "data-remove-branding": "false",
} as const;

export function widgetMode(raw: string | undefined): WidgetMode {
  if (raw === undefined || raw === "disabled") return "disabled";
  return raw === "enabled" ? "enabled" : "invalid";
}
