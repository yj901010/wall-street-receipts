import { fireEvent, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Locale } from "@/lib/i18n/config";
import {
  CALL_LIST_METADATA_NOT_EXPOSED_REASON,
  type CallListProvider,
} from "@/lib/providers/call-list-provider";
import type { CallListSearchParams } from "@/lib/providers/call-list-query";
import type { CallsQuery } from "@/lib/providers/calls-provider";
import { FixtureCallListProvider } from "@/lib/providers/fixture-call-list-provider";
import { renderWithLocale } from "@/test/render-with-locale";
import CallsError from "./error";
import CallsLoading from "./loading";
import CallsPage from "./page";

const i18nServer = vi.hoisted(() => ({
  getLocale: vi.fn(),
}));

const providers = vi.hoisted(() => ({
  callListProvider: vi.fn(),
}));

vi.mock("@/lib/i18n/server", () => ({
  getLocale: i18nServer.getLocale,
}));

vi.mock("@/lib/providers/call-list-provider.server", () => ({
  callListProvider: providers.callListProvider,
}));

function apiModeProvider(): CallListProvider {
  const fixture = new FixtureCallListProvider();
  return {
    async list(query) {
      const snapshot = await fixture.list(query);
      return {
        ...snapshot,
        datasetEvidence: {
          availability: "NOT_EXPOSED",
          reason: CALL_LIST_METADATA_NOT_EXPOSED_REASON,
          asOf: null,
          source: null,
          disclaimer: null,
        },
      };
    },
  };
}

async function renderPage(
  searchParams: CallListSearchParams = {},
  locale: Locale = "ko",
) {
  i18nServer.getLocale.mockResolvedValue(locale);
  return renderWithLocale(
    await CallsPage({ searchParams: Promise.resolve(searchParams) }),
    locale,
  );
}

