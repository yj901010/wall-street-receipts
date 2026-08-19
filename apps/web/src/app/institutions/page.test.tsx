import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { FixtureInstitutionDirectoryProvider } from "@/lib/providers";
import InstitutionsError from "./error";
import { InstitutionDirectory } from "./institution-directory";
import InstitutionsLoading from "./loading";
import InstitutionsPage from "./page";

describe("InstitutionsPage", () => {
  it("renders the canonical DEMO identity directory without a leaderboard", async () => {
    render(await InstitutionsPage());

    expect(screen.getByRole("heading", {
      name: "Institutions as recorded evidence, not a leaderboard.",
    })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Institutions" })).toHaveAttribute(
      "aria-current",
      "page",
    );

    const provenance = screen.getByLabelText("Institution identity fixture provenance");
    expect(within(provenance).getByText("1.0.0")).toBeInTheDocument();
    expect(within(provenance).getByText("v1")).toBeInTheDocument();
    expect(within(provenance).getByText("fixture-master-data-v1")).toBeInTheDocument();
    expect(within(provenance).getByText("DEMO")).toBeInTheDocument();
    expect(within(provenance).getAllByText("Aug 18, 2026, 12:00 AM UTC")).toHaveLength(2);

    const sourceEvidence = screen.getByLabelText("Institution source evidence");
    expect(within(sourceEvidence).getByText("LOCAL_SPECIFICATION")).toBeInTheDocument();
    expect(within(sourceEvidence).getByText("INTERNAL_DEMO")).toBeInTheDocument();
    expect(within(sourceEvidence).getByText("true")).toBeInTheDocument();
    expect(within(sourceEvidence).getByText("docs/fixtures/institutions.json")).toBeInTheDocument();
    expect(within(sourceEvidence).getByText("docs/docs/DOMAIN_MODEL.md")).toBeInTheDocument();

    const policy = screen.getByLabelText("Institution directory policy");
    expect(within(policy).getByText("Product policy · not fixture evidence", { exact: true }))
      .toBeVisible();
    expect(policy).toHaveTextContent("Not ranked.");
    expect(policy).toHaveTextContent("not a live operating-status claim");
    expect(policy).toHaveTextContent("not an endorsement or investment advice");
    expect(screen.getByText("2 DEMO fixture records · coverage not asserted")).toBeInTheDocument();

    const region = screen.getByRole("region", { name: "Institution identity table" });
    expect(region).toHaveAttribute("tabindex", "0");
    const table = within(region).getByRole("table", {
      name: "Canonical institution identities and their captured evidence",
    });
    expect(within(table).getAllByRole("columnheader")).toHaveLength(9);
    expect(within(table).queryByRole("columnheader", {
      name: /rank|score|accuracy|performance|call count/i,
    })).not.toBeInTheDocument();

    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    expect(rows.map((row) => row.querySelector('[data-label="Institution"] strong')?.textContent))
      .toEqual(["Goldman Sachs", "JPMorgan"]);
    expect(rows.map((row) => row.querySelector('[data-label="Institution"] .mono')?.textContent))
      .toEqual(["inst-gs", "inst-jpm"]);

    const goldman = rows[0];
    expect(within(goldman).getByText("goldman-sachs")).toBeInTheDocument();
    expect(within(goldman).getByText("US")).toBeInTheDocument();
    expect(within(goldman).getByText("true")).toBeInTheDocument();
    expect(within(goldman).getByText("DEMO")).toBeInTheDocument();
    expect(within(goldman).getByText("Aug 10, 2026, 12:00 AM UTC")).toBeInTheDocument();
    expect(within(goldman).getByText("Aug 18, 2026, 12:00 AM UTC")).toBeInTheDocument();
    expect(within(goldman).getByText("fixture-master-data-v1")).toBeInTheDocument();
    expect(within(goldman).getByRole("link", {
      name: "Filter call ledger for Goldman Sachs",
    })).toHaveAttribute("href", "/calls?institutionId=inst-gs");

    const jpmorgan = rows[1];
    expect(within(jpmorgan).getByText("jpmorgan", { exact: true })).toBeInTheDocument();
    expect(within(jpmorgan).getByText("US")).toBeInTheDocument();
    expect(within(jpmorgan).getByText("true")).toBeInTheDocument();
    expect(within(jpmorgan).getByText("DEMO")).toBeInTheDocument();
    expect(within(jpmorgan).getByText("Aug 10, 2026, 12:00 AM UTC")).toBeInTheDocument();
    expect(within(jpmorgan).getByText("Aug 18, 2026, 12:00 AM UTC")).toBeInTheDocument();
    expect(within(jpmorgan).getByText("fixture-master-data-v1")).toBeInTheDocument();
    expect(within(jpmorgan).getByRole("link", {
      name: "Filter call ledger for JPMorgan",
    })).toHaveAttribute("href", "/calls?institutionId=inst-jpm");
    expect(within(table).queryByRole("link", { name: "Goldman Sachs" })).not.toBeInTheDocument();
    expect(within(table).queryByRole("link", { name: "JPMorgan" })).not.toBeInTheDocument();
    expect(screen.queryByText("Demo Analyst A")).not.toBeInTheDocument();
    expect(screen.queryByText("Demo Analyst B")).not.toBeInTheDocument();
  });

  it("renders a valid empty identity catalog without placeholders", async () => {
    const snapshot = await new FixtureInstitutionDirectoryProvider().directory();

    render(<InstitutionDirectory snapshot={{ ...snapshot, institutions: [] }} />);

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "No placeholder identity, coverage claim, score, accuracy, or rank was generated.",
    );
    expect(screen.getByText("0 DEMO fixture records · coverage not asserted")).toBeInTheDocument();
  });

  it("keeps DEMO navigation in accessible loading and retryable error states", () => {
    const loading = render(<InstitutionsLoading />);

    expect(screen.getByText("Loading institution evidence…").closest("main")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Institutions" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    loading.unmount();

    const reset = vi.fn();
    render(<InstitutionsError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "No partial identity, placeholder institution, score, accuracy, or rank is being displayed.",
    );
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Institutions" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
