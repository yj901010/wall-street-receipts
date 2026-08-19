import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DashboardView } from "@/components/dashboard-view";
import { marketProvider } from "@/lib/providers";
import DashboardError from "./error";
import DashboardLoading from "./loading";
import DashboardPage from "./page";

function sectionForHeading(name: string) {
  const section = screen.getByRole("heading", { name }).closest("section");
  expect(section).not.toBeNull();
  return section!;
}

describe("DashboardPage", () => {
  it("renders independently sourced canonical calls and both DEMO map previews", async () => {
    render(await DashboardPage());

    expect(
      screen.getByRole("heading", { name: "Market evidence, without inferred gaps." }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Dataset provenance")).not.toBeInTheDocument();
    expect(screen.getByText(/does not synthesize one global as-of time or source/i))
      .toBeInTheDocument();

    const callSection = sectionForHeading("Latest calls within this fixture");
    const callProvenance = within(callSection).getByLabelText("Dashboard call section provenance");
    expect(callProvenance).toHaveTextContent("fixture-analyst-calls-v1");
    expect(callProvenance).toHaveTextContent("DEMO");
    expect(within(callProvenance).getByText("As of", { exact: true })).toBeInTheDocument();
    expect(callProvenance).toHaveTextContent("Aug 18, 2026, 12:00 AM UTC");
    expect(callProvenance).toHaveTextContent("Original event time, descending");
    expect(within(callSection).getByText(/latest within the committed DEMO fixture/i))
      .toBeInTheDocument();
    expect(within(callSection).getByText(/no record represents a real JPMorgan or Goldman Sachs/i))
      .toBeInTheDocument();

    const callsTable = within(callSection).getByRole("table", {
      name: "Latest analyst calls within the committed DEMO fixture",
    });
    const rows = within(callsTable).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(3);
    expect(rows.map((row) => row.querySelector<HTMLAnchorElement>(".row-link")?.getAttribute("href")))
      .toEqual([
        "/calls/demo-call-002",
        "/calls/demo-call-001",
        "/calls/demo-call-003",
      ]);
    rows.forEach((row) => expect(row).toHaveTextContent("DEMO"));

    const nullableRow = rows[2];
    expect(within(nullableRow).getByText("MSFT")).toBeInTheDocument();
    expect(within(nullableRow).getByText("DEMO unattributed neutral outlook")).toHaveAttribute(
      "href",
      "/calls/demo-call-003#source",
    );
    expect(nullableRow).toHaveTextContent("NA → NA");
    expect(nullableRow).toHaveTextContent("NA · ACTIVE · DEMO");

    for (const [title, provenanceId] of [
      ["S&P 500 map preview", "fixture-market-treemap-sp500-v1"],
      ["Nasdaq 100 map preview", "fixture-market-treemap-nasdaq100-v1"],
    ] as const) {
      const preview = screen.getByRole("heading", { name: title }).closest("article");
      expect(preview).not.toBeNull();
      const previewProvenance = within(preview!).getByLabelText(
          new RegExp(`${title.replace(" map preview", "")}.*provenance`, "i"),
        );
      expect(previewProvenance).toHaveTextContent(provenanceId);
      expect(within(previewProvenance).getByText("As of", { exact: true })).toBeInTheDocument();
      expect(within(previewProvenance).getByText("Generated", { exact: true })).toBeInTheDocument();
      expect(within(previewProvenance).getByText("Captured", { exact: true })).toBeInTheDocument();
      expect(previewProvenance).toHaveTextContent("Aug 19, 2026, 12:30 AM UTC");
      expect(within(previewProvenance).getAllByText("Aug 19, 2026, 1:00 AM UTC")).toHaveLength(2);
      expect(preview).toHaveTextContent("SAMPLE · 3 cells");
      expect(preview).toHaveTextContent("false");
      expect(preview).toHaveTextContent("1 outer sector · 3 industries");
      expect(preview).toHaveTextContent("SYNTHETIC_MARKET_CAP_PROXY");
      expect(preview).toHaveTextContent("144 relative units");
      expect(preview).toHaveTextContent("AAPL");
      expect(preview).toHaveTextContent("NA");
      expect(preview).toHaveTextContent(/does not assert official index membership/i);
    }

    expect(screen.getByRole("link", { name: "Open S&P 500 map" })).toHaveAttribute(
      "href",
      "/maps/sp500",
    );
    expect(screen.getByRole("link", { name: "Open Nasdaq 100 map" })).toHaveAttribute(
      "href",
      "/maps/nasdaq100",
    );
    expect(screen.getByRole("note")).toHaveTextContent(
      /reuse 3 stored synthetic ticker cells.*does not assert official membership/is,
    );
  });

  it("renders closed unavailable and deferred states without placeholder rows or metrics", async () => {
    render(await DashboardPage());

    const marketBoard = sectionForHeading("Market board");
    expect(within(marketBoard).getByText("Not published")).toBeInTheDocument();
    expect(within(marketBoard).getByLabelText("Market board availability")).toHaveTextContent(
      "NOT_PUBLISHED",
    );
    expect(within(marketBoard).getByLabelText("Market board availability")).toHaveTextContent("NA");
    expect(marketBoard).toHaveTextContent(/call-event snapshots.*not promoted to current quotes/i);
    expect(within(marketBoard).queryByRole("row")).not.toBeInTheDocument();

    const eventCalendar = sectionForHeading("Scheduled events");
    expect(within(eventCalendar).getByText("Not published")).toBeInTheDocument();
    expect(within(eventCalendar).getByLabelText("Scheduled events availability"))
      .toHaveTextContent("NOT_PUBLISHED");
    expect(eventCalendar).toHaveTextContent(/stays attached to its historical call/i);
    expect(within(eventCalendar).queryByRole("row")).not.toBeInTheDocument();

    const ranking = sectionForHeading("Ranking preview");
    expect(within(ranking).getByText("P3 deferred")).toBeInTheDocument();
    expect(within(ranking).getByLabelText("Ranking preview availability"))
      .toHaveTextContent("P3_DEFERRED");
    expect(within(ranking).getByLabelText("Ranking preview availability")).toHaveTextContent("NA");
    expect(within(ranking).queryByRole("row")).not.toBeInTheDocument();
    expect(within(ranking).queryByRole("table")).not.toBeInTheDocument();
    expect(within(ranking).getByRole("link", { name: "Review methodology evidence" }))
      .toHaveAttribute("href", "/methodology");

    expect(screen.queryByText("5,278.52")).not.toBeInTheDocument();
    expect(screen.queryByText("18,752.34")).not.toBeInTheDocument();
    expect(screen.queryByText("13.72")).not.toBeInTheDocument();
    expect(screen.queryByText("Versioned local fixture v1")).not.toBeInTheDocument();
  });

  it("renders explicit call and map empty states without substituting another dataset", async () => {
    const snapshot = structuredClone(await marketProvider().dashboard());
    snapshot.latestCalls.items = [];
    snapshot.mapPreviews.forEach((preview) => {
      preview.cells = [];
      preview.coverage.cellCount = 0;
    });

    render(<DashboardView snapshot={snapshot} />);

    expect(screen.getByText("No call events are recorded.")).toBeInTheDocument();
    expect(screen.getByText("No S&P 500 preview cells are recorded.")).toBeInTheDocument();
    expect(screen.getByText("No Nasdaq 100 preview cells are recorded.")).toBeInTheDocument();
    expect(screen.getAllByText(/No cell from another universe was substituted/i)).toHaveLength(2);
    expect(screen.queryByRole("table", {
      name: "Latest analyst calls within the committed DEMO fixture",
    })).not.toBeInTheDocument();
  });

  it("provides explicit loading and recoverable error boundaries", () => {
    const { unmount } = render(<DashboardLoading />);
    expect(document.querySelector('[aria-busy="true"]')).toHaveTextContent(
      "Loading independently sourced DEMO sections",
    );
    expect(screen.getByText(/No global timestamp, source, quote, event, or ranking/i))
      .toBeInTheDocument();
    unmount();

    const reset = vi.fn();
    render(<DashboardError error={new Error("fixture failure")} reset={reset} />);
    expect(screen.getByRole("alert")).toHaveTextContent(
      "The fixture sections could not be composed.",
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/No partial quote.*fallback universe/i);
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(reset).toHaveBeenCalledOnce();
    expect(screen.getByRole("link", { name: "Open the call ledger" })).toHaveAttribute(
      "href",
      "/calls",
    );
  });
});
