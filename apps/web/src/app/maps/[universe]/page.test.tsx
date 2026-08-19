import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import MarketMapError from "./error";
import MarketMapLoading from "./loading";
import MarketMapPage, { generateStaticParams } from "./page";

describe("MarketMapPage", () => {
  it("renders the exact limited S&P 500 DEMO sample without index or ledger claims", async () => {
    render(await MarketMapPage({ params: Promise.resolve({ universe: "sp500" }) }));

    expect(screen.getByRole("heading", { name: "S&P 500 map evidence." })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Maps" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "S&P 500" })).toHaveAttribute("aria-current", "page");

    const provenance = screen.getByLabelText("S&P 500 map provenance");
    expect(within(provenance).getByText("fixture-market-map-v1")).toBeInTheDocument();
    expect(within(provenance).getByText("DEMO")).toBeInTheDocument();

    expect(screen.getByText("3-cell DEMO sample")).toBeInTheDocument();
    expect(screen.getByText(/limited DEMO sample — not a complete index map/i)).toBeInTheDocument();
    expect(screen.getByText(/completeUniverse is false/i)).toBeInTheDocument();
    expect(screen.getAllByText("SYNTHETIC_RELATIVE")).toHaveLength(2);
    expect(screen.getByRole("note")).toHaveTextContent(
      "On wide layouts, tile area uses SYNTHETIC_RELATIVE fixture weights",
    );
    expect(screen.getByRole("note")).toHaveTextContent(
      "Small screens stack the same cells for readability",
    );
    expect(screen.getByText(/not official index or market-cap weights/i)).toBeInTheDocument();
    const disclaimer = document.querySelector(".map-disclaimer");
    expect(disclaimer).toHaveTextContent(
      "Sector labels, weights, analyst-consensus metrics, and call counts are synthetic fixture values.",
    );
    expect(disclaimer).toHaveTextContent(
      "no metric or call count was observed or derived from canonical calls.",
    );

    const cells = screen.getByRole("list", { name: "S&P 500 limited DEMO sample cells" });
    expect(within(cells).getAllByRole("article")).toHaveLength(3);
    expect(within(cells).getByText("NVDA")).toBeInTheDocument();
    expect(within(cells).getByText("MSFT")).toBeInTheDocument();

    const aapl = within(cells).getByRole("article", { name: "AAPL map evidence" });
    expect(aapl).toHaveClass("map-metric-unavailable");
    expect(within(aapl).getByText("NA")).toBeInTheDocument();
    expect(within(aapl).getByText("0")).toBeInTheDocument();
    expect(aapl).not.toHaveClass("map-metric-positive");
    expect(aapl).not.toHaveClass("map-metric-negative");
    expect(within(cells).queryByRole("link")).not.toBeInTheDocument();
    expect(document.querySelector('a[href^="/stocks/"]')).toBeNull();
    expect(screen.getByText(/no drilldown link is shown/i)).toBeInTheDocument();
  });

  it("renders canonical Nasdaq 100 known-empty metadata without substituting cells", async () => {
    render(await MarketMapPage({ params: Promise.resolve({ universe: "nasdaq100" }) }));

    expect(screen.getByRole("heading", { name: "Nasdaq 100 map evidence." })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Nasdaq 100" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByText("0-cell DEMO sample")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "No membership, weight, metric, or call count was inferred",
    );
    expect(screen.getByRole("note")).toHaveTextContent(
      "no tile geometry or weight is rendered or inferred for this known-empty fixture",
    );
    expect(screen.queryByRole("list", { name: /limited DEMO sample cells/i })).not.toBeInTheDocument();
    expect(screen.queryByText("NVDA")).not.toBeInTheDocument();

    const definition = screen.getByLabelText("Nasdaq 100 map definition");
    expect(within(definition).getByText("analystConsensus")).toBeInTheDocument();
    expect(within(definition).getByText("score")).toBeInTheDocument();
    expect(screen.getByLabelText("Nasdaq 100 map provenance")).toHaveTextContent(
      "fixture-market-map-nasdaq100-v1",
    );
    expect(screen.getByText(/known-empty Nasdaq 100 DEMO SAMPLE/i)).toBeInTheDocument();
  });

  it("publishes only the two canonical universe routes", () => {
    expect(generateStaticParams()).toEqual([
      { universe: "sp500" },
      { universe: "nasdaq100" },
    ]);
  });

  it("provides accessible loading and retryable error states", () => {
    const { unmount } = render(<MarketMapLoading />);

    expect(screen.getByText("Loading the DEMO map sample…").closest("div")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    unmount();

    const reset = vi.fn();
    render(<MarketMapError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "No cell, weight, metric, or universe membership was inferred.",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
