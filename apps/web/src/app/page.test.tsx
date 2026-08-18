import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import DashboardPage from "./page";

describe("DashboardPage", () => {
  it("renders an explicit DEMO dashboard with provenance and missing values", async () => {
    render(await DashboardPage());

    expect(screen.getAllByText("DEMO").length).toBeGreaterThan(0);
    expect(screen.getByText("Versioned local fixture v1")).toBeInTheDocument();
    expect(screen.getByText("DXY")).toBeInTheDocument();
    expect(screen.getAllByText("NA").length).toBeGreaterThan(0);

    const callsTable = screen.getByRole("table");
    expect(within(callsTable).getByText("JPMorgan")).toBeInTheDocument();
    expect(within(callsTable).getByText("DEMO equity interview")).toBeInTheDocument();
  });
});