describe("CallsPage", () => {
  beforeEach(() => {
    i18nServer.getLocale.mockReset();
    i18nServer.getLocale.mockResolvedValue("ko");
    providers.callListProvider.mockReset();
    providers.callListProvider.mockReturnValue(new FixtureCallListProvider());
  });

  it("renders the Korean-default canonical DEMO ledger and accessible filters", async () => {
    await renderPage();

    expect(screen.getByRole("heading", { name: "애널리스트 콜" })).toBeInTheDocument();
    expect(screen.getByRole("form", { name: "애널리스트 콜 필터" })).toBeInTheDocument();
    expect(screen.getByLabelText("티커 (대소문자 구분 없음)")).toBeInTheDocument();
    expect(screen.getByLabelText("자산 ID (대소문자 정확히 일치)")).toBeInTheDocument();
    expect(screen.getByLabelText("기관 ID (대소문자 정확히 일치)")).toBeInTheDocument();
    expect(screen.getByLabelText("애널리스트 ID (대소문자 정확히 일치)")).toBeInTheDocument();
    expect(screen.getAllByText("DEMO").length).toBeGreaterThan(0);
    expect(screen.getByText(/현재 응답 페이지에 반환된 콜만 요약/)).toBeInTheDocument();
    expect(screen.getByText("제공됨")).toBeInTheDocument();

    const table = screen.getByRole("table", { name: "필터링된 애널리스트 콜 이벤트" });
    const sourceLink = within(table).getByRole("link", { name: "DEMO equity interview" });
    const callRow = sourceLink.closest("tr");

    expect(callRow).not.toBeNull();
    expect(within(callRow!).getByText("Goldman Sachs")).toBeInTheDocument();
    expect(within(callRow!).getByText("NVDA")).toBeInTheDocument();
    expect(within(callRow!).getByText("2026-08-11 23:20:00 KST")).toHaveAttribute(
      "datetime",
      "2026-08-11T14:20:00Z",
    );
    expect(within(callRow!).getByText("$210.00 → $235.00")).toBeInTheDocument();
    expect(sourceLink).toHaveAttribute("href", "/calls/demo-call-002#source");
  });

  it("renders English UI from an English server cookie without changing finance evidence", async () => {
    await renderPage({}, "en");

    expect(screen.getByRole("heading", { name: "Analyst calls" })).toBeInTheDocument();
    expect(screen.getByRole("form", { name: "Filter analyst calls" })).toBeInTheDocument();
    expect(screen.getByText("AVAILABLE")).toBeInTheDocument();
    expect(screen.getByLabelText("Ticker (case-insensitive)")).toBeInTheDocument();
    expect(screen.getByLabelText("Asset ID (exact case)")).toBeInTheDocument();
    const table = screen.getByRole("table", { name: "Filtered analyst call events" });
    const sourceLink = within(table).getByRole("link", { name: "DEMO equity interview" });
    const callRow = sourceLink.closest("tr");

    expect(callRow).not.toBeNull();
    expect(within(callRow!).getByText("Goldman Sachs")).toBeInTheDocument();
    expect(within(callRow!).getByText("NVDA")).toBeInTheDocument();
    expect(within(callRow!).getByText("2026-08-11 23:20:00 KST")).toHaveAttribute(
      "datetime",
      "2026-08-11T14:20:00Z",
    );
    expect(within(callRow!).getByText("$210.00 → $235.00")).toBeInTheDocument();
    expect(sourceLink).toHaveAttribute("href", "/calls/demo-call-002#source");
  });

  it("renders zero-based provider pagination as Korean human page numbers", async () => {
    await renderPage({ size: "1", page: "0" });

    expect(screen.getByText(/1\/3페이지/)).toBeInTheDocument();
    const pagination = screen.getByRole("navigation", { name: "콜 목록 페이지" });
    expect(within(pagination).getByText("이전")).toHaveAttribute("aria-disabled", "true");
    expect(within(pagination).getByRole("link", { name: "다음" })).toHaveAttribute(
      "href",
      "/calls?size=1&page=1",
    );
    expect(screen.getByLabelText("행 수")).toHaveValue(1);
  });

  it("renders an explicit Korean empty state without substitute records", async () => {
    await renderPage({ ticker: "TSLA" });

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("이 응답에는 필터와 일치하는 항목이 없습니다.");
    expect(screen.getByRole("status")).not.toHaveTextContent("요청한 응답 페이지에는 항목이 없습니다.");
    expect(screen.getByText(/데이터셋 완전성을 주장하지 않습니다/)).toBeInTheDocument();
    expect(screen.getAllByText("NA").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText(/결과 페이지 0개 · 요청 페이지 1/)).toBeInTheDocument();
  });

  it("renders API dataset metadata as explicitly NOT_EXPOSED without row-derived inference", async () => {
    providers.callListProvider.mockReturnValue(apiModeProvider());
    await renderPage();

    const dataset = screen.getByRole("region", { name: "콜 데이터셋 출처 정보" });
    expect(within(dataset).getAllByText("NA")).toHaveLength(2);
    expect(within(dataset).getByText("NOT_EXPOSED")).toBeInTheDocument();
    expect(within(dataset).getByText(/LIST_API_HAS_NO_DATASET_METADATA/)).toBeInTheDocument();
    expect(within(dataset).getByText(/현재 페이지에서 이를 추론하지 않습니다/)).toBeInTheDocument();
  });

  it("renders the English API metadata-not-exposed state without inferred dataset evidence", async () => {
    providers.callListProvider.mockReturnValue(apiModeProvider());
    await renderPage({}, "en");

    const dataset = screen.getByRole("region", { name: "Call dataset provenance" });
    expect(within(dataset).getAllByText("NA")).toHaveLength(2);
    expect(within(dataset).getByText("NOT_EXPOSED")).toBeInTheDocument();
    expect(within(dataset).getByText(/LIST_API_HAS_NO_DATASET_METADATA/)).toBeInTheDocument();
    expect(within(dataset).getByText(/does not infer them from returned rows/)).toBeInTheDocument();
  });

  it("selects one provider and performs exactly one successful page read with the parsed query", async () => {
    const fixture = new FixtureCallListProvider();
    const list = vi.fn(fixture.list.bind(fixture));
    providers.callListProvider.mockReturnValue({ list });

    await renderPage({
      assetId: "asset-nvda",
      ticker: "nvda",
      dataMode: "DEMO",
      from: "2026-08-11",
      to: "2026-08-11",
      page: "0",
      size: "1",
      sort: "capturedAt",
      order: "asc",
    });

    expect(providers.callListProvider).toHaveBeenCalledOnce();
    expect(list).toHaveBeenCalledOnce();
    expect(list).toHaveBeenCalledWith(expect.objectContaining({
      assetId: "asset-nvda",
      ticker: "nvda",
      dataMode: "DEMO",
      from: "2026-08-10T15:00:00.000Z",
      to: "2026-08-11T15:00:00.000Z",
      page: 0,
      size: 1,
      sort: "capturedAt",
      order: "asc",
    }));
  });

  it("renders honest out-of-range page echo and arbitrary accepted size values", async () => {
    await renderPage({ page: "99", size: "7" });

    expect(screen.getByLabelText("행 수")).toHaveValue(7);
    expect(screen.getByText(/요청 페이지 100 · 전체 1페이지/)).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("요청한 응답 페이지에는 항목이 없습니다.");
    expect(screen.getByRole("status")).toHaveTextContent("필터와 일치하는 이벤트는 3건");
    expect(screen.getByRole("status")).not.toHaveTextContent("필터와 일치하는 항목이 없습니다.");
  });

  it("distinguishes an English out-of-range page from a zero-match response", async () => {
    await renderPage({ page: "99", size: "7" }, "en");

    expect(screen.getByRole("status")).toHaveTextContent(
      "The requested response page contains no items.",
    );
    expect(screen.getByRole("status")).toHaveTextContent("3 events match the filters");
    expect(screen.getByRole("status")).not.toHaveTextContent(
      "This response contains no items matching these filters.",
    );
  });

  it("rejects invalid or duplicate URL state before selecting or reading a provider", async () => {
    await expect(CallsPage({
      searchParams: Promise.resolve({ ticker: ["NVDA", "TSLA"] }),
    })).rejects.toThrow(/duplicate values are not allowed/);
    expect(providers.callListProvider).not.toHaveBeenCalled();

    await expect(CallsPage({
      searchParams: Promise.resolve({ dataMode: "REALTIME" }),
    })).rejects.toThrow(/only DEMO/);
    expect(providers.callListProvider).not.toHaveBeenCalled();
  });

  it("propagates provider failure to the route error boundary instead of showing an empty response", async () => {
    const list = vi.fn(async () => { throw new Error("API unavailable"); });
    providers.callListProvider.mockReturnValue({ list });

    await expect(CallsPage({ searchParams: Promise.resolve({}) }))
      .rejects.toThrow("API unavailable");
    expect(list).toHaveBeenCalledOnce();
  });

  it("renders long returned-page opaque provenance evidence without truncating its text", async () => {
    const fixture = new FixtureCallListProvider();
    const longId = `provenance-${"x".repeat(116)}`;
    providers.callListProvider.mockReturnValue({
      async list(query?: CallsQuery) {
        const snapshot = await fixture.list(query);
        return {
          ...snapshot,
          returnedPageEvidence: {
            ...snapshot.returnedPageEvidence,
            callProvenanceIds: [longId],
          },
        };
      },
    });

    await renderPage();
    expect(screen.getByText(longId)).toBeInTheDocument();
  });

  it("localizes loading and recoverable client error states", async () => {
    renderWithLocale(await CallsLoading(), "ko");
    expect(screen.getByRole("heading", { name: "애널리스트 콜을 불러오는 중…" })).toBeInTheDocument();

    const reset = vi.fn();
    renderWithLocale(
      <CallsError error={new Error("fixture failed")} reset={reset} />,
      "ko",
    );
    expect(screen.getByRole("heading", { name: "콜 증거를 읽을 수 없습니다." })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
