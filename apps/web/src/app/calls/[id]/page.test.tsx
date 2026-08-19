import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import CallDetailPage from "./page";

describe("CallDetailPage", () => {
  it("separates event and processing time and preserves source provenance", async () => {
    render(await CallDetailPage({ params: Promise.resolve({ id: "demo-call-002" }) }));

    expect(screen.getByRole("heading", { name: "Goldman Sachs on NVDA" })).toBeInTheDocument();
    expect(screen.getByText("DEMO Buy")).toBeInTheDocument();
    expect(screen.getByText("2 minutes")).toBeInTheDocument();
    expect(screen.getByText("+$25.00 (+11.9%)")).toBeInTheDocument();

    const eventSection = screen.getByRole("heading", { name: "Call facts" }).closest("section");
    expect(eventSection).not.toBeNull();
    expect(within(eventSection!).getByText("Event time")).toBeInTheDocument();
    expect(within(eventSection!).getByText("Processing time")).toBeInTheDocument();

    const sourceSection = screen.getByRole("heading", { name: "Source provenance" }).closest("section");
    expect(sourceSection).not.toBeNull();
    expect(within(sourceSection!).getByText("DEMO Channel")).toBeInTheDocument();
    expect(within(sourceSection!).getByText("INTERNAL_DEMO")).toBeInTheDocument();
    expect(within(sourceSection!).getByText("source-ref-demo-002")).toBeInTheDocument();
    expect(within(sourceSection!).getByText("fixture")).toBeInTheDocument();
    expect(within(sourceSection!).getByText("Reference provenance")).toBeInTheDocument();
    expect(within(sourceSection!).getAllByText("fixture-analyst-calls-v1")).toHaveLength(2);
    expect(within(sourceSection!).getByText("Page / time offset")).toBeInTheDocument();
    expect(within(sourceSection!).getByRole("link", { name: "Open canonical source" })).toHaveAttribute(
      "href",
      "https://example.invalid/demo-call-002",
    );
  });

  it("renders unavailable source metadata as NA without a canonical source link", async () => {
    render(await CallDetailPage({ params: Promise.resolve({ id: "demo-call-003" }) }));

    const sourceSection = screen.getByRole("heading", { name: "Source provenance" }).closest("section");
    expect(sourceSection).not.toBeNull();

    for (const label of ["Publisher", "External ID", "Published", "Content hash"]) {
      const term = within(sourceSection!).getByText(label);

      expect(term.nextElementSibling).toHaveTextContent(/^NA$/);
    }

    expect(within(sourceSection!).queryByRole("link", { name: "Open canonical source" })).not.toBeInTheDocument();
    expect(within(sourceSection!).getByText("Canonical source URL: NA")).toBeInTheDocument();
  });

  it("labels the snapshot immutable and renders unavailable values as NA", async () => {
    render(await CallDetailPage({ params: Promise.resolve({ id: "demo-call-002" }) }));

    expect(screen.getByText("Immutable point-in-time record")).toBeInTheDocument();
    expect(screen.getByText("Append-only; no update surface")).toBeInTheDocument();
    expect(screen.getByText("Snapshot processing time")).toBeInTheDocument();
    expect(screen.getByText("fixture-market-snapshots-v1")).toBeInTheDocument();
    expect(screen.getAllByText("NA").length).toBeGreaterThan(5);
    expect(screen.getByText("Outcome values remain NA until a versioned methodology is calculated. The UI never infers a score.")).toBeInTheDocument();
  });

  it("renders accessible point-in-time macro and scheduled-event evidence without derived claims", async () => {
    render(await CallDetailPage({ params: Promise.resolve({ id: "demo-call-001" }) }));

    const macroSection = screen.getByRole("heading", { name: "Macro context" }).closest("section");
    expect(macroSection).not.toBeNull();
    expect(within(macroSection!).getByText("macro-snapshot-demo-001")).toBeInTheDocument();
    expect(within(macroSection!).getAllByText("fixture-call-contexts-v1").length).toBeGreaterThan(1);

    const macroRegion = within(macroSection!).getByRole("region", {
      name: "Macro observation evidence table",
    });
    expect(macroRegion).toHaveAttribute("tabindex", "0");
    expect(within(macroRegion).getByRole("table", {
      name: "Macro observations at analyst-call event time",
    })).toBeInTheDocument();
    expect(within(macroRegion).getAllByRole("columnheader")).toHaveLength(11);

    const ppiRow = within(macroRegion).getByRole("row", { name: /PPI_YOY/ });
    expect(ppiRow.querySelector('[data-label="Value"]')).toHaveTextContent(/^NA$/);
    expect(ppiRow.querySelector('[data-label="Vintage end"]')).toHaveTextContent(/^NA$/);
    expect(within(ppiRow).getByText("source-ref-demo-macro-inflation-original-001")).toBeInTheDocument();

    const cpiRow = within(macroRegion).getByRole("row", { name: /CPI_YOY macro-observation-demo-cpi-original-001/ });
    expect(cpiRow.querySelector('[data-label="Value"]')).toHaveTextContent(/^3.1$/);
    expect(within(macroSection!).queryByText("macro-observation-demo-cpi-revision-001")).not.toBeInTheDocument();

    const eventSection = screen.getByRole("heading", { name: "Scheduled event context" }).closest("section");
    expect(eventSection).not.toBeNull();
    expect(within(eventSection!).getByText("event-context-demo-001")).toBeInTheDocument();
    expect(within(eventSection!).getByText("source-ref-demo-event-calendar-001")).toBeInTheDocument();

    const earnings = within(eventSection!).getByText("Earnings");
    const nextCpi = within(eventSection!).getByText("Next CPI");
    expect(earnings.nextElementSibling).toHaveTextContent(/^NA$/);
    expect(nextCpi.nextElementSibling).toHaveTextContent("Aug 12, 2026, 12:30 PM UTC");
    expect(within(macroSection!).queryByText(/proximity|regime|score|days? until/i)).not.toBeInTheDocument();
    expect(within(eventSection!).queryByText(/proximity|regime|score|days? until/i)).not.toBeInTheDocument();
  });

  it("keeps known-empty call context explicit and substitutes no values", async () => {
    render(await CallDetailPage({ params: Promise.resolve({ id: "demo-call-002" }) }));

    const macroSection = screen.getByRole("heading", { name: "Macro context" }).closest("section");
    const eventSection = screen.getByRole("heading", { name: "Scheduled event context" }).closest("section");
    expect(macroSection).not.toBeNull();
    expect(eventSection).not.toBeNull();

    for (const section of [macroSection!, eventSection!]) {
      expect(within(section).getByText("Known empty · DEMO")).toBeInTheDocument();
      expect(within(section).getByRole("status")).toHaveTextContent("Missing values remain NA.");
      expect(within(section).getByText("Source").nextElementSibling).toHaveTextContent(/^NA$/);
      expect(within(section).getByText("Provenance").nextElementSibling).toHaveTextContent(/^NA$/);
    }

    expect(within(macroSection!).queryByRole("table")).not.toBeInTheDocument();
    expect(within(eventSection!).queryByLabelText("Observed scheduled event timestamps")).not.toBeInTheDocument();
  });
});
