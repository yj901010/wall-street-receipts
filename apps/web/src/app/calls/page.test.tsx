import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import CallsPage from "./page";

describe("CallsPage", () => {
  it("renders the canonical DEMO call ledger and accessible filters", async () => {
    render(await CallsPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByRole("heading", { name: "Analyst calls" })).toBeInTheDocument();
    expect(screen.getByRole("form", { name: "Filter analyst calls" })).toBeInTheDocument();
    expect(screen.getByLabelText("Ticker")).toBeInTheDocument();
    expect(screen.getAllByText("DEMO").length).toBeGreaterThan(0);

    const table = screen.getByRole("table", { name: "Filtered analyst call events" });
    const sourceLink = within(table).getByRole("link", { name: "DEMO equity interview" });
    const callRow = sourceLink.closest("tr");

    expect(callRow).not.toBeNull();
    expect(within(callRow!).getByText("Goldman Sachs")).toBeInTheDocument();
    expect(within(callRow!).getByText("NVDA")).toBeInTheDocument();
    expect(sourceLink).toHaveAttribute(
      "href",
      "/calls/demo-call-002#source",
    );
  });

  it("renders zero-based provider pagination as human page numbers", async () => {
    render(await CallsPage({ searchParams: Promise.resolve({ size: "1", page: "0" }) }));

    expect(screen.getByText(/Page 1 of 3/)).toBeInTheDocument();
    const pagination = screen.getByRole("navigation", { name: "Calls pages" });
    expect(within(pagination).getByText("Previous")).toHaveAttribute("aria-disabled", "true");
    expect(within(pagination).getByRole("link", { name: "Next" })).toHaveAttribute(
      "href",
      "/calls?size=1&page=1",
    );
  });

  it("renders an explicit empty state without substitute records", async () => {
    render(await CallsPage({ searchParams: Promise.resolve({ ticker: "TSLA" }) }));

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Nothing matches these filters.");
    expect(screen.getByText(/never replaced with synthetic values/i)).toBeInTheDocument();
  });
});
