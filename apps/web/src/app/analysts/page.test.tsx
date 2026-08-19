import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { FixtureAnalystDirectoryProvider } from "@/lib/providers";
import { AnalystDirectory } from "./analyst-directory";
import AnalystsError from "./error";
import AnalystsLoading from "./loading";
import AnalystsPage from "./page";

describe("AnalystsPage", () => {
  it("renders the canonical DEMO analyst identities without affiliation or leaderboard data", async () => {
    render(await AnalystsPage());

    expect(screen.getByRole("heading", {
      name: "Analysts as recorded evidence, not a leaderboard.",
    })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Analysts" })).toHaveAttribute(
      "aria-current",
      "page",
    );

    const provenance = screen.getByLabelText("Analyst identity fixture provenance");
    expect(within(provenance).getByText("1.0.0")).toBeInTheDocument();
    expect(within(provenance).getByText("v1")).toBeInTheDocument();
    expect(within(provenance).getByText("fixture-master-data-v1")).toBeInTheDocument();
    expect(within(provenance).getByText("DEMO")).toBeInTheDocument();
    expect(within(provenance).getAllByText("Aug 18, 2026, 12:00 AM UTC")).toHaveLength(2);

    const sourceEvidence = screen.getByLabelText("Analyst source evidence");
    expect(within(sourceEvidence).getByText("LOCAL_SPECIFICATION")).toBeInTheDocument();
    expect(within(sourceEvidence).getByText("INTERNAL_DEMO")).toBeInTheDocument();
    expect(within(sourceEvidence).getByText("true")).toBeInTheDocument();
    expect(within(sourceEvidence).getByText("docs/fixtures/institutions.json")).toBeInTheDocument();
    expect(within(sourceEvidence).getByText("docs/docs/DOMAIN_MODEL.md")).toBeInTheDocument();

    const policy = screen.getByLabelText("Analyst directory policy");
    expect(within(policy).getByText("Product policy · not fixture evidence", { exact: true }))
      .toBeVisible();
    expect(policy).toHaveTextContent("Not ranked.");
    expect(policy).toHaveTextContent("not a live activity claim");
    expect(policy).toHaveTextContent(
      "do not establish verified coverage, employer or affiliation, endorsement, performance, or investment advice",
    );
    expect(screen.getByText("DEMO identity fixture · coverage not asserted")).toBeInTheDocument();

    const region = screen.getByRole("region", { name: "Analyst identity table" });
    expect(region).toHaveAttribute("tabindex", "0");
    const table = within(region).getByRole("table", {
      name: "Canonical analyst identities and their captured evidence",
    });
    expect(within(table).getAllByRole("columnheader")).toHaveLength(7);
    expect(within(table).queryByRole("columnheader", {
      name: /institution|employer|affiliation|rank|score|accuracy|performance|call count|outcome/i,
    })).not.toBeInTheDocument();

    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    expect(rows.map((row) => row.querySelector('[data-label="Analyst"] strong')?.textContent))
      .toEqual(["Demo Analyst A", "Demo Analyst B"]);
    expect(rows.map((row) => row.querySelector('[data-label="Analyst"] .mono')?.textContent))
      .toEqual(["analyst-demo-a", "analyst-demo-b"]);

    for (const [index, analystId] of ["analyst-demo-a", "analyst-demo-b"].entries()) {
      const row = rows[index];
      expect(within(row).getByText("true")).toBeInTheDocument();
      expect(within(row).getByText("DEMO")).toBeInTheDocument();
      expect(within(row).getByText("Aug 10, 2026, 12:00 AM UTC")).toBeInTheDocument();
      expect(within(row).getByText("Aug 18, 2026, 12:00 AM UTC")).toBeInTheDocument();
      expect(within(row).getByText("fixture-master-data-v1")).toBeInTheDocument();
      expect(within(row).getByRole("link", {
        name: `Filter call ledger for Demo Analyst ${index === 0 ? "A" : "B"}`,
      })).toHaveAttribute("href", `/calls?analystId=${analystId}`);
    }

    expect(screen.queryByText("JPMorgan")).not.toBeInTheDocument();
    expect(screen.queryByText("Goldman Sachs")).not.toBeInTheDocument();
    expect(screen.queryByText("DEMO Strategist")).not.toBeInTheDocument();
    expect(screen.queryByText("DEMO Equity Analyst")).not.toBeInTheDocument();
    expect(screen.queryByText("demo-call-001")).not.toBeInTheDocument();
    expect(screen.queryByText("demo-call-002")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Demo Analyst A" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Demo Analyst B" })).not.toBeInTheDocument();
  });

  it("renders a valid empty analyst catalog without counts or placeholders", async () => {
    const snapshot = await new FixtureAnalystDirectoryProvider().directory();

    render(<AnalystDirectory snapshot={{ ...snapshot, analysts: [] }} />);

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "No placeholder identity, affiliation, call data, metric, score, or rank was generated.",
    );
    expect(screen.getByText("DEMO identity fixture · coverage not asserted")).toBeInTheDocument();
    expect(screen.queryByText(/0 DEMO/)).not.toBeInTheDocument();
  });

  it("keeps DEMO navigation in accessible loading and retryable error states", () => {
    const loading = render(<AnalystsLoading />);

    expect(screen.getByText("Loading analyst evidence…").closest("main")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Analysts" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    loading.unmount();

    const reset = vi.fn();
    render(<AnalystsError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "No partial identity, affiliation, call data, metric, score, or rank is being displayed.",
    );
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Analysts" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
