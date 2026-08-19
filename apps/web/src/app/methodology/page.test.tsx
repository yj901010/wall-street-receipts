import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { MethodologyCatalog } from "@/lib/providers";
import MethodologyError from "./error";
import MethodologyLoading from "./loading";
import { MethodologyRegistry } from "./methodology-registry";
import MethodologyPage from "./page";

describe("MethodologyPage", () => {
  it("renders the DEMO version registry without implying calculated outcomes", async () => {
    render(await MethodologyPage());

    expect(screen.getByRole("heading", {
      name: "Methodology definitions, before performance claims.",
    })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Methodology" })).toHaveAttribute(
      "aria-current",
      "page",
    );

    const provenance = screen.getByLabelText("Methodology dataset provenance");
    expect(within(provenance).getByText("fixture-call-outcomes-v1")).toBeInTheDocument();
    expect(within(provenance).getByText("DEMO")).toBeInTheDocument();

    const registryRegion = screen.getByRole("region", { name: "Methodology registry table" });
    expect(registryRegion).toHaveAttribute("tabindex", "0");
    const table = within(registryRegion).getByRole("table", {
      name: "Versioned scoring methodology definitions",
    });
    expect(within(table).getAllByRole("columnheader")).toHaveLength(8);
    expect(within(table).getAllByText("MODEL_ONLY")).toHaveLength(2);

    const versions = within(table).getAllByRole("row").slice(1).map((row) =>
      row.querySelector('[data-label="Version"]')?.textContent,
    );
    expect(versions).toEqual(["1.0.0", "2.0.0"]);
    expect(within(table).getByText(
      "03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2",
    )).toBeInTheDocument();
    expect(within(table).getByText(
      "256056d7cb2b292a1ec0bd7b905f856134bb38851a65b8a2fceaca41489db3e8",
    )).toBeInTheDocument();
    expect(screen.getByText(/fixture does not contain the formula body/i)).toBeInTheDocument();
    expect(screen.getByText(/calculations remain deferred to P3/i)).toBeInTheDocument();
    expect(screen.queryByText(/directional win:\s*(true|false)/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/active methodology|current version|accuracy score/i)).not.toBeInTheDocument();
  });

  it("renders an explicit empty registry without substitute definitions", () => {
    const emptyCatalog: MethodologyCatalog = {
      asOf: "2026-08-18T00:10:00Z",
      dataMode: "DEMO",
      source: "fixture-call-outcomes-v1",
      disclaimer: "Synthetic DEMO model records only.",
      items: [],
    };

    render(<MethodologyRegistry catalog={emptyCatalog} />);

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "No substitute version, hash, or calculation result was generated.",
    );
    expect(screen.getByText("0 DEMO definitions")).toBeInTheDocument();
  });

  it("provides accessible loading and retryable error states", () => {
    const { unmount } = render(<MethodologyLoading />);

    expect(screen.getByText("Loading methodology evidence…").closest("div")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    unmount();

    const reset = vi.fn();
    render(<MethodologyError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "No partial definition or calculated value is being displayed.",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
