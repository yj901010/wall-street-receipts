import { fireEvent, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { FixtureAnalystDirectoryProvider } from "@/lib/providers";
import { renderWithLocale } from "@/test/render-with-locale";
import { AnalystDirectory } from "./analyst-directory";
import AnalystsError from "./error";
import AnalystsLoading from "./loading";
import AnalystsPage from "./page";

const i18n = vi.hoisted(() => ({ getLocale: vi.fn() }));

vi.mock("@/lib/i18n/server", () => ({ getLocale: i18n.getLocale }));

describe("AnalystsPage", () => {
  beforeEach(() => {
    i18n.getLocale.mockReset();
    i18n.getLocale.mockResolvedValue("ko");
  });

  it("renders Korean-default analyst UI without changing canonical identity evidence", async () => {
    renderWithLocale(await AnalystsPage());

    expect(screen.getByRole("heading", {
      name: "애널리스트를 순위표가 아닌 기록된 증거로 봅니다.",
    })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "애널리스트" })).toHaveAttribute(
      "aria-current",
      "page",
    );

    const provenance = screen.getByLabelText("애널리스트 식별 픽스처 출처");
    expect(within(provenance).getByText("1.0.0")).toBeInTheDocument();
    expect(within(provenance).getByText("v1")).toBeInTheDocument();
    expect(within(provenance).getByText("fixture-master-data-v1")).toBeInTheDocument();
    expect(within(provenance).getByText("DEMO")).toBeInTheDocument();
    expect(within(provenance).getAllByText("Aug 18, 2026, 12:00 AM UTC")).toHaveLength(2);

    const sourceEvidence = screen.getByLabelText("애널리스트 소스 증거");
    for (const value of [
      "LOCAL_SPECIFICATION",
      "INTERNAL_DEMO",
      "true",
      "docs/fixtures/institutions.json",
      "docs/docs/DOMAIN_MODEL.md",
    ]) {
      expect(within(sourceEvidence).getByText(value)).toBeInTheDocument();
    }

    const policy = screen.getByLabelText("애널리스트 디렉터리 정책");
    expect(within(policy).getByText("제품 정책 · 픽스처 증거 아님", { exact: true })).toBeVisible();
    expect(policy).toHaveTextContent("순위가 아닙니다.");
    expect(policy).toHaveTextContent("현재 활동 상태를 주장하지 않습니다");
    expect(policy).toHaveTextContent("고용주나 소속, 보증, 성과 또는 투자 조언");
    expect(screen.getByText("DEMO 식별 픽스처 · 범위를 주장하지 않음"))
      .toBeInTheDocument();

    const region = screen.getByRole("region", { name: "애널리스트 식별 정보 표" });
    expect(region).toHaveAttribute("tabindex", "0");
    const table = within(region).getByRole("table", {
      name: "정규 애널리스트 식별 정보와 수집된 증거",
    });
    expect(within(table).getAllByRole("columnheader")).toHaveLength(7);

    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    expect(rows.map((row) => row.querySelector('[data-label="애널리스트"] strong')?.textContent))
      .toEqual(["Demo Analyst A", "Demo Analyst B"]);
    expect(rows.map((row) => row.querySelector('[data-label="애널리스트"] .mono')?.textContent))
      .toEqual(["analyst-demo-a", "analyst-demo-b"]);

    for (const [index, analystId] of ["analyst-demo-a", "analyst-demo-b"].entries()) {
      const row = rows[index];
      expect(within(row).getByText("true")).toBeInTheDocument();
      expect(within(row).getByText("DEMO")).toBeInTheDocument();
      expect(within(row).getByText("Aug 10, 2026, 12:00 AM UTC")).toBeInTheDocument();
      expect(within(row).getByText("Aug 18, 2026, 12:00 AM UTC")).toBeInTheDocument();
      expect(within(row).getByText("fixture-master-data-v1")).toBeInTheDocument();
      expect(within(row).getByRole("link", {
        name: `다음 애널리스트로 콜 원장 필터링: Demo Analyst ${index === 0 ? "A" : "B"}`,
      })).toHaveAttribute("href", `/calls?analystId=${analystId}`);
    }

    for (const absent of [
      "JPMorgan",
      "Goldman Sachs",
      "DEMO Strategist",
      "DEMO Equity Analyst",
      "demo-call-001",
      "demo-call-002",
    ]) {
      expect(screen.queryByText(absent)).not.toBeInTheDocument();
    }
  });

  it("renders English cookie UI with byte-stable identities and times", async () => {
    i18n.getLocale.mockResolvedValue("en");
    renderWithLocale(await AnalystsPage(), "en");

    expect(screen.getByRole("heading", {
      name: "Analysts as recorded evidence, not a leaderboard.",
    })).toBeInTheDocument();
    const table = screen.getByRole("table", {
      name: "Canonical analyst identities and their captured evidence",
    });
    expect(within(table).getByText("Demo Analyst A")).toBeInTheDocument();
    expect(within(table).getAllByText("Aug 10, 2026, 12:00 AM UTC")).toHaveLength(2);
    expect(within(table).getAllByText("fixture-master-data-v1")).toHaveLength(2);
  });

  it("renders a valid Korean empty analyst catalog without placeholders", async () => {
    const snapshot = await new FixtureAnalystDirectoryProvider().directory();

    renderWithLocale(<AnalystDirectory snapshot={{ ...snapshot, analysts: [] }} locale="ko" />);

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "대체 식별 정보, 소속, 콜 데이터, 지표, 점수 또는 순위를 생성하지 않았습니다.",
    );
    expect(screen.getByText("DEMO 식별 픽스처 · 범위를 주장하지 않음"))
      .toBeInTheDocument();
    expect(screen.queryByText(/0 DEMO/)).not.toBeInTheDocument();
  });

  it("keeps Korean DEMO navigation in loading and retryable error states", async () => {
    const loading = renderWithLocale(await AnalystsLoading());

    expect(screen.getByText("애널리스트 증거를 불러오는 중…").closest("main"))
      .toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "애널리스트" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    loading.unmount();

    const reset = vi.fn();
    renderWithLocale(<AnalystsError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "부분 식별 정보, 소속, 콜 데이터, 지표, 점수 또는 순위를 대신 표시하지 않습니다.",
    );
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
