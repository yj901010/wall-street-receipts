import type { MarketProvider } from "./market-provider";
import { FixtureMarketProvider } from "./fixture-market-provider";
import type { MethodologyProvider } from "./methodology-provider";
import { FixtureMethodologyProvider } from "./fixture-methodology-provider";
import type { MarketMapProvider } from "./market-map-provider";
import { FixtureMarketMapProvider } from "./fixture-market-map-provider";

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
export { FixtureMarketMapProvider } from "./fixture-market-map-provider";
export {
  isMarketMapUniverse,
  MARKET_MAP_UNIVERSES,
} from "./market-map-provider";
export type {
  MarketMapCell,
  MarketMapCoverage,
  MarketMapMetric,
  MarketMapMode,
  MarketMapProvider,
  MarketMapSnapshot,
  MarketMapUniverse,
} from "./market-map-provider";

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

export function marketMapProvider(): MarketMapProvider {
  const configuredProvider = process.env.MARKET_MAP_PROVIDER?.toLocaleLowerCase("en-US") ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported market-map provider: ${configuredProvider}`);
  }

  return new FixtureMarketMapProvider();
}
