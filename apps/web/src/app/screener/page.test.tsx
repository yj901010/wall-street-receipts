import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { SCREENER_SHELL_STATE } from "@/lib/screener-shell-state";
import ScreenerError from "./error";
import ScreenerLoading from "./loading";
import ScreenerNotFound from "./not-found";
import ScreenerPage, { isQueryFreeScreenerRequest } from "./page";

function page(searchParams: Record<string, string | string[] | undefined> = {}) {
  return ScreenerPage({ searchParams: Promise.resolve(searchParams) });
}

describe("ScreenerPage", () => {
  it("renders the exact known-deferred application policy without screener output", async () => {
    render(await page());

    expect(screen.getByRole("heading", {
      name: "Historical equity screening is deferred.",
    })).toBeInTheDocument();
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();

    const navigation = screen.getByRole("navigation", { name: "Primary navigation" });
    const links = within(navigation).getAllByRole("link");
    expect(links.map((link) => link.textContent)).toEqual([
      "Dashboard",
      "Market",
      "Calls",
      "Institutions",
      "Analysts",
      "Maps",
      "Screener",
      "Methodology",
    ]);
    expect(within(navigation).getByRole("link", { name: "Screener" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(within(navigation).getByRole("link", { name: "Screener" })).toHaveAttribute(
      "href",
      "/screener",
    );

    const region = screen.getByRole("region", {
      name: "Historical screening publication state",
    });
    expect(region).toHaveAttribute("tabindex", "0");
    const policy = within(region).getByLabelText("Screener product availability policy");
    expect(within(policy).getByText(
      "Product availability policy · not fixture evidence",
      { exact: true },
    )).toBeVisible();
    expect(policy).toHaveTextContent("historical bars, a point-in-time feature catalog");
    expect(policy).toHaveTextContent("distinct from a completed screen with no matches");
    expect(policy).toHaveTextContent("Performance outcomes and rankings remain P3 work");
    expect(policy).toHaveTextContent("licensed observed-provider integration remains P5 work");

    const status = within(region).getByRole("status", { name: "Deferred screener state" });
    expect([...status.querySelectorAll("dt")].map((term) => term.textContent)).toEqual([
      "Data mode",
      "Scope",
      "Status",
      "Reason",
      "Missing display",
    ]);
    expect([...status.querySelectorAll("dd")].map((definition) => definition.textContent)).toEqual([
      SCREENER_SHELL_STATE.dataMode,
      SCREENER_SHELL_STATE.scope,
      SCREENER_SHELL_STATE.status,
      SCREENER_SHELL_STATE.reasonCode,
      SCREENER_SHELL_STATE.missingDisplay,
    ]);
    expect(within(status).queryByText("As of", { exact: true })).not.toBeInTheDocument();
    expect(within(status).queryByText("Source", { exact: true })).not.toBeInTheDocument();
    expect(within(status).queryByText("Provenance", { exact: true })).not.toBeInTheDocument();
    expect(within(region).getByRole("note")).toHaveTextContent(
      "NA records an unpublished capability state; it never means zero matches",
    );

    const adjacent = within(region).getByRole("navigation", { name: "Adjacent evidence routes" });
    expect(within(adjacent).getByText(
      "Separate evidence surfaces · not screener output",
      { exact: true },
    )).toBeVisible();
    expect(within(adjacent).getByRole("link", { name: "Open recorded call evidence" }))
      .toHaveAttribute("href", "/calls");
    expect(within(adjacent).getByRole("link", { name: "Open methodology definitions" }))
      .toHaveAttribute("href", "/methodology");

    expect(region.querySelector(
      "time, [datetime], form, button, input, select, textarea, table, [role=row], "
      + "[role=grid], canvas, svg, ol, ul, .metric-grid",
    )).toBeNull();
  });

  it("accepts only a keyless request and fails closed for empty, repeated, or unknown queries", async () => {
    expect(isQueryFreeScreenerRequest({})).toBe(true);
    expect(isQueryFreeScreenerRequest({ filter: "" })).toBe(false);
    expect(isQueryFreeScreenerRequest({ filter: ["value", "other"] })).toBe(false);
    expect(isQueryFreeScreenerRequest({ status: "P8_DEFERRED" })).toBe(false);
    expect(isQueryFreeScreenerRequest({ unknown: "value" })).toBe(false);

    await expect(page({ filter: "" })).rejects.toThrow(/404/);
    await expect(page({ filter: ["value", "other"] })).rejects.toThrow(/404/);
    await expect(page({ status: "P8_DEFERRED" })).rejects.toThrow(/404/);
    await expect(page({ unknown: "value" })).rejects.toThrow(/404/);
  });

  it("renders a mode-neutral unsupported-request body without leaking supported policy state", () => {
    render(<ScreenerNotFound />);

    const main = screen.getByRole("main");
    expect(within(main).getByText("Unsupported screener request", { exact: true }))
      .toBeInTheDocument();
    expect(within(main).getByRole("heading", {
      name: "This screener request is not published.",
    })).toBeInTheDocument();
    expect(within(main).queryByText("DEMO", { exact: true })).not.toBeInTheDocument();
    for (const value of Object.values(SCREENER_SHELL_STATE)) {
      expect(within(main).queryByText(value, { exact: true })).not.toBeInTheDocument();
    }
    expect(within(main).queryByRole("navigation", { name: "Primary navigation" }))
      .not.toBeInTheDocument();
    expect(within(main).queryByRole("form")).not.toBeInTheDocument();
    expect(within(main).queryByRole("table")).not.toBeInTheDocument();
    expect(main.querySelector("input, select, canvas, svg, .metric-grid")).toBeNull();

    const actions = main.querySelector<HTMLElement>(".state-actions");
    expect(actions).not.toBeNull();
    const actionLinks = within(actions!).getAllByRole("link");
    expect(actionLinks).toHaveLength(2);
    expect(actionLinks.map((link) => link.getAttribute("href"))).toEqual([
      "/calls",
      "/methodology",
    ]);
  });

  it("keeps DEMO navigation and no-fallback semantics in loading and recoverable error states", () => {
    const loading = render(<ScreenerLoading />);

    expect(screen.getByText("Loading the DEMO application policy…").closest("div"))
      .toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Screener" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByText(/No filter, result, ordering, chart, count, or numeric metric/))
      .toBeInTheDocument();
    loading.unmount();

    const reset = vi.fn();
    render(<ScreenerError error={new Error("policy failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "No fixture, source, filter, result, chart, or numeric value is displayed as a fallback.",
    );
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Screener" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
