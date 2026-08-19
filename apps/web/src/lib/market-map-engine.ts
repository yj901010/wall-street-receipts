import type { MarketMapCell, MarketMapMetric } from "@/lib/providers/market-map-provider";

export type MarketMapMetricTone = "negative" | "neutral" | "positive" | "unavailable";

export type MarketMapCellPresentation = {
  metricDisplay: string;
  metricTone: MarketMapMetricTone;
  sectorDisplay: string;
};

export function presentMarketMapCell(
  cell: MarketMapCell,
  metric: MarketMapMetric,
): MarketMapCellPresentation {
  if (cell.metric === null) {
    return {
      metricDisplay: metric.missingDisplay,
      metricTone: "unavailable",
      sectorDisplay: cell.sector ?? "NA",
    };
  }

  return {
    metricDisplay: String(cell.metric),
    metricTone: cell.metric > 0 ? "positive" : cell.metric < 0 ? "negative" : "neutral",
    sectorDisplay: cell.sector ?? "NA",
  };
}
