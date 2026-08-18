import type { DataMode } from "@/lib/data-mode";

export type MarketInstrument = {
  symbol: string;
  name: string;
  price: string | null;
  changePercent: string | null;
};

export type AnalystCallSummary = {
  id: string;
  eventTime: string;
  institution: string;
  asset: string;
  direction: "BULLISH" | "NEUTRAL" | "BEARISH";
  previousTarget: string | null;
  target: string | null;
  sourceTitle: string;
};

export type DashboardSnapshot = {
  asOf: string;
  dataMode: DataMode;
  source: string;
  instruments: MarketInstrument[];
  calls: AnalystCallSummary[];
};

export interface MarketProvider {
  dashboard(): Promise<DashboardSnapshot>;
}
