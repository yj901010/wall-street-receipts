import { fireEvent, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  FixtureSecManifestAuditProvider,
  SEC_MANIFEST_AUDIT_DEMO_QUERY,
} from "@/lib/providers/fixture-sec-manifest-audit-provider";
import type {
  SecManifestAuditProvider,
} from "@/lib/providers/sec-manifest-audit-provider";
import { renderWithLocale } from "@/test/render-with-locale";
import SecFilingHistoryAuditError from "./error";
import SecFilingHistoryAuditLoading from "./loading";
import SecFilingHistoryAuditNotFound from "./not-found";
import SecFilingHistoryAuditPage, { generateMetadata } from "./page";

const i18n = vi.hoisted(() => ({ getLocale: vi.fn() }));
const providers = vi.hoisted(() => ({ secManifestAuditProvider: vi.fn() }));

vi.mock("@/lib/i18n/server", () => ({ getLocale: i18n.getLocale }));
vi.mock("@/lib/providers/sec-manifest-audit-provider.server", () => ({
  secManifestAuditProvider: providers.secManifestAuditProvider,
}));

function raw(
  view = "summary",
  values: Record<string, string | string[] | undefined> = {},
) {
  return {
    ...SEC_MANIFEST_AUDIT_DEMO_QUERY,
    view,
    ...values,
  };
}

async function page(
  searchParams: Record<string, string | string[] | undefined> = {},
) {
  return SecFilingHistoryAuditPage({ searchParams: Promise.resolve(searchParams) });
}

function expectSecNavigation(locale: "ko" | "en" = "ko") {
  const navigation = screen.getByRole("navigation", {
    name: locale === "ko" ? "주요 탐색" : "Primary navigation",
  });
  const link = within(navigation).getByRole("link", {
    name: locale === "ko" ? "SEC 증거" : "SEC evidence",
  });
  expect(link).toHaveAttribute("href", "/research/sec/filing-history");
  expect(link).toHaveAttribute("aria-current", "page");
  expect(navigation.querySelectorAll('[aria-current="page"]')).toHaveLength(1);
}

