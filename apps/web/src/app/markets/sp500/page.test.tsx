import { fireEvent, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Locale } from "@/lib/i18n/config";
import {
  FixtureCallsProvider,
  FixtureSp500HistoryProvider,
  type Sp500HistorySnapshot,
} from "@/lib/providers";
import { renderWithLocale } from "@/test/render-with-locale";
import Sp500HistoryError from "./error";
import { KeyboardScrollRegion } from "./keyboard-scroll-region";
import Sp500HistoryLoading from "./loading";
import Sp500HistoryPage from "./page";
import { Sp500CallHistory } from "./sp500-call-history";

const i18nServer = vi.hoisted(() => ({
  getLocale: vi.fn(),
}));

vi.mock("@/lib/i18n/server", () => ({
  getLocale: i18nServer.getLocale,
}));

async function fixtureSnapshot() {
  return new FixtureSp500HistoryProvider(new FixtureCallsProvider()).history();
}

async function renderPage(locale: Locale = "ko") {
  i18nServer.getLocale.mockResolvedValue(locale);
  return renderWithLocale(await Sp500HistoryPage(), locale);
}

describe("Sp500HistoryPage", () => {
  beforeEach(() => {
    i18nServer.getLocale.mockReset();
    i18nServer.getLocale.mockResolvedValue("ko");
  });

  it("renders the canonical recorded DEMO event with scoped catalog and source evidence", async () => {
    await renderPage();

    expect(screen.getByRole("heading", {
      name: "기록된 S&P 500 전망 콜 이벤트",
    })).toBeInTheDocument();
    const navigation = screen.getByRole("navigation", { name: "주요 탐색" });
    expect(within(navigation).getAllByRole("link")).toHaveLength(8);
    expect(within(navigation).getByRole("link", { name: "시장" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(within(navigation).getByRole("link", { name: "시장" })).toHaveAttribute(
      "href",
      "/market",
    );

    const provenance = screen.getByLabelText("S&P 500 콜 이력 출처 정보");
    expect(within(provenance).getByText("콜 카탈로그 기준 시각", { exact: true }))
      .toBeInTheDocument();
    expect(within(provenance).getByText("2026-08-18T00:00:00Z", { exact: true }))
      .toHaveAttribute("datetime", "2026-08-18T00:00:00Z");
    expect(within(provenance).getByText("fixture-analyst-calls-v1", { exact: true }))
      .toBeInTheDocument();
    expect(within(provenance).getByText("SPX", { exact: true })).toBeInTheDocument();
    expect(within(provenance).getByText("DEMO", { exact: true })).toBeInTheDocument();

    const history = screen.getByRole("region", { name: "S&P 500 콜 이벤트 이력" });
    expect(within(history).getByText(
      "1개 행 표시 · 일치하는 DEMO 이벤트 1건 · 불완전한 픽스처 범위",
      { exact: true },
    )).toBeInTheDocument();
    const policy = within(history).getByLabelText("S&P 500 콜 이력 정책");
    expect(within(policy).getByText("표시 정책 · 픽스처 증거 아님", { exact: true }))
      .toBeVisible();
    expect(policy).toHaveTextContent("정정이나 개정 내용을 현재 유효 상태로 합치지 않습니다");
    expect(policy).toHaveTextContent(
      "현재 추천, 가격, 컨센서스 또는 성과가 아닙니다",
    );
    expect(policy).toHaveTextContent("S&P 500 범위, 신뢰도, 완전성 또는 시장 추세를 주장하지 않습니다");

    const queryEvidence = within(history).getByLabelText("S&P 500 이력 쿼리 증거");
    expect(queryEvidence).toHaveTextContent("S&P 500 Index");
    expect(queryEvidence).toHaveTextContent("asset-spx");
    expect(queryEvidence).toHaveTextContent("SPX · INDEX");
    expect(queryEvidence).toHaveTextContent("asset-spx · page 0 · size 25");
    expect(queryEvidence).toHaveTextContent("이벤트 시각 내림차순 · 동일 시각은 콜 ID 오름차순");
    expect(queryEvidence).toHaveTextContent("1 / 1");

    const table = within(history).getByRole("table", {
      name: "원본 확정 S&P 500 DEMO 애널리스트 콜 이벤트",
    });
    const disclaimer = within(history).getByText(
      "Synthetic DEMO events only; no record represents a real JPMorgan or Goldman Sachs analyst statement.",
      { exact: true },
    );
    expect(disclaimer.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING)
      .toBeTruthy();
    expect(within(table).getAllByRole("columnheader")).toHaveLength(8);
    expect(within(table).queryByRole("columnheader", {
      name: /market price|return|alpha|hit|accuracy|rank|consensus|outcome/i,
    })).not.toBeInTheDocument();

    const row = within(table).getAllByRole("row")[1];
    expect(within(row).getByRole("link", { name: "2026-08-10T12:00:00Z" }))
      .toHaveAttribute("href", "/calls/demo-call-001");
    expect(within(row).getByText("demo-call-001", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("JPMorgan", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("Demo Analyst A", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("BULLISH", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("DEMO Bullish", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("$7,800.00 → $8,000.00", { exact: true }))
      .toBeInTheDocument();
    expect(within(row).getByText("통화: USD", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("NA", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("ACTIVE", { exact: true })).toBeInTheDocument();
    expect(within(row).getByRole("link", { name: "DEMO index outlook" }))
      .toHaveAttribute("href", "/calls/demo-call-001#source");
    expect(within(row).getByText("DEMO Publisher · 검증 여부: false", { exact: true }))
      .toBeInTheDocument();
    expect(within(row).getAllByText("2026-08-10T12:03:00Z", { exact: true }))
      .toHaveLength(2);
    expect(within(row).getByText("DEMO · fixture-analyst-calls-v1", { exact: true }))
      .toBeInTheDocument();
    expect(within(row).queryByText("demo-source-001", { exact: true })).not.toBeInTheDocument();

    expect(within(history).getByRole("link", { name: "필터링된 콜 원장 열기" }))
      .toHaveAttribute("href", "/calls?assetId=asset-spx");
    expect(within(history).getByRole("link", { name: "시장 게시 상태로 돌아가기" }))
      .toHaveAttribute("href", "/market");
  });

  it("renders English UI without changing canonical dates, money, IDs, statuses, or sources", async () => {
    await renderPage("en");

    expect(screen.getByRole("heading", {
      name: "Recorded S&P 500 forecast-call events.",
    })).toBeInTheDocument();
    const history = screen.getByRole("region", { name: "S&P 500 call-event history" });
    const table = within(history).getByRole("table", {
      name: "Original committed S&P 500 DEMO analyst-call events",
    });
    const row = within(table).getAllByRole("row")[1];

    expect(within(row).getByRole("link", { name: "2026-08-10T12:00:00Z" }))
      .toHaveAttribute("href", "/calls/demo-call-001");
    expect(within(row).getByText("demo-call-001", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("BULLISH", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("DEMO Bullish", { exact: true })).toBeInTheDocument();
    expect(within(row).getByText("$7,800.00 → $8,000.00", { exact: true }))
      .toBeInTheDocument();
    expect(within(row).getByText("ACTIVE", { exact: true })).toBeInTheDocument();
    expect(within(row).getByRole("link", { name: "DEMO index outlook" }))
      .toHaveAttribute("href", "/calls/demo-call-001#source");
    expect(within(history).getByText(
      "Synthetic DEMO events only; no record represents a real JPMorgan or Goldman Sachs analyst statement.",
      { exact: true },
    )).toBeVisible();
  });

  it("renders honest empty paging and preserves visible microsecond evidence", async () => {
    const canonical = await fixtureSnapshot();
    const empty: Sp500HistorySnapshot = {
      ...canonical,
      items: [],
      page: {
        ...canonical.page,
        totalElements: 0,
        totalPages: 0,
        last: true,
      },
    };
    const emptyRender = renderWithLocale(
      <Sp500CallHistory locale="ko" snapshot={empty} />,
      "ko",
    );

    expect(screen.getByText(
      "0개 행 표시 · 일치하는 DEMO 이벤트 0건 · 불완전한 픽스처 범위",
      { exact: true },
    )).toBeInTheDocument();
    expect(screen.getByLabelText("S&P 500 이력 쿼리 증거")).toHaveTextContent("0 / 0");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "대체 전망, 목표가, 상태, 출처, 시장 가격 또는 성과를 만들지 않았습니다.",
    );
    emptyRender.unmount();

    const item = structuredClone(canonical.items[0]);
    item.call.eventTime = "2026-08-10T12:00:00.000001Z";
    item.call.processingTime = "2026-08-10T12:03:00.000002Z";
    item.call.capturedAt = "2026-08-10T12:03:00.000003Z";
    renderWithLocale(
      <Sp500CallHistory locale="ko" snapshot={{ ...canonical, items: [item] }} />,
      "ko",
    );

    for (const instant of [
      "2026-08-10T12:00:00.000001Z",
      "2026-08-10T12:03:00.000002Z",
      "2026-08-10T12:03:00.000003Z",
    ]) {
      expect(screen.getByText(instant, { exact: true })).toHaveAttribute("datetime", instant);
    }
  });

  it("distinguishes a full first page from the matching fixture total", async () => {
    const canonical = await fixtureSnapshot();
    const items = Array.from({ length: 25 }, (_, index) => {
      if (index === 0) return structuredClone(canonical.items[0]);
      const item = structuredClone(canonical.items[0]);
      const suffix = String(index + 1).padStart(3, "0");
      item.call.callId = `demo-call-future-${suffix}`;
      item.call.providerEventId = `fixture-call-future-${suffix}`;
      return item;
    });

    renderWithLocale(
      <Sp500CallHistory
        locale="ko"
        snapshot={{
          ...canonical,
          items,
          page: {
            ...canonical.page,
            totalElements: 26,
            totalPages: 2,
            last: false,
          },
        }}
      />,
      "ko",
    );

    expect(screen.getByText(
      "25개 행 표시 · 일치하는 DEMO 이벤트 26건 · 불완전한 픽스처 범위",
      { exact: true },
    )).toBeInTheDocument();
    expect(screen.getByLabelText("S&P 500 이력 쿼리 증거")).toHaveTextContent("1 / 2");
    expect(screen.getAllByRole("row")).toHaveLength(26);
  });

  it("renders every supported nullable row field as NA without borrowing a value", async () => {
    const canonical = await fixtureSnapshot();
    const item = structuredClone(canonical.items[0]);
    item.call.analystId = null;
    item.analyst = null;
    item.call.originalRating = null;
    item.call.previousTarget = null;
    item.call.target = null;
    item.call.currency = null;
    item.call.targetDate = null;
    item.source.document.publisher = null;

    renderWithLocale(
      <Sp500CallHistory locale="ko" snapshot={{ ...canonical, items: [item] }} />,
      "ko",
    );

    const row = screen.getAllByRole("row")[1];
    const identity = row.querySelector('[data-field="institution-analyst"]');
    const rating = row.querySelector('[data-field="direction-rating"]');
    const targets = row.querySelector('[data-field="stored-targets"]');
    const targetDate = row.querySelector('[data-field="target-date"]');
    const source = row.querySelector('[data-field="source-evidence"]');
    expect(identity?.querySelector(".cell-secondary")).toHaveTextContent(/^NA$/);
    expect(rating?.querySelector(".cell-secondary")).toHaveTextContent(/^NA$/);
    expect(targets).toHaveTextContent("NA → NA");
    expect(targets).toHaveTextContent("통화: NA");
    expect(targets).not.toHaveTextContent("$0");
    expect(targetDate).toHaveTextContent(/^NA$/);
    expect(source?.querySelector(".cell-secondary")).toHaveTextContent(
      /^NA · 검증 여부: false$/,
    );
  });

  it("moves only an overflowing focused table region with horizontal arrow keys", () => {
    renderWithLocale(
      <KeyboardScrollRegion ariaLabel="Test horizontal evidence" className="table-scroll">
        <button type="button">Nested evidence control</button>
      </KeyboardScrollRegion>,
      "ko",
    );
    const region = screen.getByRole("region", { name: "Test horizontal evidence" });
    Object.defineProperties(region, {
      clientWidth: { configurable: true, value: 400 },
      scrollWidth: { configurable: true, value: 1_000 },
    });
    region.scrollLeft = 0;

    expect(fireEvent.keyDown(region, { key: "ArrowRight" })).toBe(false);
    expect(region.scrollLeft).toBe(100);
    expect(fireEvent.keyDown(region, { key: "ArrowLeft" })).toBe(false);
    expect(region.scrollLeft).toBe(0);

    expect(fireEvent.keyDown(region, { key: "ArrowLeft" })).toBe(true);
    expect(fireEvent.keyDown(region, { key: "ArrowRight", shiftKey: true })).toBe(true);
    expect(fireEvent.keyDown(screen.getByRole("button"), { key: "ArrowRight" })).toBe(true);
    expect(region.scrollLeft).toBe(0);

    Object.defineProperty(region, "scrollWidth", { configurable: true, value: 400 });
    expect(fireEvent.keyDown(region, { key: "ArrowRight" })).toBe(true);
    expect(region.scrollLeft).toBe(0);
  });

  it("keeps DEMO navigation in accessible Korean loading and retryable error states", async () => {
    const loading = renderWithLocale(await Sp500HistoryLoading(), "ko");

    expect(screen.getByText("확정된 DEMO 콜 일부를 불러오는 중…").closest("main"))
      .toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "시장" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByText(/시장 가격, 차트, 목표가, 상태, 성과 또는 대체 행을 채우지 않습니다/))
      .toBeInTheDocument();
    loading.unmount();

    const reset = vi.fn();
    renderWithLocale(
      <Sp500HistoryError error={new Error("fixture failed")} reset={reset} />,
      "ko",
    );

    expect(screen.getByRole("alert")).toHaveTextContent(
      "일부 콜, 시장 스냅샷, 차트, 성과, 컨센서스 또는 애플리케이션 상수",
    );
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "시장" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "시장 게시 상태로 돌아가기" }))
      .toHaveAttribute("href", "/market");
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
