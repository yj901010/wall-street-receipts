import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { OperatorCpiView } from "./view";
import { operatorFixture } from "@/test/operator-cpi-fixture";
const TOKEN = btoa(String.fromCharCode(...Array(32).fill(7))); // DEMO only.
afterEach(() => vi.unstubAllGlobals());
function submit() {
  fireEvent.change(screen.getByLabelText("운영자 토큰"), { target: { value: TOKEN } });
  fireEvent.click(screen.getByRole("button", { name: "이력 조회" }));
}
describe("manual operator view", () => {
  it("never fetches on mount and clears credential on submit, old result on failure", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(Response.json(operatorFixture())).mockResolvedValueOnce(new Response("private error", { status: 401 }));
    vi.stubGlobal("fetch", fetcher); render(<OperatorCpiView />);
    expect(fetcher).not.toHaveBeenCalled(); submit();
    await screen.findByText("상태 미확인");
    expect(screen.getByLabelText("운영자 토큰")).toHaveValue("");
    expect(screen.getByText("2026-09-08 23:59:59.123456 KST")).toBeVisible();
    submit(); await screen.findByRole("alert");
    expect(screen.queryByText("상태 미확인")).toBeNull();
    expect(screen.getByRole("alert")).not.toHaveTextContent("private error");
    expect(localStorage.length).toBe(0); expect(sessionStorage.length).toBe(0);
  });
  it("clears loading and prevents late result resurrection after reset", async () => {
    let resolve!: (r: Response) => void;
    vi.stubGlobal("fetch", vi.fn(() => new Promise(r => { resolve = r; })));
    render(<OperatorCpiView />); submit();
    expect(screen.getByRole("button", { name: "조회 중…" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "입력·결과 지우기" }));
    await act(async () => resolve(Response.json(operatorFixture())));
    expect(screen.queryByText("상태 미확인")).toBeNull();
  });
  it("distinguishes empty and resets evidence on pagehide", async () => {
    const raw = operatorFixture(); raw.attempts = [];
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(raw)));
    render(<OperatorCpiView />); submit();
    await screen.findByText(/저장된 수집 시도 기록이 없습니다/);
    fireEvent(window, new Event("pagehide"));
    await waitFor(() => expect(screen.queryByText(/저장된 수집 시도 기록이 없습니다/)).toBeNull());
  });
});
