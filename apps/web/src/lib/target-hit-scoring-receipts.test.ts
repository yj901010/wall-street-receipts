import { describe, expect, it } from "vitest";
import { receipt, receiptPage, type TargetHitScoringReceipt } from "./target-hit-scoring-receipts";
import { sampleReceipt, samplePage } from "./target-hit-scoring-receipts.fixture";

describe("strict partial DEMO receipts", () => {
  it("preserves target hit and miss, selected field and exact source values without calculating the outcome", () => {
    const row=sampleReceipt();
    expect(receipt(row,"demo-call").targetHit.booleanValue).toBe(true);
    row.targetHit.booleanValue=false; row.windowEvidence!.selectedField="LOW"; row.windowEvidence!.selectedValue="80.000";
    expect(receipt(row,"demo-call")).toEqual(row);
    row.windowEvidence!.target.value="0.000000000001";
    row.windowEvidence!.observation.windowHigh="99999999999999999999999999.999999999999";
    expect(receipt(row,"demo-call")).toEqual(row);
    // This boundary checks wire consistency, not price truth or the Java outcome calculation.
  });
  it.each(["PENDING","UNAVAILABLE","NOT_APPLICABLE"] as const)("keeps target-hit %s distinct from false and selected evidence", state => {
    const row=sampleReceipt(); row.targetHit={state,decimalValue:null,booleanValue:null,reasons:["EVIDENCE_MISSING"]};
    expect(()=>receipt(row,"demo-call")).toThrow(); row.windowEvidence=null;
    expect(receipt(row,"demo-call")).toEqual(row);
    expect(()=>receipt({...row,targetHit:{...row.targetHit,booleanValue:false}},"demo-call")).toThrow();
  });
  it("rejects absent or extra selected evidence and old five-metric profile bytes", () => {
    const row=sampleReceipt();
    for(const mutation of [{windowEvidence:null},{targetHit:{...row.targetHit,booleanValue:null}},
      {targetHit:{...row.targetHit,decimalValue:"0.000000000000"}}, {windowEvidence:{...row.windowEvidence,windowCandidates:[]}},
      {methodologyId:"wsr-demo-comparative-preview",scope:"PARTIAL_COMPARATIVE"}])
      expect(()=>receipt({...row,...mutation},"demo-call")).toThrow();
  });
  it.each(["target","binding","observation"] as const)("rejects extra fields and future/invalid capture times in %s", part => {
    const row=sampleReceipt(), window=row.windowEvidence!;
    for(const change of [{extra:true},{availableAtUtc:"2026-01-06T00:00:00.000001Z"},
      {capturedAtUtc:"2026-01-06T00:00:00.000001Z"},{capturedAtUtc:"2025-01-01T00:00:00Z"}])
      expect(()=>receipt({...row,windowEvidence:{...window,[part]:{...window[part],...change}}},"demo-call")).toThrow();
  });
  it.each(["assetId","venueId","currency","priceSourceId","priceSourceRevision","lowerBoundType","upperBoundType",
    "priceField","coverageCompleteness","adjustmentBasis","corporateActionContinuity"])("rejects selected window %s mismatch", field=>{
    const row=sampleReceipt(), w=row.windowEvidence!;
    expect(()=>receipt({...row,windowEvidence:{...w,observation:{...w.observation,[field]:"WRONG"}}},"demo-call")).toThrow();
  });
  it("rejects wrong bounds, duplicate/missing sessions, wrong selection and false attestation",()=>{
    const row=sampleReceipt(), w=row.windowEvidence!;
    for(const observation of [{lowerBoundUtc:"2026-01-05T15:00:00.000001Z"},{upperBoundUtc:w.observation.lowerBoundUtc},
      {upperBoundUtc:"2026-01-05T21:00:00.000001Z"},{windowLow:"161"},{orderedSessionIds:[]},
      {orderedSessionIds:["a","a"]},{orderedSessionIds:Array(4097).fill("a")},{orderedSessionIds:[""]}])
      expect(()=>receipt({...row,windowEvidence:{...w,observation:{...w.observation,...observation}}},"demo-call")).toThrow();
    for(const change of [{selectedField:"CLOSE"},{selectedValue:"159"},{attestationScope:"RAW_TRADES_VERIFIED"},
      {target:{...w.target,currency:"KRW"}},{target:{...w.target,adjustmentBasis:"UNKNOWN"}}])
      expect(()=>receipt({...row,windowEvidence:{...w,...change}},"demo-call")).toThrow();
  });
  it.each([0,null,"0","-1","1e2","01","1.0000000000001","100000000000000000000000000"])("rejects unsafe window levels %s",value=>{
    const row=sampleReceipt(), w=row.windowEvidence!;
    for(const change of [{selectedValue:value},{target:{...w.target,value}},
      {observation:{...w.observation,windowHigh:value}},{observation:{...w.observation,windowLow:value}}])
      expect(()=>receipt({...row,windowEvidence:{...w,...change}},"demo-call")).toThrow();
  });
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
    const missing = {...row} as Partial<TargetHitScoringReceipt>; delete missing.snapshotId; expect(() => receipt(missing,"demo-call")).toThrow();
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
    for (const page of [samplePage([row,row]), {...samplePage(),hasMore:true},samplePage(Array(21).fill(row)),{...samplePage(),limit:21},samplePage([{...row,dataComplete:true} as unknown as TargetHitScoringReceipt])])
      expect(() => receiptPage(page,"demo-call")).toThrow();
  });
  it.each(["benchmark", "sector"] as const)("requires selected %s evidence without changing financial strings", leg => {
    const row=sampleReceipt(); const key = leg === "benchmark" ? "benchmarkEvidence" : "sectorEvidence";
    const value=row[key]!; const metricKey=leg === "benchmark" ? "benchmarkReturn" : "sectorReturn";
    expect(receipt(row, "demo-call")[key]).toEqual(value);
    for(const change of [null, {...value, extra:true}, {...value, sourceBindingRole:"WRONG"}, {...value, currency:"usd"},
      {...value, basisLevel:{...value.basisLevel,observedAtUtc:"2026-01-05T15:00:00.000001Z"}},
      {...value, endpointLevel:{...value.endpointLevel,observedAtUtc:"2026-01-06T00:00:01Z"}},
      {...value, endpointLevel:{...value.endpointLevel,sourceRevision:"wrong"}}])
      expect(() => receipt({...row,[key]:change},"demo-call")).toThrow();
    for(const state of ["PENDING","NOT_APPLICABLE","UNAVAILABLE"] as const) {
      const missing={state,decimalValue:null,booleanValue:null,reasons:["EVIDENCE_MISSING"]};
      expect(() => receipt({...row,[metricKey]:missing},"demo-call")).toThrow();
      expect(receipt({...row,[metricKey]:missing,[key]:null},"demo-call")[metricKey]).toEqual(missing);
      expect(() => receipt({...row,[metricKey]:{...missing,decimalValue:"0.000000000000"},[key]:null},"demo-call")).toThrow();
    }
    const overflow={state:"UNAVAILABLE",decimalValue:null,booleanValue:null,reasons:["OUTPUT_NOT_REPRESENTABLE"]};
    expect(receipt({...row,[metricKey]:overflow},"demo-call")[key]).toEqual(value);
    expect(() => receipt({...row,[metricKey]:overflow,[key]:null},"demo-call")).toThrow();
  });
  it.each([0, null, "0", "0.000000000000", "-1", "01", "1e3", "1.0000000000001", "100000000000000000000000000"])("rejects unsafe reference levels %s", value => {
    const row=sampleReceipt(), evidence=row.benchmarkEvidence!;
    expect(() => receipt({...row,benchmarkEvidence:{...evidence,basisLevel:{...evidence.basisLevel,value}}},"demo-call")).toThrow();
  });
  it("preserves provider identity whitespace and large exact positive index values", () => {
    const row=sampleReceipt(); row.benchmarkEvidence!.providerId="  DEMO exact provider  ";
    row.benchmarkEvidence!.basisLevel.value="0.000000000001";
    row.benchmarkEvidence!.endpointLevel.value="99999999999999999999999999.999999999999";
    expect(receipt(row,"demo-call")).toEqual(row);
    const old={...row,methodologyId:"wsr-demo-endpoint-preview",scope:"PARTIAL_ENDPOINT"};
    expect(() => receipt(old,"demo-call")).toThrow();
  });
});
