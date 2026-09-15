import { StrictMode } from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ReferenceWidget } from "./reference-widget";
import { SCRIPT_TIMEOUT_MS, WIDGET_SCRIPT, widgetAttributes } from "./config";

const scripts = () => document.querySelectorAll<HTMLScriptElement>('script[data-wsr-reference="vunelix"]');
afterEach(() => { scripts().forEach(script => script.remove()); vi.useRealTimers(); });

describe("external display boundary", () => {
  it.each(["disabled", "invalid"] as const)("keeps %s free of scripts, prices and activation", mode => {
    render(<ReferenceWidget mode={mode} locale="ko" />);
    expect(screen.getByRole("status")).toBeVisible();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(scripts()).toHaveLength(0);
    expect(document.querySelector("vunelix-symbol-overview")).toBeNull();
  });
  it("requires a separate browser click even when enabled; StrictMode never duplicates", () => {
    render(<StrictMode><ReferenceWidget mode="enabled" locale="ko" /></StrictMode>);
    expect(scripts()).toHaveLength(0);
    fireEvent.click(screen.getByRole("button", { name: "외부 Vunelix 위젯 불러오기" }));
    expect(scripts()).toHaveLength(1);
    expect(scripts()[0].src).toBe(WIDGET_SCRIPT);
    expect(scripts()[0].type).toBe("module");
    expect(scripts()[0].referrerPolicy).toBe("strict-origin");
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    const element = document.querySelector("vunelix-symbol-overview");
    for (const [key, value] of Object.entries(widgetAttributes)) expect(element).toHaveAttribute(key, value);
    fireEvent.load(scripts()[0]);
    expect(screen.getByRole("status")).toHaveTextContent("시세는 미검증");
    expect(screen.getByRole("link", { name: /외부 코드 종료/ })).toHaveAttribute("href", "/market/reference/AAPL");
  });
  it("does not equate a downloaded script with received market data", () => {
    render(<ReferenceWidget mode="enabled" locale="en" />);
    fireEvent.click(screen.getByRole("button", { name: "Load external Vunelix widget" }));
    fireEvent.load(scripts()[0]);
    expect(screen.getByRole("status")).toHaveTextContent("quotes remain unverified");
    expect(screen.getByText(/does not prove that any quote arrived/)).toBeVisible();
    expect(document.querySelector("table")).toBeNull();
  });
  it("sanitizes script errors and does not auto retry", () => {
    render(<ReferenceWidget mode="enabled" locale="en" />);
    fireEvent.click(screen.getByRole("button"));
    fireEvent.error(scripts()[0], { message: "private vendor details" });
    expect(screen.getByRole("status")).toHaveTextContent("could not be loaded");
    expect(screen.queryByText(/private vendor/)).not.toBeInTheDocument();
    expect(scripts()).toHaveLength(1);
    fireEvent.load(scripts()[0]);
    expect(screen.getByRole("status")).toHaveTextContent("could not be loaded");
  });
  it("bounds script waiting and ignores a late load without fabricating a quote", () => {
    vi.useFakeTimers();
    render(<ReferenceWidget mode="enabled" locale="ko" />);
    fireEvent.click(screen.getByRole("button"));
    act(() => vi.advanceTimersByTime(SCRIPT_TIMEOUT_MS));
    expect(screen.getByRole("status")).toHaveTextContent("불러오지 못했습니다");
    fireEvent.load(scripts()[0]);
    expect(screen.getByRole("status")).toHaveTextContent("불러오지 못했습니다");
    expect(scripts()).toHaveLength(1);
  });
});
