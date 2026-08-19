import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MarketTreemap } from "@/components/market-treemap";
import { marketTreemapProvider } from "@/lib/providers";
import MarketMapError from "./error";
import MarketMapLoading from "./loading";
import MarketMapNotFound from "./not-found";
import MarketMapPage, { generateStaticParams, readMarketMapRouteMode } from "./page";

function page(universe: string, mode?: string | string[]) {
  return MarketMapPage({
    params: Promise.resolve({ universe }),
    searchParams: Promise.resolve({ mode }),
  });
}

describe("MarketMapPage", () => {
  it("SSR-renders PRICE_CHANGE as the default nested S&P 500 DEMO mode", async () => {
    render(await page("sp500"));

    expect(screen.getByRole("heading", { name: "S&P 500 map evidence." })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Maps" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Price change" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Analyst consensus" })).not.toHaveAttribute(
      "aria-current",
    );

    const provenance = screen.getByLabelText("S&P 500 map provenance");
    expect(within(provenance).getByText("fixture-market-treemap-sp500-v1")).toBeInTheDocument();
    expect(within(provenance).getByText("DEMO")).toBeInTheDocument();

    expect(screen.getByText("3-cell DEMO sample")).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent(
      "This committed fixture demonstrates 1 outer sector and 3 nested industries",
    );
    expect(screen.getByRole("note")).toHaveTextContent(
      "It is a synthetic proxy, never an official or current market-cap value",
    );
    const definition = screen.getByLabelText("S&P 500 treemap definition");
    expect(within(definition).getByText("PRICE_CHANGE")).toBeInTheDocument();
    expect(within(definition).getByText("priceChangePercent")).toBeInTheDocument();
    expect(within(definition).getByText("percent")).toBeInTheDocument();
    expect(within(definition).getByText("sector → industry")).toBeInTheDocument();
    expect(within(definition).getByText("SYNTHETIC_MARKET_CAP_PROXY")).toBeInTheDocument();

    expect(screen.getByLabelText(/palette saturates at -5% and \+5%/i)).toHaveTextContent(
      "displayed values are never clamped",
    );

    const cells = screen.getByRole("list", { name: "S&P 500 nested DEMO treemap cells" });
    expect(within(cells).getAllByRole("article")).toHaveLength(3);

    const nvda = within(cells).getByRole("article", { name: "NVDA treemap evidence: +1.25%" });
    expect(nvda).toHaveAttribute("tabindex", "0");
    expect(nvda).toHaveClass("treemap-metric-positive");
    expect(nvda.closest("li")).toHaveAttribute("data-proxy", "144");
    nvda.focus();
    expect(nvda).toHaveFocus();
    expect(within(nvda).getByRole("tooltip")).toHaveTextContent("Semiconductors");
    expect(within(nvda).getByRole("tooltip")).toHaveTextContent("144 relative units");
    expect(within(nvda).getByRole("tooltip")).toHaveTextContent(
      "fixture-market-treemap-sp500-v1",
    );

    const aapl = within(cells).getByRole("article", { name: "AAPL treemap evidence: NA" });
    expect(aapl).toHaveClass("treemap-metric-unavailable");
    expect(within(aapl).getAllByText("NA").length).toBeGreaterThan(0);
    expect(aapl).not.toHaveClass("treemap-metric-positive");
    expect(aapl).not.toHaveClass("treemap-metric-negative");

    expect(within(cells).queryByRole("link")).not.toBeInTheDocument();
    expect(document.querySelector('a[href^="/stocks/"]')).toBeNull();
    expect(screen.getByText(/no drilldown link is shown/i)).toBeInTheDocument();
    expect(document.querySelector(".treemap-disclaimer")).toHaveTextContent(
      "Every grouping label, market-cap proxy, and non-null price-change value is synthetic; null remains NA.",
    );
  });

  it("preserves the legacy ANALYST_CONSENSUS surface behind an explicit mode link", async () => {
    render(await page("sp500", "analyst-consensus"));

    expect(screen.getByRole("link", { name: "Analyst consensus" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "Price change" })).not.toHaveAttribute("aria-current");
    expect(screen.getByLabelText("S&P 500 map provenance")).toHaveTextContent(
      "fixture-market-map-v1",
    );
    expect(screen.getByRole("heading", { name: "S&P 500 analyst-consensus sample" }))
      .toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "S&P 500 price-change treemap" }))
      .not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Nasdaq 100" })).toHaveAttribute(
      "href",
      "/maps/nasdaq100?mode=analyst-consensus",
    );
  });

  it("uses distinct canonical Nasdaq treemap provenance while preserving price-change mode", async () => {
    render(await page("nasdaq100", "price-change"));

    expect(screen.getByRole("heading", { name: "Nasdaq 100 price-change treemap" }))
      .toBeInTheDocument();
    expect(screen.getByLabelText("Nasdaq 100 map provenance")).toHaveTextContent(
      "fixture-market-treemap-nasdaq100-v1",
    );
    expect(screen.getByRole("link", { name: "S&P 500" })).toHaveAttribute("href", "/maps/sp500");
  });

  it("renders an explicit empty treemap without inventing groups, cells, or geometry", async () => {
    const canonical = await marketTreemapProvider().findByUniverse("sp500");
    render(
      <MarketTreemap
        snapshot={{
          ...canonical,
          coverage: { ...canonical.coverage, cellCount: 0 },
          cells: [],
          disclaimer: "Explicit known-empty DEMO test fixture.",
        }}
      />,
    );

    expect(screen.getByRole("status")).toHaveTextContent(
      "No sector, industry, ticker, proxy area, or price-change value was inferred",
    );
    expect(screen.queryByLabelText(/nested DEMO treemap cells/i)).not.toBeInTheDocument();
    expect(document.querySelector(".treemap-canvas")).toBeNull();
    expect(screen.getByText("Explicit known-empty DEMO test fixture.")).toBeInTheDocument();
  });

  it("keeps subpixel-proxy evidence in a non-geometric keyboard index", async () => {
    const canonical = await marketTreemapProvider().findByUniverse("sp500");
    render(
      <MarketTreemap
        snapshot={{
          ...canonical,
          coverage: { ...canonical.coverage, cellCount: 2 },
          cells: [
            { ...canonical.cells[0], syntheticMarketCapProxy: 1_000_000_000_000 },
            { ...canonical.cells[1], syntheticMarketCapProxy: 1 },
          ],
        }}
      />,
    );

    const tinyCell = screen.getByRole("article", { name: "MSFT treemap evidence: -0.75%" });
    expect(tinyCell).toHaveClass("treemap-label-hidden");

    const summary = screen.getByText("Accessible evidence index · 2 cells");
    summary.focus();
    expect(summary).toHaveFocus();
    fireEvent.click(summary);
    expect(summary.closest("details")).toHaveAttribute("open");

    const index = screen.getByRole("table", { name: "S&P 500 accessible treemap evidence index" });
    expect(within(index).getByText("asset-msft")).toBeInTheDocument();
    expect(within(index).getByText("MSFT")).toBeInTheDocument();
    expect(within(index).getByText("Software")).toBeInTheDocument();
    expect(within(index).getByText("-0.75%")).toBeInTheDocument();
    expect(within(index).getByText("1 relative units")).toBeInTheDocument();
    expect(screen.getByText(/non-geometric index preserves every stored field/i)).toBeInTheDocument();
  });

  it("renders an out-of-palette raw value exactly while saturating color only", async () => {
    const canonical = await marketTreemapProvider().findByUniverse("sp500");
    render(
      <MarketTreemap
        snapshot={{
          ...canonical,
          coverage: { ...canonical.coverage, cellCount: 1 },
          cells: [{ ...canonical.cells[0], priceChangePercent: -7.25 }],
        }}
      />,
    );

    const cell = screen.getByRole("article", { name: "NVDA treemap evidence: -7.25%" });
    expect(cell).toHaveClass("treemap-metric-negative");
    expect(cell).toHaveStyle({ backgroundColor: "rgb(138, 52, 56)" });
    expect(cell.querySelector(".treemap-cell-copy")).toHaveTextContent("-7.25%");
    expect(cell.querySelector(".treemap-cell-copy")).not.toHaveTextContent("-5%");
  });

  it("fails closed for unsupported and non-scalar modes", () => {
    expect(readMarketMapRouteMode(undefined)).toBe("price-change");
    expect(readMarketMapRouteMode("price-change")).toBe("price-change");
    expect(readMarketMapRouteMode("analyst-consensus")).toBe("analyst-consensus");
    expect(readMarketMapRouteMode("live")).toBeNull();
    expect(readMarketMapRouteMode(["price-change", "analyst-consensus"])).toBeNull();
  });

  it("returns not-found behavior rather than fallback data for invalid modes", async () => {
    await expect(page("sp500", "live")).rejects.toThrow(/404/);
    await expect(page("sp500", ["price-change", "analyst-consensus"])).rejects.toThrow(/404/);
  });

  it("publishes only the two canonical universe routes", () => {
    expect(generateStaticParams()).toEqual([
      { universe: "sp500" },
      { universe: "nasdaq100" },
    ]);
  });

  it("provides accessible mode-neutral loading and retryable error states", () => {
    const { unmount } = render(<MarketMapLoading />);

    expect(screen.getByText("Loading the DEMO map evidence…").closest("div")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(screen.getByText(/Reading the selected mode/i)).toBeInTheDocument();
    unmount();

    const reset = vi.fn();
    render(<MarketMapError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "No cell, geometry, weight, metric, or universe membership was inferred.",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(reset).toHaveBeenCalledOnce();
  });

  it("uses a mode-neutral not-found state without substituting another map", () => {
    render(<MarketMapNotFound />);

    expect(screen.getByText("Unsupported map request")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "This market map is not published." }))
      .toBeInTheDocument();
    expect(screen.getByText(/No data from another universe or mode was substituted/i))
      .toBeInTheDocument();
  });
});