describe("SecFilingHistoryAuditPage", () => {
  beforeEach(() => {
    i18n.getLocale.mockReset();
    i18n.getLocale.mockResolvedValue("ko");
    providers.secManifestAuditProvider.mockReset();
    providers.secManifestAuditProvider.mockReturnValue(new FixtureSecManifestAuditProvider());
  });

  it("renders a Korean exact-ID locator and labels the generated fixture as DEMO", async () => {
    renderWithLocale(await page());
    expectSecNavigation();

    expect(screen.getByRole("heading", { name: "SEC 제출 이력 manifest 감사" }))
      .toBeInTheDocument();
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    const form = screen.getByRole("form", {
      name: "정확한 manifest와 기준 시각을 입력하세요.",
    });
    expect(within(form).getByLabelText("Manifest ID")).toHaveAttribute(
      "pattern",
      "[0-9a-f]{64}",
    );
    expect(within(form).getByLabelText("평가 기준 원본 조회 키(UTC)"))
      .toHaveAttribute("type", "text");
    expect(form.querySelector('input[name="view"]')).toHaveValue("summary");
    expect(screen.getByText("합성 DEMO 예시")).toBeInTheDocument();
    expect(screen.getByText(/실제 SEC 관측이 아닙니다/)).toBeInTheDocument();

    const demo = screen.getByRole("link", { name: "합성 DEMO 요약 열기" });
    expect(demo.getAttribute("href")).toContain(`manifestId=${SEC_MANIFEST_AUDIT_DEMO_QUERY.manifestId}`);
    expect(demo.getAttribute("href")).toContain("view=summary");
  });

  it("rejects malformed or additive URL state without reading a manifest", async () => {
    const findExact = vi.fn();
    providers.secManifestAuditProvider.mockReturnValue({
      mode: "fixture",
      demoQuery: SEC_MANIFEST_AUDIT_DEMO_QUERY,
      syntheticDemoManifestId: SEC_MANIFEST_AUDIT_DEMO_QUERY.manifestId,
      findExact,
    } satisfies SecManifestAuditProvider);

    renderWithLocale(await page({ ...raw(), ticker: "NVDA" }));
    expect(screen.getByRole("alert")).toHaveTextContent(
      "조회 주소가 닫힌 문법과 맞지 않습니다.",
    );
    expect(findExact).not.toHaveBeenCalled();
    expect(screen.queryByText("NVDA")).not.toBeInTheDocument();
  });

  it.each(["fixture", "api"] as const)("opens the %s locator without reading or selecting evidence", async (mode) => {
    const findExact = vi.fn();
    providers.secManifestAuditProvider.mockReturnValue({
      mode,
      demoQuery: mode === "fixture" ? SEC_MANIFEST_AUDIT_DEMO_QUERY : null,
      syntheticDemoManifestId: mode === "fixture" ? SEC_MANIFEST_AUDIT_DEMO_QUERY.manifestId : null,
      findExact,
    } satisfies SecManifestAuditProvider);
    renderWithLocale(await page());
    expectSecNavigation();
    expect(findExact).not.toHaveBeenCalled();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByText("LIVE", { exact: true })).not.toBeInTheDocument();
    if (mode === "api") {
      expect(screen.queryByText("DEMO", { exact: true })).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "합성 DEMO 요약 열기" })).not.toBeInTheDocument();
    }
  });

  it("performs exactly one selected summary read and renders immutable disclosure tokens", async () => {
    const fixture = new FixtureSecManifestAuditProvider();
    const findExact = vi.fn(fixture.findExact.bind(fixture));
    providers.secManifestAuditProvider.mockReturnValue({
      mode: "fixture",
      demoQuery: fixture.demoQuery,
      syntheticDemoManifestId: fixture.syntheticDemoManifestId,
      findExact,
    });

    renderWithLocale(await page(raw()));
    expectSecNavigation();
    expect(findExact).toHaveBeenCalledOnce();
    expect(findExact).toHaveBeenCalledWith({
      ...SEC_MANIFEST_AUDIT_DEMO_QUERY,
      view: "summary",
      page: 0,
      size: 25,
    });
    expect(screen.getAllByText("ALL_ADVERTISED_DESCRIPTORS_SELECTED")).toHaveLength(2);
    expect(screen.getByText("ROOT_RELATIVE_SELECTED_REFERENCES_ONLY")).toBeInTheDocument();
    expect(screen.getAllByText("NOT_RESOLVED")).toHaveLength(3);
    expect(screen.getByText("NOT_CLAIMED")).toBeInTheDocument();
    expect(screen.getByText("합성 DEMO · 실제 SEC 자료 아님")).toBeInTheDocument();
    expect(screen.getAllByText("2026-08-25 12:30:00.123456 KST").length)
      .toBeGreaterThan(0);
  });

  it("preserves accession source order and labels conflict without choosing a winner", async () => {
    renderWithLocale(await page(raw("accessions", { page: "0", size: "25" })));
    const table = within(screen.getByRole("region", { name: "Accession occurrence 비교" }))
      .getByRole("table");
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows.map((row) => within(row).getAllByRole("cell")[4]?.textContent)).toEqual([
      "SINGLE_SOURCE_OCCURRENCE",
      "SINGLE_SOURCE_OCCURRENCE",
      "MULTIPLE_OCCURRENCES_EXACT_AGREEMENT",
      "MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT",
    ]);
    expect(screen.getByText(/이 화면은 승자나 canonical filing을 선택하지 않습니다/))
      .toBeInTheDocument();
  });

  it("shows canonical SEC document URIs as text rather than outbound links", async () => {
    renderWithLocale(await page(raw("occurrences", { page: "0", size: "25" })));
    const uri = "https://www.sec.gov/Archives/edgar/data/320193/000032019326000001/form10q.htm";
    expect(screen.getByText(uri)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: uri })).not.toBeInTheDocument();
    expect(screen.getByText("2026-08-21 05:00:00.123456 KST"))
      .toHaveAttribute("datetime", "2026-08-20T20:00:00.123456Z");
    expect(screen.getAllByText("NA").length).toBeGreaterThan(0);
  });

  it("renders an exact out-of-range page without changing totals or adding rows", async () => {
    renderWithLocale(await page(raw("descriptors", { page: "99", size: "1" })));
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "이 정확한 응답 페이지에 항목이 없습니다.",
    );
    expect(screen.getAllByText("요청 100 · 전체 2").length).toBeGreaterThan(0);
  });

  it("keeps API presentation mode-neutral and does not relabel it LIVE or DEMO", async () => {
    const fixture = new FixtureSecManifestAuditProvider();
    const apiLike: SecManifestAuditProvider = {
      mode: "api",
      demoQuery: null,
      syntheticDemoManifestId: null,
      findExact: fixture.findExact.bind(fixture),
    };
    providers.secManifestAuditProvider.mockReturnValue(apiLike);
    renderWithLocale(await page(raw()));

    expect(screen.getByText("저장된 감사 API 응답")).toBeInTheDocument();
    expect(screen.queryByText("DEMO", { exact: true })).not.toBeInTheDocument();
    expect(screen.queryByText("LIVE", { exact: true })).not.toBeInTheDocument();
    expect(screen.queryByText("REALTIME", { exact: true })).not.toBeInTheDocument();
  });

  it("labels an identity-pinned synthetic API response as DEMO", async () => {
    const fixture = new FixtureSecManifestAuditProvider();
    providers.secManifestAuditProvider.mockReturnValue({
      mode: "api",
      demoQuery: null,
      syntheticDemoManifestId: SEC_MANIFEST_AUDIT_DEMO_QUERY.manifestId,
      findExact: fixture.findExact.bind(fixture),
    } satisfies SecManifestAuditProvider);
    renderWithLocale(await page(raw()));

    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByText("합성 DEMO · 실제 SEC 자료 아님")).toBeInTheDocument();
    expect(screen.queryByText("LIVE", { exact: true })).not.toBeInTheDocument();
    expect(screen.queryByText("REALTIME", { exact: true })).not.toBeInTheDocument();
  });

  it("localizes the route to English while preserving exact evidence bytes and tokens", async () => {
    i18n.getLocale.mockResolvedValue("en");
    renderWithLocale(await page(raw()), "en");
    expectSecNavigation("en");

    expect(screen.getByRole("heading", { name: "SEC filing-history manifest audit" }))
      .toBeInTheDocument();
    expect(screen.getAllByText(SEC_MANIFEST_AUDIT_DEMO_QUERY.manifestId).length)
      .toBeGreaterThan(0);
    const cutoff = screen.getAllByText("2026-08-25 12:30:00.123456 KST")[0];
    expect(cutoff).toHaveAttribute(
      "datetime",
      SEC_MANIFEST_AUDIT_DEMO_QUERY.evaluationAsOf,
    );
    expect(screen.getByText("ROOT_RELATIVE_SELECTED_REFERENCES_ONLY"))
      .toBeInTheDocument();
    expect(screen.getByText("Synthetic DEMO · not observed SEC data")).toBeInTheDocument();

    const summary = screen.getByRole("link", { name: "Summary" });
    expect(summary).toHaveAttribute("aria-current", "page");
  });

  it("routes exact absence to not-found and propagates transport failure", async () => {
    const findExact = vi.fn(async () => null);
    providers.secManifestAuditProvider.mockReturnValue({
      mode: "api",
      demoQuery: null,
      syntheticDemoManifestId: null,
      findExact,
    });
    await expect(page(raw())).rejects.toThrow(/404/);
    expect(findExact).toHaveBeenCalledOnce();

    const failure = vi.fn(async () => { throw new Error("API unavailable"); });
    providers.secManifestAuditProvider.mockReturnValue({
      mode: "api",
      demoQuery: null,
      syntheticDemoManifestId: null,
      findExact: failure,
    });
    await expect(page(raw())).rejects.toThrow("API unavailable");
  });

  it("localizes metadata and loading, error, and not-found states without fallback values", async () => {
    await expect(generateMetadata()).resolves.toMatchObject({
      title: "SEC 제출 이력 manifest 감사 · Wall Street Receipts",
    });

    const loading = renderWithLocale(await SecFilingHistoryAuditLoading());
    expectSecNavigation();
    expect(screen.getByRole("heading", { name: "지정한 증거를 검증하는 중…" }))
      .toBeInTheDocument();
    expect(screen.queryByText("DEMO", { exact: true })).not.toBeInTheDocument();
    loading.unmount();

    const reset = vi.fn();
    const error = renderWithLocale(
      <SecFilingHistoryAuditError error={new Error("unavailable")} reset={reset} />,
    );
    expectSecNavigation();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "합성 값 또는 다른 manifest를 대신 표시하지 않습니다",
    );
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(reset).toHaveBeenCalledOnce();
    error.unmount();

    renderWithLocale(await SecFilingHistoryAuditNotFound());
    expectSecNavigation();
    expect(screen.getByRole("heading", { name: "이 정확한 manifest를 표시할 수 없습니다." }))
      .toBeInTheDocument();
    expect(screen.queryByText(SEC_MANIFEST_AUDIT_DEMO_QUERY.manifestId)).not.toBeInTheDocument();
  });
});
