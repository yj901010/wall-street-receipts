import { fireEvent, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { FixtureInstitutionDirectoryProvider } from "@/lib/providers";
import { renderWithLocale } from "@/test/render-with-locale";
import InstitutionsError from "./error";
import { InstitutionDirectory } from "./institution-directory";
import InstitutionsLoading from "./loading";
import InstitutionsPage from "./page";

const i18n = vi.hoisted(() => ({ getLocale: vi.fn() }));

vi.mock("@/lib/i18n/server", () => ({ getLocale: i18n.getLocale }));

describe("InstitutionsPage", () => {
  beforeEach(() => {
    i18n.getLocale.mockReset();
    i18n.getLocale.mockResolvedValue("ko");
  });

  it("renders the Korean-default identity directory without changing canonical evidence", async () => {
    renderWithLocale(await InstitutionsPage());

    expect(screen.getByRole("heading", {
      name: "기관을 순위표가 아닌 기록된 증거로 봅니다.",
    })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "기관" })).toHaveAttribute("aria-current", "page");

    const provenance = screen.getByLabelText("기관 식별 픽스처 출처");
    expect(within(provenance).getByText("1.0.0")).toBeInTheDocument();
    expect(within(provenance).getByText("v1")).toBeInTheDocument();
    expect(within(provenance).getByText("fixture-master-data-v1")).toBeInTheDocument();
    expect(within(provenance).getByText("DEMO")).toBeInTheDocument();
    expect(within(provenance).getAllByText("2026-08-18 09:00:00 KST")).toHaveLength(2);

    const sourceEvidence = screen.getByLabelText("기관 소스 증거");
    for (const value of [
      "LOCAL_SPECIFICATION",
      "INTERNAL_DEMO",
      "true",
      "docs/fixtures/institutions.json",
      "docs/docs/DOMAIN_MODEL.md",
    ]) {
      expect(within(sourceEvidence).getByText(value)).toBeInTheDocument();
    }

    const policy = screen.getByLabelText("기관 디렉터리 정책");
    expect(within(policy).getByText("제품 정책 · 픽스처 증거 아님", { exact: true })).toBeVisible();
    expect(policy).toHaveTextContent("순위가 아닙니다.");
    expect(policy).toHaveTextContent("현재 운영 상태를 주장하지 않습니다");
    expect(policy).toHaveTextContent("보증이나 투자 조언이 아닙니다");
    expect(screen.getByText("2 DEMO 픽스처 레코드 · 범위를 주장하지 않음"))
      .toBeInTheDocument();

    const region = screen.getByRole("region", { name: "기관 식별 정보 표" });
    expect(region).toHaveAttribute("tabindex", "0");
    const table = within(region).getByRole("table", {
      name: "정규 기관 식별 정보와 수집된 증거",
    });
    expect(within(table).getAllByRole("columnheader")).toHaveLength(9);

    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    expect(rows.map((row) => row.querySelector('[data-label="기관"] strong')?.textContent))
      .toEqual(["Goldman Sachs", "JPMorgan"]);
    expect(rows.map((row) => row.querySelector('[data-label="기관"] .mono')?.textContent))
      .toEqual(["inst-gs", "inst-jpm"]);

    for (const [index, institution] of [
      { id: "inst-gs", name: "Goldman Sachs", slug: "goldman-sachs" },
      { id: "inst-jpm", name: "JPMorgan", slug: "jpmorgan" },
    ].entries()) {
      const row = rows[index];
      expect(within(row).getByText(institution.slug, { exact: true })).toBeInTheDocument();
      expect(within(row).getByText("US")).toBeInTheDocument();
      expect(within(row).getByText("true")).toBeInTheDocument();
      expect(within(row).getByText("DEMO")).toBeInTheDocument();
      expect(within(row).getByText("2026-08-10 09:00:00 KST")).toBeInTheDocument();
      expect(within(row).getByText("2026-08-18 09:00:00 KST")).toBeInTheDocument();
      expect(within(row).getByText("fixture-master-data-v1")).toBeInTheDocument();
      expect(within(row).getByRole("link", {
        name: `다음 기관으로 콜 원장 필터링: ${institution.name}`,
      })).toHaveAttribute("href", `/calls?institutionId=${institution.id}`);
    }
    expect(screen.queryByText("Demo Analyst A")).not.toBeInTheDocument();
    expect(screen.queryByText("Demo Analyst B")).not.toBeInTheDocument();
  });

  it("renders English cookie UI with byte-stable identities and times", async () => {
    i18n.getLocale.mockResolvedValue("en");
    renderWithLocale(await InstitutionsPage(), "en");

    expect(screen.getByRole("heading", {
      name: "Institutions as recorded evidence, not a leaderboard.",
    })).toBeInTheDocument();
    const table = screen.getByRole("table", {
      name: "Canonical institution identities and their captured evidence",
    });
    expect(within(table).getByText("Goldman Sachs")).toBeInTheDocument();
    expect(within(table).getAllByText("2026-08-10 09:00:00 KST")).toHaveLength(2);
    expect(within(table).getAllByText("fixture-master-data-v1")).toHaveLength(2);
  });

  it("renders a valid Korean empty identity catalog without placeholders", async () => {
    const snapshot = await new FixtureInstitutionDirectoryProvider().directory();

    renderWithLocale(<InstitutionDirectory snapshot={{ ...snapshot, institutions: [] }} locale="ko" />);

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "대체 식별 정보, 범위 주장, 점수, 정확도 또는 순위를 생성하지 않았습니다.",
    );
    expect(screen.getByText("0 DEMO 픽스처 레코드 · 범위를 주장하지 않음"))
      .toBeInTheDocument();
  });

  it("keeps Korean DEMO navigation in loading and retryable error states", async () => {
    const loading = renderWithLocale(await InstitutionsLoading());

    expect(screen.getByText("기관 증거를 불러오는 중…").closest("main")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "기관" })).toHaveAttribute("aria-current", "page");
    loading.unmount();

    const reset = vi.fn();
    renderWithLocale(<InstitutionsError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "부분 식별 정보, 대체 기관, 점수, 정확도 또는 순위를 대신 표시하지 않습니다.",
    );
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
