import type { MarketProvider } from "./market-provider";
import { FixtureMarketProvider } from "./fixture-market-provider";

export { callsProvider, FixtureCallsProvider } from "./fixture-calls-provider";
export type {
  AnalystCall,
  AnalystCallDetail,
  AnalystCallPage,
  AnalystCallView,
  CallDirection,
  CallContext,
  CallSortField,
  CallStatus,
  CallsMetadata,
  CallsProvider,
  CallsQuery,
  EventContext,
  MacroObservation,
  MacroSeries,
  MacroSnapshot,
  MacroUnit,
  MarketSnapshot,
  SourceEvidence,
} from "./calls-provider";

export function marketProvider(): MarketProvider {
  const configuredProvider = process.env.MARKET_PROVIDER?.toLowerCase() ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported market provider: ${configuredProvider}`);
  }

  return new FixtureMarketProvider();
}
