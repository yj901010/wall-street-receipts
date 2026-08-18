import { readDataMode } from "@/lib/data-mode";
import type { DashboardSnapshot, MarketProvider } from "./market-provider";

export class FixtureMarketProvider implements MarketProvider {
  async dashboard(): Promise<DashboardSnapshot> {
    return {
      asOf: "2026-08-12T06:00:00Z",
      dataMode: readDataMode("DEMO"),
      source: "Versioned local fixture v1",
      instruments: [
        { symbol: "SPX", name: "S&P 500", price: "5,278.52", changePercent: "+0.63%" },
        { symbol: "NDX", name: "Nasdaq 100", price: "18,752.34", changePercent: "+0.78%" },
        { symbol: "VIX", name: "Cboe VIX", price: "13.72", changePercent: "-2.01%" },
        { symbol: "DXY", name: "US Dollar", price: null, changePercent: null },
      ],
      calls: [
        {
          id: "demo-call-001",
          eventTime: "2026-08-10T12:00:00Z",
          institution: "JPMorgan",
          asset: "SPX",
          direction: "BULLISH",
          previousTarget: "7,800",
          target: "8,000",
          sourceTitle: "DEMO index outlook",
        },
        {
          id: "demo-call-002",
          eventTime: "2026-08-11T14:20:00Z",
          institution: "Goldman Sachs",
          asset: "NVDA",
          direction: "BULLISH",
          previousTarget: "210",
          target: "235",
          sourceTitle: "DEMO equity interview",
        },
      ],
    };
  }
}
