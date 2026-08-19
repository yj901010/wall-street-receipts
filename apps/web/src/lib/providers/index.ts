import type { MarketProvider } from "./market-provider";
import { FixtureMarketProvider } from "./fixture-market-provider";
import type { Sp500HistoryProvider } from "./sp500-history-provider";
import { FixtureSp500HistoryProvider } from "./fixture-sp500-history-provider";
import type { MarketBoardProvider } from "./market-board-provider";
import { FixtureMarketBoardProvider } from "./fixture-market-board-provider";
import { callsProvider as createCallsProvider } from "./fixture-calls-provider";
import type { MethodologyProvider } from "./methodology-provider";
import { FixtureMethodologyProvider } from "./fixture-methodology-provider";
import type { MarketMapProvider } from "./market-map-provider";
import { FixtureMarketMapProvider } from "./fixture-market-map-provider";
import type { MarketTreemapProvider } from "./market-treemap-provider";
import { FixtureMarketTreemapProvider } from "./fixture-market-treemap-provider";
import type { InstitutionDirectoryProvider } from "./institution-directory-provider";
import { FixtureInstitutionDirectoryProvider } from "./fixture-institution-directory-provider";
import type { AnalystDirectoryProvider } from "./analyst-directory-provider";
import { FixtureAnalystDirectoryProvider } from "./fixture-analyst-directory-provider";

export { callsProvider, FixtureCallsProvider } from "./fixture-calls-provider";
export { FixtureSp500HistoryProvider } from "./fixture-sp500-history-provider";
export type {
  Sp500HistoryProvider,
  Sp500HistorySnapshot,
} from "./sp500-history-provider";
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
export { FixtureAnalystDirectoryProvider } from "./fixture-analyst-directory-provider";
export type {
  AnalystDirectoryIdentity,
  AnalystDirectoryProvider,
  AnalystDirectoryProvenance,
  AnalystDirectorySnapshot,
} from "./analyst-directory-provider";
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
export { FixtureMarketBoardProvider } from "./fixture-market-board-provider";
export type {
  MarketBoardProvider,
  MarketBoardProvenance,
  MarketBoardSnapshot,
} from "./market-board-provider";
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

  return new FixtureMarketProvider(
    createCallsProvider(),
    marketTreemapProvider(),
    marketBoardProvider(),
  );
}

export function sp500HistoryProvider(): Sp500HistoryProvider {
  const configuredProvider = process.env.SP500_HISTORY_PROVIDER?.toLowerCase() ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported S&P 500 history provider: ${configuredProvider}`);
  }

  return new FixtureSp500HistoryProvider(createCallsProvider());
}

export function marketBoardProvider(): MarketBoardProvider {
  const configuredProvider = process.env.MARKET_BOARD_PROVIDER?.toLowerCase() ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported market-board provider: ${configuredProvider}`);
  }

  return new FixtureMarketBoardProvider();
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

export function analystDirectoryProvider(): AnalystDirectoryProvider {
  const configuredProvider = process.env.ANALYST_DIRECTORY_PROVIDER?.toLowerCase() ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported analyst-directory provider: ${configuredProvider}`);
  }

  return new FixtureAnalystDirectoryProvider();
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
