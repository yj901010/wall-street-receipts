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

  it("labels the snapshot immutable and renders unavailable values as NA", async () => {
    render(await CallDetailPage({ params: Promise.resolve({ id: "demo-call-002" }) }));

    expect(screen.getByText("Immutable point-in-time record")).toBeInTheDocument();
    expect(screen.getByText("Append-only; no update surface")).toBeInTheDocument();
    expect(screen.getByText("Snapshot processing time")).toBeInTheDocument();
    expect(screen.getByText("fixture-market-snapshots-v1")).toBeInTheDocument();
    expect(screen.getAllByText("NA").length).toBeGreaterThan(5);
    expect(screen.getByText("Outcome values remain NA until a versioned methodology is calculated. The UI never infers a score.")).toBeInTheDocument();
  });
});
