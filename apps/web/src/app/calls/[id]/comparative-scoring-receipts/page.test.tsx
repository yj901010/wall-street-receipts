import { afterEach, describe, expect, it, vi } from "vitest";
import { render, cleanup, screen } from "@testing-library/react";
import Page from "./page";
import { loadComparativeScoringReceipts } from "@/lib/comparative-scoring-receipts.server";
vi.mock("@/lib/comparative-scoring-receipts.server",()=>({loadComparativeScoringReceipts:vi.fn().mockResolvedValue({kind:"disabled"})}));
vi.mock("@/lib/i18n/server",()=>({getLocale:vi.fn().mockResolvedValue("ko")}));
vi.mock("@/components/site-header",()=>({SiteHeader:()=> <header>DEMO</header>}));
afterEach(()=>{cleanup();vi.clearAllMocks();});
describe("comparative audit query boundary",()=>{
  it.each([{receiptId:["a","b"]},{unexpected:"DO_NOT_ECHO"},{receiptId:"1-1-1-1-1"}])("rejects malformed queries before API access %s",async query=>{
    render(await Page({params:Promise.resolve({id:"demo-call"}),searchParams:Promise.resolve(query)}));
    expect(loadComparativeScoringReceipts).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toBeVisible();
    expect(document.body.textContent).not.toContain("DO_NOT_ECHO");
  });
  it("blank and exact canonical UUID have distinct requests",async()=>{
    await Page({params:Promise.resolve({id:"demo-call"}),searchParams:Promise.resolve({receiptId:""})});
    expect(loadComparativeScoringReceipts).toHaveBeenLastCalledWith("demo-call",undefined);
    const id="00000000-0000-0000-0000-000000000001";
    await Page({params:Promise.resolve({id:"demo-call"}),searchParams:Promise.resolve({receiptId:id})});
    expect(loadComparativeScoringReceipts).toHaveBeenLastCalledWith("demo-call",id);
  });
});
