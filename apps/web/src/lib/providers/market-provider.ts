import type { DataMode } from "@/lib/data-mode";
import type { AnalystCallView } from "./calls-provider";
import type { MarketTreemapSnapshot } from "./market-treemap-provider";

export type DashboardLatestCalls = {
  items: readonly AnalystCallView[];
  asOf: string;
  dataMode: DataMode;
  source: string;
  disclaimer: string;
};

export type DashboardUnavailableSection = {
  status: "NOT_PUBLISHED";
  missingDisplay: "NA";
};

export type DashboardDeferredSection = {
  status: "P3_DEFERRED";
  missingDisplay: "NA";
};

export type DashboardSnapshot = {
  dataMode: DataMode;
  latestCalls: DashboardLatestCalls;
  mapPreviews: readonly [MarketTreemapSnapshot, MarketTreemapSnapshot];
  marketBoard: DashboardUnavailableSection;
  eventCalendar: DashboardUnavailableSection;
  ranking: DashboardDeferredSection;
};

export interface MarketProvider {
  dashboard(): Promise<DashboardSnapshot>;
}
