import type { MarketProvider } from "./market-provider";
import { FixtureMarketProvider } from "./fixture-market-provider";
import type { MethodologyProvider } from "./methodology-provider";
import { FixtureMethodologyProvider } from "./fixture-methodology-provider";

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
export { FixtureMethodologyProvider } from "./fixture-methodology-provider";
export type {
  MethodologyCatalog,
  MethodologyProvider,
  MethodologyStatus,
  ScoringMethodology,
} from "./methodology-provider";

export function marketProvider(): MarketProvider {
  const configuredProvider = process.env.MARKET_PROVIDER?.toLowerCase() ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported market provider: ${configuredProvider}`);
  }

  return new FixtureMarketProvider();
}

export function methodologyProvider(): MethodologyProvider {
  const configuredProvider = process.env.METHODOLOGY_PROVIDER?.toLocaleLowerCase("en-US") ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported methodology provider: ${configuredProvider}`);
  }

  return new FixtureMethodologyProvider();
}
