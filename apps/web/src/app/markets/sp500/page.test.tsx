import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import {
  FixtureCallsProvider,
  FixtureSp500HistoryProvider,
  type Sp500HistorySnapshot,
} from "@/lib/providers";
import Sp500HistoryError from "./error";
import { KeyboardScrollRegion } from "./keyboard-scroll-region";
import Sp500HistoryLoading from "./loading";
import Sp500HistoryPage from "./page";
import { Sp500CallHistory } from "./sp500-call-history";

async function fixtureSnapshot() {
  return new FixtureSp500HistoryProvider(new FixtureCallsProvider()).history();
}

describe("Sp500HistoryPage", () => {
  it("renders the canonical recorded DEMO event with scoped catalog and source evidence", async () => {
    render(await Sp500HistoryPage());

    expect(screen.getByRole("heading", {
      name: "Recorded S&P 500 forecast-call events.",
    })).toBeInTheDocument();
    const navigation = screen.getByRole("navigation", { name: "Primary navigation" });
    expect(within(navigation).getAllByRole("link")).toHaveLength(7);
    expect(within(navigation).getByRole("link", { name: "Market" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(within(navigation).getByRole("link", { name: "Market" })).toHaveAttribute(
      "href",
      "/market",
    );

    const provenance = screen.getByLabelText("S&P 500 call-history provenance");
    expect(within(provenance).getByText("Call catalog as of", { exact: true }))
      .toBeInTheDocument();
    expect(within(provenance).getByText("2026-08-18T00:00:00Z", { exact: true }))
      .toHaveAttribute("datetime", "2026-08-18T00:00:00Z");
    expect(within(provenance).getByText("fixture-analyst-calls-v1", { exact: true }))
      .toBeInTheDocument();
    expect(within(provenance).getByText("SPX", { exact: true })).toBeInTheDocument();
    expect(within(provenance).getByText("DEMO", { exact: true })).toBeInTheDocument();

    const history = screen.getByRole("region", { name: "S&P 500 call-event history" });
    expect(within(history).getByText(
      "1 row shown · 1 matching DEMO event · incomplete fixture coverage",
      { exact: true },
    )).toBeInTheDocument();
    const policy = within(history).getByLabelText("S&P 500 call-history policy");
    expect(within(policy).getByText("Presentation policy · not fixture evidence", { exact: true }))
      .toBeVisible();
    expect(policy).toHaveTextContent("No correction or revision is folded into a current effective view");
    expect(policy).toHaveTextContent(
      "not current recommendations, prices, consensus, or performance",
    );
    expect(policy).toHaveTextContent("do not assert S&P 500 coverage");

    const queryEvidence = within(history).getByLabelText("S&P 500 history query evidence");
    expect(queryEvidence).toHaveTextContent("S&P 500 Index");
    expect(queryEvidence).toHaveTextContent("asset-spx");
    expect(queryEvidence).toHaveTextContent("SPX · INDEX");
    expect(queryEvidence).toHaveTextContent("asset-spx · page 0 · size 25");
    expect(queryEvidence).toHaveTextContent("Event time descending · call ID ascending tie break");
    expect(queryEvidence).toHaveTextContent("1 / 1");

    const table = within(history).getByRole("table", {
      name: "Original committed S&P 500 DEMO analyst-call events",
    });
    const disclaimer = within(history).getByText(
      "Synthetic DEMO events only; no record represents a real JPMorgan or Goldman Sachs analyst statement.",
      { exact: true },
    );
    expect(disclaimer.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING)
      .toBeTruthy();
    expect(within(table).getAllByRole("columnheader")).toHaveLength(8);
    expect(within(table).queryByRole("columnheader", {
      name: /market price|return|alpha|hit|accuracy|rank|consensus|outcome/i,
    })).not.toBeInTheDocument();

    const row = within(table).getAllByRole("row")[1];
    expect(within(row).getByRole("link", { name: "2026-08-10T12:00:00Z" }))
      .toHaveAttribute("href", "/calls/demo-call-001");
    expect(within(row).getByText("demo-call-001", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("JPMorgan", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("Demo Analyst A", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("BULLISH", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("DEMO Bullish", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("$7,800.00 → $8,000.00", { exact: true }))
      .toBeInTheDocument();
    expect(within(row).getByText("Currency: USD", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("NA", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("ACTIVE", { exact: true })).toBeInTheDocument();
    expect(within(row).getByRole("link", { name: "DEMO index outlook" }))
      .toHaveAttribute("href", "/calls/demo-call-001#source");
    expect(within(row).getByText("DEMO Publisher · Verified: false", { exact: true }))
      .toBeInTheDocument();
    expect(within(row).getAllByText("2026-08-10T12:03:00Z", { exact: true }))
      .toHaveLength(2);
    expect(within(row).getByText("DEMO · fixture-analyst-calls-v1", { exact: true }))
      .toBeInTheDocument();
    expect(within(row).queryByText("demo-source-001", { exact: true })).not.toBeInTheDocument();

    expect(within(history).getByRole("link", { name: "Open filtered call ledger" }))
      .toHaveAttribute("href", "/calls?assetId=asset-spx");
    expect(within(history).getByRole("link", { name: "Return to market publication status" }))
      .toHaveAttribute("href", "/market");
  });

  it("renders honest empty paging and preserves visible microsecond evidence", async () => {
    const canonical = await fixtureSnapshot();
    const empty: Sp500HistorySnapshot = {
      ...canonical,
      items: [],
      page: {
        ...canonical.page,
        totalElements: 0,
        totalPages: 0,
        last: true,
      },
    };
    const emptyRender = render(<Sp500CallHistory snapshot={empty} />);

    expect(screen.getByText(
      "0 rows shown · 0 matching DEMO events · incomplete fixture coverage",
      { exact: true },
    )).toBeInTheDocument();
    expect(screen.getByLabelText("S&P 500 history query evidence")).toHaveTextContent("0 / 0");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "No placeholder forecast, target, status, source, market price, or outcome was created.",
    );
    emptyRender.unmount();

    const item = structuredClone(canonical.items[0]);
    item.call.eventTime = "2026-08-10T12:00:00.000001Z";
    item.call.processingTime = "2026-08-10T12:03:00.000002Z";
    item.call.capturedAt = "2026-08-10T12:03:00.000003Z";
    render(<Sp500CallHistory snapshot={{ ...canonical, items: [item] }} />);

    for (const instant of [
      "2026-08-10T12:00:00.000001Z",
      "2026-08-10T12:03:00.000002Z",
      "2026-08-10T12:03:00.000003Z",
    ]) {
      expect(screen.getByText(instant, { exact: true })).toHaveAttribute("datetime", instant);
    }
  });

  it("distinguishes a full first page from the matching fixture total", async () => {
    const canonical = await fixtureSnapshot();
    const items = Array.from({ length: 25 }, (_, index) => {
      if (index === 0) return structuredClone(canonical.items[0]);
      const item = structuredClone(canonical.items[0]);
      const suffix = String(index + 1).padStart(3, "0");
      item.call.callId = `demo-call-future-${suffix}`;
      item.call.providerEventId = `fixture-call-future-${suffix}`;
      return item;
    });

    render(<Sp500CallHistory snapshot={{
      ...canonical,
      items,
      page: {
        ...canonical.page,
        totalElements: 26,
        totalPages: 2,
        last: false,
      },
    }} />);

    expect(screen.getByText(
      "25 rows shown · 26 matching DEMO events · incomplete fixture coverage",
      { exact: true },
    )).toBeInTheDocument();
    expect(screen.getByLabelText("S&P 500 history query evidence")).toHaveTextContent("1 / 2");
    expect(screen.getAllByRole("row")).toHaveLength(26);
  });

  it("renders every supported nullable row field as NA without borrowing a value", async () => {
    const canonical = await fixtureSnapshot();
    const item = structuredClone(canonical.items[0]);
    item.call.analystId = null;
    item.analyst = null;
    item.call.originalRating = null;
    item.call.previousTarget = null;
    item.call.target = null;
    item.call.currency = null;
    item.call.targetDate = null;
    item.source.document.publisher = null;

    render(<Sp500CallHistory snapshot={{ ...canonical, items: [item] }} />);

    const row = screen.getAllByRole("row")[1];
    const identity = row.querySelector('[data-label="Institution / analyst"]');
    const rating = row.querySelector('[data-label="Recorded direction / rating"]');
    const targets = row.querySelector('[data-label="Stored targets"]');
    const targetDate = row.querySelector('[data-label="Target date"]');
    const source = row.querySelector('[data-label="Source evidence"]');
    expect(identity?.querySelector(".cell-secondary")).toHaveTextContent(/^NA$/);
    expect(rating?.querySelector(".cell-secondary")).toHaveTextContent(/^NA$/);
    expect(targets).toHaveTextContent("NA → NA");
    expect(targets).toHaveTextContent("Currency: NA");
    expect(targets).not.toHaveTextContent("$0");
    expect(targetDate).toHaveTextContent(/^NA$/);
    expect(source?.querySelector(".cell-secondary")).toHaveTextContent(
      /^NA · Verified: false$/,
    );
  });

  it("moves only an overflowing focused table region with horizontal arrow keys", () => {
    render(
      <KeyboardScrollRegion ariaLabel="Test horizontal evidence" className="table-scroll">
        <button type="button">Nested evidence control</button>
      </KeyboardScrollRegion>,
    );
    const region = screen.getByRole("region", { name: "Test horizontal evidence" });
    Object.defineProperties(region, {
      clientWidth: { configurable: true, value: 400 },
      scrollWidth: { configurable: true, value: 1_000 },
    });
    region.scrollLeft = 0;

    expect(fireEvent.keyDown(region, { key: "ArrowRight" })).toBe(false);
    expect(region.scrollLeft).toBe(100);
    expect(fireEvent.keyDown(region, { key: "ArrowLeft" })).toBe(false);
    expect(region.scrollLeft).toBe(0);

    expect(fireEvent.keyDown(region, { key: "ArrowLeft" })).toBe(true);
    expect(fireEvent.keyDown(region, { key: "ArrowRight", shiftKey: true })).toBe(true);
    expect(fireEvent.keyDown(screen.getByRole("button"), { key: "ArrowRight" })).toBe(true);
    expect(region.scrollLeft).toBe(0);

    Object.defineProperty(region, "scrollWidth", { configurable: true, value: 400 });
    expect(fireEvent.keyDown(region, { key: "ArrowRight" })).toBe(true);
    expect(region.scrollLeft).toBe(0);
  });

  it("keeps DEMO navigation in accessible loading and retryable error states", () => {
    const loading = render(<Sp500HistoryLoading />);

    expect(screen.getByText("Loading the committed DEMO call subset…").closest("main"))
      .toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Market" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByText(/No market price, chart, target, status, outcome, or placeholder/))
      .toBeInTheDocument();
    loading.unmount();

    const reset = vi.fn();
    render(<Sp500HistoryError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "No partial call, market snapshot, chart, outcome, consensus, or application literal",
    );
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Market" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "Return to market publication status" }))
      .toHaveAttribute("href", "/market");
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
