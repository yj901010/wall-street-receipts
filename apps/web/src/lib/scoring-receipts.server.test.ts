// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";
import { loadScoringReceipts } from "./scoring-receipts.server";
import { samplePage, sampleReceipt } from "./scoring-receipts.fixture";

afterEach(() => { vi.unstubAllEnvs(); vi.useRealTimers(); });
const baseUrl = "http://127.0.0.1:8080/prefix/";
describe("server-only receipt transport", () => {
  it("disabled is not empty and invalid input never fetches", async () => {
    const fetcher=vi.fn();
    expect(await loadScoringReceipts("demo-call",undefined,{mode:"disabled",fetcher})).toEqual({kind:"disabled"});
    expect(await loadScoringReceipts("bad/id",undefined,{mode:"api",fetcher})).toEqual({kind:"invalid"});
    expect(await loadScoringReceipts("demo-call","1-1-1-1-1",{mode:"api",fetcher})).toEqual({kind:"invalid"});
    expect(fetcher).not.toHaveBeenCalled();
  });
  it("uses exact server-side no-store GET and selected identity without fallback", async () => {
    const fetcher=vi.fn().mockResolvedValue(Response.json(samplePage()));
    expect(await loadScoringReceipts("demo-call",undefined,{mode:"api",baseUrl,fetcher})).toMatchObject({kind:"ready",selected:false});
    expect(fetcher).toHaveBeenCalledWith(new URL(baseUrl+"v1/calls/demo-call/scoring-receipts"),expect.objectContaining({method:"GET",cache:"no-store",redirect:"error",headers:{Accept:"application/json"}}));
    const row=sampleReceipt(); fetcher.mockResolvedValue(Response.json(row));
    expect(await loadScoringReceipts("demo-call",row.receiptId,{mode:"api",baseUrl,fetcher})).toMatchObject({kind:"ready",selected:true,items:[row]});
    fetcher.mockResolvedValue(Response.json({...row,callId:"foreign"}));
    expect(await loadScoringReceipts("demo-call",row.receiptId,{mode:"api",baseUrl,fetcher})).toEqual({kind:"unavailable"});
  });
  it.each(["", "file:///private", "https://user:PRIVATE@example.invalid", "https://example.invalid?secret=PRIVATE", "http://example.invalid/#PRIVATE"])("rejects malformed or credential-bearing base %s", async baseUrl => {
    const fetcher=vi.fn(); expect(await loadScoringReceipts("demo-call",undefined,{mode:"api",baseUrl,fetcher})).toEqual({kind:"unavailable"}); expect(fetcher).not.toHaveBeenCalled();
  });
  it.each([400,401,403,500,503])("sanitizes upstream HTTP %s", async status => {
    const fetcher=vi.fn().mockResolvedValue(new Response("PRIVATE_DATABASE_ERROR",{status}));
    expect(await loadScoringReceipts("demo-call",undefined,{mode:"api",baseUrl,fetcher})).toEqual({kind:"unavailable"});
  });
  it("distinguishes not found and real empty; rejects media/JSON/size/Unicode errors", async () => {
    const fetcher=vi.fn().mockResolvedValue(new Response("PRIVATE",{status:404}));
    expect(await loadScoringReceipts("demo-call",undefined,{mode:"api",baseUrl,fetcher})).toEqual({kind:"missing"});
    fetcher.mockResolvedValue(Response.json(samplePage([]))); expect(await loadScoringReceipts("demo-call",undefined,{mode:"api",baseUrl,fetcher})).toMatchObject({kind:"ready",items:[]});
    for(const response of [new Response("{}"),new Response("invalid",{headers:{"content-type":"application/json"}}),
      new Response(" ".repeat(262145),{headers:{"content-type":"application/json"}}),
      new Response(new Uint8Array([0xff]),{headers:{"content-type":"application/json"}}),
      new Response("{}",{headers:{"content-type":"application/json","content-length":"262145"}})]) {
      fetcher.mockResolvedValue(response); expect(await loadScoringReceipts("demo-call",undefined,{mode:"api",baseUrl,fetcher})).toEqual({kind:"unavailable"});
    }
  });
  it("aborts a stalled request on the bounded transport timer", async () => {
    vi.useFakeTimers(); const fetcher=vi.fn((_url,init) => new Promise<Response>((_resolve,reject) => init?.signal?.addEventListener("abort",()=>reject(new Error("PRIVATE")))));
    const result=loadScoringReceipts("demo-call",undefined,{mode:"api",baseUrl,fetcher}); await vi.advanceTimersByTimeAsync(5001); expect(await result).toEqual({kind:"unavailable"});
  });
});
