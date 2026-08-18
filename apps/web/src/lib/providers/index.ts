import type { MarketProvider } from "./market-provider";
import { FixtureMarketProvider } from "./fixture-market-provider";

export function marketProvider(): MarketProvider {
  const configuredProvider = process.env.MARKET_PROVIDER?.toLowerCase() ?? "fixture";

  if (configuredProvider !== "fixture") {
    throw new Error(`Unsupported market provider: ${configuredProvider}`);
  }

  return new FixtureMarketProvider();
}
