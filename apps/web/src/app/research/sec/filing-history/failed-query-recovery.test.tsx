import { fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LocaleProvider } from "@/components/locale-provider";
import { renderWithLocale } from "@/test/render-with-locale";
import SecFilingHistoryAuditError from "./error";
import SecFilingHistoryAuditNotFound from "./not-found";
import { FailedQueryRecovery } from "./failed-query-recovery";

const navigation = vi.hoisted(() => ({ useSearchParams: vi.fn() }));
const i18n = vi.hoisted(() => ({ getLocale: vi.fn() }));
vi.mock("next/navigation", async (original) => ({
  ...await original<typeof import("next/navigation")>(),
  useSearchParams: navigation.useSearchParams,
}));
vi.mock("@/lib/i18n/server", () => ({ getLocale: i18n.getLocale }));

const ID = "a".repeat(64);
const CUTOFF = "2026-09-08T00:00:00.000001Z";
function query(view = "summary") {
  return new URLSearchParams({ manifestId: ID, evaluationAsOf: CUTOFF, view,
    ...(view === "summary" ? {} : { page: "99", size: "1" }) });
}

describe("failed SEC query recovery", () => {
  beforeEach(() => {
    navigation.useSearchParams.mockReturnValue(query());
    i18n.getLocale.mockResolvedValue("ko");
    vi.stubGlobal("fetch", vi.fn());
  });
  afterEach(() => { vi.unstubAllGlobals(); });

  it.each(["ko", "en"] as const)("preserves attempted keys without asserting evidence in %s", (locale) => {
    for (const view of ["summary", "descriptors", "accessions", "occurrences"]) {
      navigation.useSearchParams.mockReturnValue(query(view));
      const rendered = renderWithLocale(<FailedQueryRecovery />, locale);
      const disclosure = rendered.container.querySelector("details")!;
      expect(disclosure).not.toHaveAttribute("open");
      fireEvent.click(disclosure.querySelector("summary")!);
      expect(disclosure).toHaveTextContent(locale === "ko" ? "확인된 증거가 아닙니다" : "not verified evidence");
      expect(disclosure).toHaveTextContent(locale === "ko" ? "입력한 조회 키" : "Attempted lookup keys");
      expect(disclosure).not.toHaveTextContent(locale === "ko" ? "알려진 증거 식별자" : "Known evidence identity");
      const form = within(disclosure).getByRole("form");
      expect(form).toHaveAttribute("action", "/research/sec/filing-history");
      expect(form).toHaveAttribute("method", "get");
      expect([...new FormData(form as HTMLFormElement).entries()]).toEqual([
        ["manifestId", ID], ["evaluationAsOf", CUTOFF], ["view", "summary"],
      ]);
      expect(screen.queryByText(ID)).not.toBeInTheDocument();
      expect(screen.queryByText("DEMO", { exact: true })).not.toBeInTheDocument();
      expect(screen.queryByText("LIVE", { exact: true })).not.toBeInTheDocument();
      expect(screen.queryByRole("table")).not.toBeInTheDocument();
      expect(screen.queryByRole("link")).not.toBeInTheDocument();
      fireEvent.change(within(form).getByLabelText("Manifest ID"), { target: { value: "b".repeat(64) } });
      expect(fetch).not.toHaveBeenCalled();
      rendered.unmount();
    }
  });

  it.each([
    ["missing router", null], ["bare locator", ""],
    ["missing cutoff", `manifestId=${ID}`],
    ["duplicate ID", `${query()}&manifestId=${ID}`],
    ["duplicate cutoff", `${query()}&evaluationAsOf=${CUTOFF}`],
    ["duplicate view", `${query()}&view=summary`],
    ["extra key", `${query()}&latest=true`],
    ["prototype key", `${query()}&__proto__=ignored`],
    ["uppercase", `manifestId=${"A".repeat(64)}&evaluationAsOf=${CUTOFF}`],
    ["long ID", `manifestId=${"a".repeat(65)}&evaluationAsOf=${CUTOFF}`],
    ["invalid calendar", `manifestId=${ID}&evaluationAsOf=2026-02-29T00:00:00Z`],
    ["non-UTC", `manifestId=${ID}&evaluationAsOf=2026-09-08T09:00:00%2B09:00`],
    ["precision", `manifestId=${ID}&evaluationAsOf=2026-09-08T00:00:00.0000001Z`],
    ["whitespace", `manifestId=%20${ID}&evaluationAsOf=${CUTOFF}`],
    ["summary pagination", `${query()}&page=0`],
    ["duplicate page", `${query("descriptors")}&page=99`],
  ])("does not select or echo a %s query", (_name, search) => {
    navigation.useSearchParams.mockReturnValue(search === null ? null : new URLSearchParams(search));
    const { container } = renderWithLocale(<FailedQueryRecovery />);
    expect(container).toBeEmptyDOMElement();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("discards dirty inputs on URL changes and removes recovery on a bare URL", () => {
    const rendered = renderWithLocale(<FailedQueryRecovery />);
    fireEvent.click(rendered.container.querySelector("summary")!);
    fireEvent.change(screen.getByLabelText("Manifest ID"), { target: { value: "unsent" } });
    const next = query("occurrences");
    next.set("manifestId", "b".repeat(64));
    navigation.useSearchParams.mockReturnValue(next);
    rendered.rerender(<LocaleProvider locale="ko"><FailedQueryRecovery /></LocaleProvider>);
    expect(rendered.container.querySelector("details")).not.toHaveAttribute("open");
    expect(screen.getByLabelText("Manifest ID")).toHaveValue("b".repeat(64));
    navigation.useSearchParams.mockReturnValue(new URLSearchParams());
    rendered.rerender(<LocaleProvider locale="ko"><FailedQueryRecovery /></LocaleProvider>);
    expect(rendered.container).toBeEmptyDOMElement();
  });

  it.each(["ko", "en"] as const)("keeps existing absence and error controls in %s", async (locale) => {
    i18n.getLocale.mockResolvedValue(locale);
    const absent = renderWithLocale(await SecFilingHistoryAuditNotFound(), locale);
    expect(absent.container.querySelector("details")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: locale === "ko" ? "정확 증거 조회로 돌아가기" : "Return to exact evidence lookup" }))
      .toHaveAttribute("href", "/research/sec/filing-history");
    absent.unmount();
    const reset = vi.fn();
    const failed = renderWithLocale(<SecFilingHistoryAuditError error={new Error("private upstream message")} reset={reset} />, locale);
    expect(failed.container.querySelector("details")).toBeInTheDocument();
    expect(screen.queryByText("private upstream message")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: locale === "ko" ? "다시 시도" : "Try again" }));
    expect(reset).toHaveBeenCalledOnce();
    expect(fetch).not.toHaveBeenCalled();
  });
});
