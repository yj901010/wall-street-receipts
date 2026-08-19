import type { MarketProvider } from "./market-provider";
import { FixtureMarketProvider } from "./fixture-market-provider";
import { callsProvider as createCallsProvider } from "./fixture-calls-provider";
import type { MethodologyProvider } from "./methodology-provider";
import { FixtureMethodologyProvider } from "./fixture-methodology-provider";
import type { MarketMapProvider } from "./market-map-provider";
import { FixtureMarketMapProvider } from "./fixture-market-map-provider";
import type { MarketTreemapProvider } from "./market-treemap-provider";
import { FixtureMarketTreemapProvider } from "./fixture-market-treemap-provider";
import type { InstitutionDirectoryProvider } from "./institution-directory-provider";
import { FixtureInstitutionDirectoryProvider } from "./fixture-institution-directory-provider";

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
export { FixtureInstitutionDirectoryProvider } from "./fixture-institution-directory-provider";
export type {
  InstitutionDirectoryIdentity,
  InstitutionDirectoryProvider,
  InstitutionDirectoryProvenance,
  InstitutionDirectorySnapshot,
} from "./institution-directory-provider";
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
  DashboardDeferredSection,
  DashboardLatestCalls,
  DashboardSnapshot,
  DashboardUnavailableSection,
  MarketProvider,
} from "./market-provider";
export { FixtureMarketTreemapProvider } from "./fixture-market-treemap-provider";
export {
  isMarketTreemapUniverse,
  MARKET_TREEMAP_UNIVERSES,
} from "./market-treemap-provider";
export type {
  MarketTreemapCell,
  MarketTreemapCoverage,
  MarketTreemapGeometry,
  MarketTreemapMetric,
  MarketTreemapMode,
  MarketTreemapProvider,
  MarketTreemapProvenance,
  MarketTreemapSnapshot,
  MarketTreemapUniverse,
} from "./market-treemap-provider";
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

  return new FixtureMarketProvider(createCallsProvider(), marketTreemapProvider());
}

export function methodologyProvider(): MethodologyProvider {
  const configuredProvider = process.env.METHODOLOGY_PROVIDER?.toLocaleLowerCase("en-US") ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported methodology provider: ${configuredProvider}`);
  }

  return new FixtureMethodologyProvider();
}

export function institutionDirectoryProvider(): InstitutionDirectoryProvider {
  const configuredProvider = process.env.INSTITUTION_DIRECTORY_PROVIDER?.toLowerCase() ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported institution-directory provider: ${configuredProvider}`);
  }

  return new FixtureInstitutionDirectoryProvider();
}

export function marketMapProvider(): MarketMapProvider {
  const configuredProvider = process.env.MARKET_MAP_PROVIDER?.toLocaleLowerCase("en-US") ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported market-map provider: ${configuredProvider}`);
  }

  return new FixtureMarketMapProvider();
}

export function marketTreemapProvider(): MarketTreemapProvider {
  const configuredProvider = process.env.MARKET_TREEMAP_PROVIDER?.toLocaleLowerCase("en-US") ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported market-treemap provider: ${configuredProvider}`);
  }

  return new FixtureMarketTreemapProvider();
}
