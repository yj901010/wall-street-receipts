import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ReceiptView } from "./receipt-view";
import { sampleReceipt } from "@/lib/scoring-receipts.fixture";
import type { ReceiptState } from "@/lib/scoring-receipts";
vi.mock("@/components/site-header", () => ({SiteHeader: () => <header>DEMO</header>}));
afterEach(cleanup);
describe("receipt audit presentation", () => {
  it.each(["disabled", "missing", "invalid", "unavailable"] as const)("keeps %s separate from empty and numbers", kind => {
    render(<ReceiptView callId="demo-call" state={{kind}} locale="ko" />);
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByText("0.000000000000")).not.toBeInTheDocument();
    expect(screen.getByText("부분 평가 · 완성 점수 아님")).toBeVisible();
    if (kind === "invalid") expect(screen.getByLabelText("기록 UUID (선택)")).toHaveAttribute("aria-invalid", "true");
    else expect(screen.getByLabelText("기록 UUID (선택)")).not.toHaveAttribute("aria-invalid");
  });
  it("empty explicitly says this page does not create records", () => {
    render(<ReceiptView callId="demo-call" state={{kind:"ready",items:[],hasMore:false,selected:false}} locale="ko" />);
    expect(screen.getByText("이 DEMO 발언에 저장된 평가 기록이 없습니다.")).toBeVisible();
    expect(screen.getByText(/이 화면은 기록을 만들지 않습니다/)).toBeVisible();
  });
  it("renders exact values and evidence without implying complete or signed data", () => {
    const row=sampleReceipt(); const state:ReceiptState={kind:"ready",items:[row],hasMore:false,selected:true};
    render(<ReceiptView callId="demo-call" selectedId={row.receiptId} state={state} locale="ko" />);
    expect(screen.getByText("0.200000000000")).toBeVisible(); expect(screen.getByText("0.250000000000")).toBeVisible();
    expect(screen.getByText(row.inputFingerprint)).toBeVisible(); expect(screen.getByText(row.ledgerFingerprint)).toBeVisible();
    expect(screen.getByText(row.snapshotId)).toBeVisible(); expect(screen.getByText(row.termsProvenanceId)).toBeVisible();
    expect(screen.getByRole("region",{name:"평가 기록 표 (가로 스크롤)"})).toHaveAttribute("tabindex","0");
    expect(screen.getByText(/전자서명이나 실제 가격의 진실성을 보장하지 않습니다/)).toBeVisible();
  });
  it("English neutral and pending remain distinct with no false Boolean", () => {
    const row=sampleReceipt(); row.assetReturn={state:"PENDING",decimalValue:null,booleanValue:null,reasons:["ENDPOINT_PRICE_UNAVAILABLE"]};
    row.directionalWin={state:"NOT_APPLICABLE",decimalValue:null,booleanValue:null,reasons:["NEUTRAL_DIRECTION"]};
    render(<ReceiptView callId="demo-call" state={{kind:"ready",items:[row],hasMore:false,selected:false}} locale="en" />);
    expect(screen.getByText("Awaiting close")).toBeVisible(); expect(screen.getByText("Not applicable")).toBeVisible();
    expect(screen.queryByText("Disagrees")).not.toBeInTheDocument(); expect(screen.queryByText("0.000000000000")).not.toBeInTheDocument();
    expect(screen.getByText("NEUTRAL_DIRECTION")).toBeVisible();
  });
});
