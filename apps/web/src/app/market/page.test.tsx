import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import MarketError from "./error";
import MarketLoading from "./loading";
import MarketPage from "./page";

describe("MarketPage", () => {
  it("renders the exact known-unavailable DEMO publication state without quote facts", async () => {
    render(await MarketPage());

    expect(screen.getByRole("heading", {
      name: "A global market board is not published.",
    })).toBeInTheDocument();
    const marketLink = screen.getByRole("link", { name: "Market" });
    expect(marketLink).toHaveAttribute("aria-current", "page");
    expect(marketLink).toHaveAttribute("href", "/market");
    expect(screen.getByRole("link", { name: "Dashboard" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", {
      name: "Open recorded S&P 500 call-event history",
    })).toHaveAttribute("href", "/markets/sp500");

    const provenance = screen.getByLabelText("Market board fixture provenance");
    expect(within(provenance).getByText("1.0.0", { exact: true })).toBeInTheDocument();
    expect(within(provenance).getByText("v1", { exact: true })).toBeInTheDocument();
    expect(within(provenance).getByText("fixture-market-board-v1", { exact: true }))
      .toBeInTheDocument();
    expect(within(provenance).getByText("DEMO", { exact: true })).toBeInTheDocument();
    expect(within(provenance).getAllByText("Aug 19, 2026, 2:00 AM UTC", { exact: true }))
      .toHaveLength(2);
    expect(within(provenance).getByText("Policy generated", { exact: true }))
      .toBeInTheDocument();
    expect(within(provenance).getByText("Policy captured", { exact: true }))
      .toBeInTheDocument();

    const publication = screen.getByRole("region", {
      name: "Market board publication state",
    });
    expect(publication).toHaveAttribute("tabindex", "0");
    expect(within(publication).getByText("Not published", { exact: true }))
      .toBeInTheDocument();

    const policy = within(publication).getByLabelText("Market board publication policy");
    expect(within(policy).getByText("Publication policy · not market evidence", { exact: true }))
      .toBeVisible();
    expect(policy).toHaveTextContent("not a delayed, end-of-day, or current quote surface");
    expect(policy).toHaveTextContent("Call-event snapshots and synthetic map samples");
    expect(policy).toHaveTextContent("Missing values are never replaced with zero");

    const status = within(publication).getByLabelText("Known-unavailable market board status");
    expect(status).toHaveTextContent("NOT_PUBLISHED");
    expect(status).toHaveTextContent("GLOBAL_MARKET_OVERVIEW");
    expect(status).toHaveTextContent("NO_CANONICAL_GLOBAL_QUOTE_CATALOG");
    expect(within(status).getAllByText("NA", { exact: true })).toHaveLength(2);
    expect(status).toHaveTextContent("None published");

    const metadata = within(publication).getByLabelText("Market board policy metadata");
    expect(metadata).toHaveTextContent("not a market as-of time");
    expect(metadata).toHaveTextContent("LOCAL_SPECIFICATION");
    expect(metadata).toHaveTextContent("INTERNAL_DEMO");
    expect(metadata).toHaveTextContent("true");

    const paths = within(publication).getByLabelText("Market board source paths");
    const sourcePathItems = within(within(paths).getByRole("list")).getAllByRole("listitem");
    expect(sourcePathItems).toHaveLength(2);
    expect(sourcePathItems.map((item) => item.textContent)).toEqual([
      "schemas/market-board.schema.json",
      "quality/P2_ACCEPTANCE.md",
    ]);
    expect(within(publication).getByText(/Known-unavailable DEMO publication state only/))
      .toHaveTextContent("Not investment advice.");

    expect(within(publication).queryByRole("table")).not.toBeInTheDocument();
    expect(within(publication).queryByRole("row")).not.toBeInTheDocument();
    expect(screen.queryByText("5278.52")).not.toBeInTheDocument();
    expect(screen.queryByText("183.42")).not.toBeInTheDocument();
    expect(screen.queryByText("SPX", { exact: true })).not.toBeInTheDocument();
    expect(screen.queryByText("NVDA", { exact: true })).not.toBeInTheDocument();
  });

  it("keeps DEMO navigation and no-fallback copy in loading and recoverable error states", () => {
    const loading = render(<MarketLoading />);

    expect(screen.getByText("Loading the DEMO publication record…").closest("main"))
      .toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Market" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByText(/No quote, change, session status, freshness, or coverage/))
      .toBeInTheDocument();
    loading.unmount();

    const reset = vi.fn();
    render(<MarketError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "No partial quote, call-event snapshot, synthetic map value, or application literal",
    );
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Market" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "Return to dashboard evidence" }))
      .toHaveAttribute("href", "/");
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
