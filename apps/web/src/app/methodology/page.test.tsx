import { fireEvent, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MethodologyCatalog } from "@/lib/providers";
import { renderWithLocale } from "@/test/render-with-locale";
import MethodologyError from "./error";
import MethodologyLoading from "./loading";
import { MethodologyRegistry } from "./methodology-registry";
import MethodologyPage from "./page";

const i18n = vi.hoisted(() => ({ getLocale: vi.fn() }));

vi.mock("@/lib/i18n/server", () => ({ getLocale: i18n.getLocale }));

describe("MethodologyPage", () => {
  beforeEach(() => {
    i18n.getLocale.mockReset();
    i18n.getLocale.mockResolvedValue("ko");
  });

  it("renders the Korean-default DEMO registry without changing canonical evidence", async () => {
    renderWithLocale(await MethodologyPage());

    expect(screen.getByRole("heading", {
      name: "성과 주장에 앞서 방법론 정의를 확인합니다.",
    })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "방법론" })).toHaveAttribute(
      "aria-current",
      "page",
    );

    const provenance = screen.getByLabelText("방법론 데이터셋 출처");
    expect(within(provenance).getByText("fixture-call-outcomes-v1")).toBeInTheDocument();
    expect(within(provenance).getByText("DEMO")).toBeInTheDocument();
    expect(within(provenance).getByText("Aug 18, 2026, 12:10 AM UTC")).toBeInTheDocument();

    const registryRegion = screen.getByRole("region", { name: "방법론 레지스트리 표" });
    expect(registryRegion).toHaveAttribute("tabindex", "0");
    const table = within(registryRegion).getByRole("table", {
      name: "버전별 점수 산정 방법론 정의",
    });
    expect(within(table).getAllByRole("columnheader")).toHaveLength(8);
    expect(within(table).getAllByText("MODEL_ONLY")).toHaveLength(2);

    const versions = within(table).getAllByRole("row").slice(1).map((row) =>
      row.querySelector('[data-label="버전"]')?.textContent,
    );
    expect(versions).toEqual(["1.0.0", "2.0.0"]);
    expect(within(table).getByText(
      "03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2",
    )).toBeInTheDocument();
    expect(within(table).getByText(
      "256056d7cb2b292a1ec0bd7b905f856134bb38851a65b8a2fceaca41489db3e8",
    )).toBeInTheDocument();
    expect(screen.getByText(/수식 본문이 없습니다/)).toBeInTheDocument();
    expect(screen.getByText(/P3까지 연기/)).toBeInTheDocument();
    expect(screen.queryByText(/directional win:\s*(true|false)/i)).not.toBeInTheDocument();
  });

  it("renders the English cookie UI while preserving the same registry values", async () => {
    i18n.getLocale.mockResolvedValue("en");
    renderWithLocale(await MethodologyPage(), "en");

    expect(screen.getByRole("heading", {
      name: "Methodology definitions, before performance claims.",
    })).toBeInTheDocument();
    expect(screen.getByRole("navigation", { name: "Primary navigation" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Methodology registry table" })).toHaveTextContent(
      "MODEL_ONLY",
    );
    expect(screen.getAllByText("MODEL_ONLY")).toHaveLength(2);
  });

  it("renders an explicit Korean empty registry without substitute definitions", () => {
    const emptyCatalog: MethodologyCatalog = {
      asOf: "2026-08-18T00:10:00Z",
      dataMode: "DEMO",
      source: "fixture-call-outcomes-v1",
      disclaimer: "Synthetic DEMO model records only.",
      items: [],
    };

    renderWithLocale(<MethodologyRegistry catalog={emptyCatalog} locale="ko" />);

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "대체 버전, 해시 또는 계산 결과를 생성하지 않았습니다.",
    );
    expect(screen.getByText("0 DEMO 개 정의")).toBeInTheDocument();
  });

  it("provides Korean accessible loading and retryable error states", async () => {
    const loading = renderWithLocale(await MethodologyLoading());

    expect(screen.getByText("방법론 증거를 불러오는 중…").closest("div")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    loading.unmount();

    const reset = vi.fn();
    renderWithLocale(<MethodologyError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "부분 정의나 계산값을 대신 표시하지 않습니다.",
    );
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
