import { fireEvent, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SCREENER_SHELL_STATE } from "@/lib/screener-shell-state";
import { renderWithLocale } from "@/test/render-with-locale";
import ScreenerError from "./error";
import ScreenerLoading from "./loading";
import ScreenerNotFound from "./not-found";
import ScreenerPage, { isQueryFreeScreenerRequest } from "./page";

const i18n = vi.hoisted(() => ({ getLocale: vi.fn() }));

vi.mock("@/lib/i18n/server", () => ({ getLocale: i18n.getLocale }));

function page(searchParams: Record<string, string | string[] | undefined> = {}) {
  return ScreenerPage({ searchParams: Promise.resolve(searchParams) });
}

describe("ScreenerPage", () => {
  beforeEach(() => {
    i18n.getLocale.mockReset();
    i18n.getLocale.mockResolvedValue("ko");
  });

  it("renders Korean-default policy UI with the exact deferred application state", async () => {
    renderWithLocale(await page());

    expect(screen.getByRole("heading", {
      name: "과거 주식 스크리닝은 연기됐습니다.",
    })).toBeInTheDocument();
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();

    const navigation = screen.getByRole("navigation", { name: "주요 탐색" });
    expect(within(navigation).getAllByRole("link").map((link) => link.textContent)).toEqual([
      "대시보드",
      "시장",
      "콜 기록",
      "기관",
      "애널리스트",
      "시장 지도",
      "스크리너",
      "방법론",
      "SEC 증거",
    ]);
    expect(within(navigation).getByRole("link", { name: "스크리너" }))
      .toHaveAttribute("aria-current", "page");

    const region = screen.getByRole("region", { name: "과거 스크리닝 게시 상태" });
    expect(region).toHaveAttribute("tabindex", "0");
    const policy = within(region).getByLabelText("스크리너 제품 제공 정책");
    expect(within(policy).getByText("제품 제공 정책 · 픽스처 증거 아님", { exact: true }))
      .toBeVisible();
    expect(policy).toHaveTextContent("과거 가격 바, 시점 기준 기능 카탈로그");
    expect(policy).toHaveTextContent("완료된 스크리닝 결과, 로딩 상태 및 경로 오류와 구분");
    expect(policy).toHaveTextContent("P3 작업");
    expect(policy).toHaveTextContent("P5 작업");

    const status = within(region).getByRole("status", { name: "연기된 스크리너 상태" });
    expect([...status.querySelectorAll("dt")].map((term) => term.textContent)).toEqual([
      "데이터 모드",
      "범위",
      "상태",
      "사유",
      "누락 표시",
    ]);
    expect([...status.querySelectorAll("dd")].map((definition) => definition.textContent)).toEqual([
      SCREENER_SHELL_STATE.dataMode,
      SCREENER_SHELL_STATE.scope,
      SCREENER_SHELL_STATE.status,
      SCREENER_SHELL_STATE.reasonCode,
      SCREENER_SHELL_STATE.missingDisplay,
    ]);
    expect(within(region).getByRole("note")).toHaveTextContent(
      "NA는 게시되지 않은 기능 상태를 기록합니다",
    );

    const adjacent = within(region).getByRole("navigation", { name: "인접 증거 경로" });
    expect(within(adjacent).getByText("별도 증거 화면 · 스크리너 출력 아님", { exact: true }))
      .toBeVisible();
    expect(within(adjacent).getByRole("link", { name: "기록된 콜 증거 열기" }))
      .toHaveAttribute("href", "/calls");
    expect(within(adjacent).getByRole("link", { name: "방법론 정의 열기" }))
      .toHaveAttribute("href", "/methodology");

    expect(region.querySelector(
      "time, [datetime], form, button, input, select, textarea, table, [role=row], "
      + "[role=grid], canvas, svg, ol, ul, .metric-grid",
    )).toBeNull();
  });

  it("renders English cookie UI without changing the five canonical state values", async () => {
    i18n.getLocale.mockResolvedValue("en");
    renderWithLocale(await page(), "en");

    expect(screen.getByRole("heading", {
      name: "Historical equity screening is deferred.",
    })).toBeInTheDocument();
    const status = screen.getByRole("status", { name: "Deferred screener state" });
    expect([...status.querySelectorAll("dd")].map((definition) => definition.textContent)).toEqual(
      Object.values(SCREENER_SHELL_STATE),
    );
  });

  it("accepts only a keyless request and fails closed for every query shape", async () => {
    expect(isQueryFreeScreenerRequest({})).toBe(true);
    expect(isQueryFreeScreenerRequest({ filter: "" })).toBe(false);
    expect(isQueryFreeScreenerRequest({ filter: ["value", "other"] })).toBe(false);
    expect(isQueryFreeScreenerRequest({ status: "P8_DEFERRED" })).toBe(false);
    expect(isQueryFreeScreenerRequest({ unknown: "value" })).toBe(false);

    await expect(page({ filter: "" })).rejects.toThrow(/404/);
    await expect(page({ filter: ["value", "other"] })).rejects.toThrow(/404/);
    await expect(page({ status: "P8_DEFERRED" })).rejects.toThrow(/404/);
    await expect(page({ unknown: "value" })).rejects.toThrow(/404/);
    expect(i18n.getLocale).not.toHaveBeenCalled();
  });

  it("localizes a mode-neutral unsupported request without leaking supported state", async () => {
    renderWithLocale(await ScreenerNotFound());

    const main = screen.getByRole("main");
    expect(within(main).getByText("지원하지 않는 스크리너 요청", { exact: true }))
      .toBeInTheDocument();
    expect(within(main).getByRole("heading", {
      name: "이 스크리너 요청은 게시되지 않았습니다.",
    })).toBeInTheDocument();
    expect(within(main).queryByText("DEMO", { exact: true })).not.toBeInTheDocument();
    for (const value of Object.values(SCREENER_SHELL_STATE)) {
      expect(within(main).queryByText(value, { exact: true })).not.toBeInTheDocument();
    }
    expect(within(main).queryByRole("navigation", { name: "주요 탐색" }))
      .not.toBeInTheDocument();
    expect(within(main).queryByRole("form")).not.toBeInTheDocument();
    expect(within(main).queryByRole("table")).not.toBeInTheDocument();
    expect(main.querySelector("input, select, canvas, svg, .metric-grid")).toBeNull();

    const actions = main.querySelector<HTMLElement>(".state-actions");
    expect(actions).not.toBeNull();
    expect(within(actions!).getAllByRole("link").map((link) => link.getAttribute("href")))
      .toEqual(["/calls", "/methodology"]);
  });

  it("keeps Korean DEMO navigation and no-fallback loading and error states", async () => {
    const loading = renderWithLocale(await ScreenerLoading());

    expect(screen.getByText("DEMO 애플리케이션 정책을 불러오는 중…").closest("div"))
      .toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "스크리너" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    loading.unmount();

    const reset = vi.fn();
    renderWithLocale(<ScreenerError error={new Error("policy failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "픽스처, 소스, 필터, 결과, 차트 또는 수치 값을 대신 표시하지 않습니다.",
    );
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
