import { describe, expect, it } from "vitest";
import { receipt, receiptPage, type ScoringReceipt } from "./scoring-receipts";
import { sampleReceipt, samplePage } from "./scoring-receipts.fixture";

describe("strict partial DEMO receipts", () => {
  it("keeps exact ratios, false, original/correction and microseconds", () => {
    const row = sampleReceipt(); row.directionalWin.booleanValue = false;
    expect(receipt(row, row.callId, row.receiptId)).toEqual(row);
    row.basisRevisionId = "correction-1"; row.basisRevisionSequence = 1; expect(receipt(row, row.callId)).toEqual(row);
    expect(receiptPage(samplePage([]), row.callId)).toMatchObject({kind:"ready",items:[],hasMore:false});
  });
  it.each(["dataMode", "scope", "source", "snapshotRole", "methodologyId", "methodologyVersion", "methodologyDefinitionHash", "inputFingerprint", "ledgerFingerprint", "horizon"])("rejects changed identity %s", field => {
    expect(() => receipt({...sampleReceipt(), [field]: "forged"}, "demo-call")).toThrow();
  });
  it("rejects completeness, cross-call, selected mismatch, unknown/missing fields and revision drift", () => {
    const row = sampleReceipt();
    for (const change of [{dataComplete:true}, {callId:"other"}, {basisRevisionSequence:1}, {basisRevisionId:"x"}, {extra:true}])
      expect(() => receipt({...row,...change}, "demo-call")).toThrow();
    expect(() => receipt(row, "demo-call", "00000000-0000-0000-0000-000000000002")).toThrow();
    const missing = {...row} as Partial<ScoringReceipt>; delete missing.snapshotId; expect(() => receipt(missing,"demo-call")).toThrow();
  });
  it.each(["0000-01-01T00:00:00Z", "2026-02-30T00:00:00Z", "2026-01-06T00:00:00.1234567Z", "2026-01-06T00:00:00+00:00", "2026-01-06T24:00:00Z", "malformed"])("rejects invalid instants %s", recordedAtUtc => {
    expect(() => receipt({...sampleReceipt(), recordedAtUtc},"demo-call")).toThrow();
  });
  it("rejects KST/as-of drift and preserves microsecond ordering", () => {
    const row = sampleReceipt(); expect(() => receipt({...row,evaluationAsOfKst:"2026-01-06T10:00+09:00"},"demo-call")).toThrow();
    const later = {...row, receiptId:"00000000-0000-0000-0000-000000000002",recordedAtUtc:"2026-01-06T00:01:00.123457Z",recordedAtKst:"2026-01-06T09:01:00.123457+09:00"};
    expect(receiptPage(samplePage([later,row]),"demo-call").items).toHaveLength(2);
    expect(() => receiptPage(samplePage([row,later]),"demo-call")).toThrow();
  });
  it.each([0, "0.2", "1e3", "-1.000000000001", "00.200000000000", "NaN"])("never converts invalid numeric wire values %s", decimalValue => {
    const row = sampleReceipt(); expect(() => receipt({...row,assetReturn:{...row.assetReturn,decimalValue}},"demo-call")).toThrow();
  });
  it("keeps neutral independently pending; never missing-to-zero or missing-to-false", () => {
    const row=sampleReceipt(); row.assetReturn={state:"PENDING",decimalValue:null,booleanValue:null,reasons:["ENDPOINT_PRICE_UNAVAILABLE"]};
    row.directionalWin={state:"NOT_APPLICABLE",decimalValue:null,booleanValue:null,reasons:["NEUTRAL_DIRECTION"]};
    expect(receipt(row,"demo-call")).toEqual(row);
    expect(() => receipt({...row,assetReturn:{...row.assetReturn,decimalValue:"0.000000000000"}},"demo-call")).toThrow();
    expect(() => receipt({...row,directionalWin:{...row.directionalWin,booleanValue:false}},"demo-call")).toThrow();
  });
  it("rejects duplicates, reversed ties, oversized page, false hasMore and bad single row", () => {
    const row=sampleReceipt();
    for (const page of [samplePage([row,row]), {...samplePage(),hasMore:true},samplePage(Array(21).fill(row)),{...samplePage(),limit:21},samplePage([{...row,dataComplete:true} as unknown as ScoringReceipt])])
      expect(() => receiptPage(page,"demo-call")).toThrow();
  });
});
