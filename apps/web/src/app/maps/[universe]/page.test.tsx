import { fireEvent, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MarketTreemap } from "@/components/market-treemap";
import { presentMarketTreemapCell } from "@/lib/market-treemap-engine";
import { marketTreemapProvider } from "@/lib/providers";
import { renderWithLocale } from "@/test/render-with-locale";
import MarketMapError from "./error";
import MarketMapLoading from "./loading";
import MarketMapNotFound from "./not-found";
import MarketMapPage, { generateStaticParams, readMarketMapRouteMode } from "./page";

vi.mock("@/lib/i18n/server", () => ({
  getLocale: vi.fn(async () => "ko"),
}));

function page(universe: string, mode?: string | string[]) {
  return MarketMapPage({
    params: Promise.resolve({ universe }),
    searchParams: Promise.resolve({ mode }),
  });
}

describe("MarketMapPage", () => {
  it("SSR-renders PRICE_CHANGE as the default nested S&P 500 DEMO mode", async () => {
    renderWithLocale(await page("sp500"));

    expect(screen.getByRole("heading", { name: "S&P 500 지도 증거." })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "시장 지도" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "가격 변동" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "애널리스트 컨센서스" })).not.toHaveAttribute(
      "aria-current",
    );

    const provenance = screen.getByLabelText("S&P 500 지도 출처");
    expect(within(provenance).getByText("fixture-market-treemap-sp500-v1")).toBeInTheDocument();
    expect(within(provenance).getByText("DEMO")).toBeInTheDocument();

    expect(screen.getByText("DEMO 셀 표본 3개")).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent(
      "커밋된 이 픽스처는 외부 섹터 1개와 중첩 산업 3개를 시연합니다",
    );
    expect(screen.getByRole("note")).toHaveTextContent(
      "이는 합성 프록시이며 공식 또는 현재 시가총액 값이 아닙니다",
    );
    const definition = screen.getByLabelText("S&P 500 트리맵 정의");
    expect(within(definition).getByText("PRICE_CHANGE")).toBeInTheDocument();
    expect(within(definition).getByText("priceChangePercent")).toBeInTheDocument();
    expect(within(definition).getByText("percent")).toBeInTheDocument();
    expect(within(definition).getByText("sector → industry")).toBeInTheDocument();
    expect(within(definition).getByText("SYNTHETIC_MARKET_CAP_PROXY")).toBeInTheDocument();

    expect(screen.getByLabelText(/-5%.*\+5%/i)).toHaveTextContent(
      "표시값은 제한하지 않습니다",
    );

    const cells = screen.getByRole("list", { name: "S&P 500 중첩 DEMO 트리맵 셀" });
    expect(within(cells).getAllByRole("article")).toHaveLength(3);

    const nvda = within(cells).getByRole("article", { name: "NVDA 트리맵 증거: +1.25%" });
    expect(nvda).toHaveAttribute("tabindex", "0");
    expect(nvda).toHaveClass("treemap-metric-positive");
    expect(nvda.closest("li")).toHaveAttribute("data-proxy", "144");
    nvda.focus();
    expect(nvda).toHaveFocus();
    expect(within(nvda).getByRole("tooltip")).toHaveTextContent("Semiconductors");
    expect(within(nvda).getByRole("tooltip")).toHaveTextContent("144 상대 단위");
    expect(within(nvda).getByRole("tooltip")).toHaveTextContent(
      "fixture-market-treemap-sp500-v1",
    );

    const aapl = within(cells).getByRole("article", { name: "AAPL 트리맵 증거: NA" });
    expect(aapl).toHaveClass("treemap-metric-unavailable");
    expect(within(aapl).getAllByText("NA").length).toBeGreaterThan(0);
    expect(aapl).not.toHaveClass("treemap-metric-positive");
    expect(aapl).not.toHaveClass("treemap-metric-negative");

    expect(within(cells).queryByRole("link")).not.toBeInTheDocument();
    expect(document.querySelector('a[href^="/stocks/"]')).toBeNull();
    expect(screen.getByText(/상세 이동 링크를 표시하지 않습니다/i)).toBeInTheDocument();
    expect(document.querySelector(".treemap-disclaimer")).toHaveTextContent(
      "Every grouping label, market-cap proxy, and non-null price-change value is synthetic; null remains NA.",
    );
  });

  it("preserves the legacy ANALYST_CONSENSUS surface behind an explicit mode link", async () => {
    renderWithLocale(await page("sp500", "analyst-consensus"));

    expect(screen.getByRole("link", { name: "애널리스트 컨센서스" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "가격 변동" })).not.toHaveAttribute("aria-current");
    expect(screen.getByLabelText("S&P 500 지도 출처")).toHaveTextContent(
      "fixture-market-map-v1",
    );
    expect(screen.getByRole("heading", { name: "S&P 500 애널리스트 컨센서스 표본" }))
      .toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "S&P 500 가격 변동 트리맵" }))
      .not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Nasdaq 100" })).toHaveAttribute(
      "href",
      "/maps/nasdaq100?mode=analyst-consensus",
    );
  });

  it("uses distinct canonical Nasdaq treemap provenance while preserving price-change mode", async () => {
    renderWithLocale(await page("nasdaq100", "price-change"));

    expect(screen.getByRole("heading", { name: "Nasdaq 100 가격 변동 트리맵" }))
      .toBeInTheDocument();
    expect(screen.getByLabelText("Nasdaq 100 지도 출처")).toHaveTextContent(
      "fixture-market-treemap-nasdaq100-v1",
    );
    expect(screen.getByRole("link", { name: "S&P 500" })).toHaveAttribute("href", "/maps/sp500");
  });

  it("renders an explicit empty treemap without inventing groups, cells, or geometry", async () => {
    const canonical = await marketTreemapProvider().findByUniverse("sp500");
    renderWithLocale(
      <MarketTreemap
        locale="ko"
        snapshot={{
          ...canonical,
          coverage: { ...canonical.coverage, cellCount: 0 },
          cells: [],
          disclaimer: "Explicit known-empty DEMO test fixture.",
        }}
      />,
    );

    expect(screen.getByRole("status")).toHaveTextContent(
      "섹터, 산업, 티커, 프록시 면적 또는 가격 변동 값을 추론하지 않았으며",
    );
    expect(screen.queryByLabelText(/중첩 DEMO 트리맵 셀/i)).not.toBeInTheDocument();
    expect(document.querySelector(".treemap-canvas")).toBeNull();
    expect(screen.getByText("Explicit known-empty DEMO test fixture.")).toBeInTheDocument();
  });

  it("keeps subpixel-proxy evidence in a non-geometric keyboard index", async () => {
    const canonical = await marketTreemapProvider().findByUniverse("sp500");
    renderWithLocale(
      <MarketTreemap
        locale="ko"
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

    const tinyCell = screen.getByRole("article", { name: "MSFT 트리맵 증거: -0.75%" });
    expect(tinyCell).toHaveClass("treemap-label-hidden");

    const summary = screen.getByText("접근 가능한 증거 인덱스 · 셀 2개");
    summary.focus();
    expect(summary).toHaveFocus();
    fireEvent.click(summary);
    expect(summary.closest("details")).toHaveAttribute("open");

    const index = screen.getByRole("table", { name: "S&P 500 접근 가능한 트리맵 증거 인덱스" });
    expect(within(index).getByText("asset-msft")).toBeInTheDocument();
    expect(within(index).getByText("MSFT")).toBeInTheDocument();
    expect(within(index).getByText("Software")).toBeInTheDocument();
    expect(within(index).getByText("-0.75%")).toBeInTheDocument();
    expect(within(index).getByText("1 상대 단위")).toBeInTheDocument();
    expect(screen.getByText(/비기하학 인덱스는.*저장된 모든 필드를 보존/i)).toBeInTheDocument();
  });

  it("renders an out-of-palette raw value exactly while saturating color only", async () => {
    const canonical = await marketTreemapProvider().findByUniverse("sp500");
    renderWithLocale(
      <MarketTreemap
        locale="ko"
        snapshot={{
          ...canonical,
          coverage: { ...canonical.coverage, cellCount: 1 },
          cells: [{ ...canonical.cells[0], priceChangePercent: -7.25 }],
        }}
      />,
    );

    const cell = screen.getByRole("article", { name: "NVDA 트리맵 증거: -7.25%" });
    expect(cell).toHaveClass("treemap-metric-negative");
    expect(cell).toHaveStyle({ backgroundColor: "rgb(221, 188, 188)" });
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

  it("provides accessible mode-neutral loading and retryable error states", async () => {
    const { unmount } = renderWithLocale(await MarketMapLoading());

    expect(screen.getByText("DEMO 지도 증거를 불러오는 중…").closest("div")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(screen.getByText(/선택한 모드, 커버리지, 시각과 출처/i)).toBeInTheDocument();
    unmount();

    const reset = vi.fn();
    renderWithLocale(<MarketMapError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "셀, 도형, 가중치, 지표 또는 유니버스 편입을 추론하지 않습니다.",
    );
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(reset).toHaveBeenCalledOnce();
  });

  it("uses a mode-neutral not-found state without substituting another map", async () => {
    renderWithLocale(await MarketMapNotFound());

    expect(screen.getByText("지원하지 않는 지도 요청")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "이 시장 지도는 게시되지 않았습니다." }))
      .toBeInTheDocument();
    expect(screen.getByText(/다른 유니버스나 모드의 데이터로 대체하지 않았습니다/i))
      .toBeInTheDocument();
  });

  it("renders English labels with identical treemap geometry and evidence values", async () => {
    const canonical = await marketTreemapProvider().findByUniverse("sp500");
    const { container } = renderWithLocale(
      <MarketTreemap snapshot={canonical} locale="en" />,
      "en",
    );

    expect(screen.getByRole("heading", { name: "S&P 500 price-change treemap" }))
      .toBeInTheDocument();
    const nvdaPresentation = presentMarketTreemapCell(canonical.cells[0], canonical.metric);
    expect(screen.getByRole("article", { name: "NVDA treemap evidence: +1.25%" }))
      .toHaveStyle({ backgroundColor: nvdaPresentation.backgroundColor });
    expect(container.querySelector('[data-proxy="144"]')).toHaveAttribute("data-rect-width");
    expect(screen.getAllByText("144 relative units").length).toBeGreaterThan(0);
    expect(screen.getAllByText("2026-08-19 09:30:00 KST").length).toBeGreaterThan(0);
  });
});
