import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ReceiptView } from "./receipt-view";
import { sampleReceipt } from "@/lib/target-hit-scoring-receipts.fixture";
import type { ReceiptState } from "@/lib/target-hit-scoring-receipts";
vi.mock("@/components/site-header", () => ({SiteHeader: () => <header>DEMO</header>}));
afterEach(cleanup);
describe("receipt audit presentation", () => {
  it.each(["ko","en"] as const)("shows target hit/miss versus absent evidence safely in %s",locale=>{
    const row=sampleReceipt(); row.targetHit.booleanValue=false;
    row.windowEvidence!.binding.provenanceId="<script>DO_NOT_EXECUTE</script>";
    render(<ReceiptView callId="demo-call" state={{kind:"ready",items:[row],hasMore:false,selected:true}} locale={locale} />);
    expect(screen.getByText(locale==="ko"?"미도달":"Miss",{exact:true})).toBeVisible();
    expect(screen.getByRole("heading",{name:locale==="ko"?"선택된 목표가 도달 근거":"Selected target-hit evidence"})).toBeVisible();
    expect(screen.getByText("HIGH / 160",{exact:true})).toBeVisible();
    expect(screen.getByText("160 / 80",{exact:true})).toBeVisible();
    expect(screen.getByText(/CALLER_ATTESTED_DEMO_CAUSAL_WINDOW_NOT_RAW_TRADE_VERIFICATION/)).toBeVisible();
    expect(document.querySelector("script")).toBeNull();
    cleanup(); row.targetHit={state:"UNAVAILABLE",decimalValue:null,booleanValue:null,reasons:["WINDOW_EVIDENCE_UNAVAILABLE","WINDOW_OBSERVATION_MISSING"]};row.windowEvidence=null;
    render(<ReceiptView callId="demo-call" state={{kind:"ready",items:[row],hasMore:false,selected:true}} locale={locale} />);
    expect(screen.queryByText(locale==="ko"?"미도달":"Miss",{exact:true})).not.toBeInTheDocument();
    expect(screen.getByText(locale==="ko"?"선택된 전체 구간 고가·저가 근거가 없습니다. 미도달을 뜻하지 않습니다.":"No full-window high/low evidence was selected. This does not mean a miss.")).toBeVisible();
  });
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
    expect(screen.getByText("이 DEMO 발언에 저장된 목표가 도달 평가 기록이 없습니다.")).toBeVisible();
    expect(screen.getByText(/이 화면은 기록을 만들지 않습니다/)).toBeVisible();
  });
  it("renders exact values and evidence without implying complete or signed data", () => {
    const row=sampleReceipt(); const state:ReceiptState={kind:"ready",items:[row],hasMore:false,selected:true};
    render(<ReceiptView callId="demo-call" selectedId={row.receiptId} state={state} locale="ko" />);
    expect(screen.getByText("0.200000000000")).toBeVisible(); expect(screen.getByText("0.250000000000")).toBeVisible();
    expect(screen.getByText("0.100000000000")).toBeVisible(); expect(screen.getByText("-0.050000000000")).toBeVisible();
    expect(screen.getByRole("heading",{name:"선택된 벤치마크 근거"})).toBeVisible();
    expect(screen.getByRole("heading",{name:"선택된 섹터 근거"})).toBeVisible();
    expect(screen.getByText(/외부 제공자의 진위나 실제 시장 관측을 독립적으로 인증하지 않습니다/)).toBeVisible();
    expect(screen.getByText(row.inputFingerprint)).toBeVisible(); expect(screen.getByText(row.ledgerFingerprint)).toBeVisible();
    expect(screen.getByText(row.snapshotId)).toBeVisible(); expect(screen.getByText(row.termsProvenanceId)).toBeVisible();
    expect(screen.getByRole("region",{name:"목표가 도달 평가 기록 표 (가로 스크롤)"})).toHaveAttribute("tabindex","0");
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
