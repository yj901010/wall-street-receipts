import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LocaleProvider } from "./locale-provider";
import { SiteHeader } from "./site-header";

const actions = vi.hoisted(() => ({
  setLocaleAction: vi.fn(),
}));

vi.mock("@/app/actions/locale", () => ({
  setLocaleAction: actions.setLocaleAction,
}));

describe("SiteHeader locale foundation", () => {
  beforeEach(() => {
    actions.setLocaleAction.mockReset();
    actions.setLocaleAction.mockResolvedValue(undefined);
  });

  it("renders the exact Korean navigation, ARIA, active route, and untranslated data mode", () => {
    render(
      <LocaleProvider locale="ko">
        <SiteHeader current="calls" dataMode="DEMO" />
      </LocaleProvider>,
    );

    const navigation = screen.getByRole("navigation", { name: "주요 탐색" });
    expect(within(navigation).getAllByRole("link").map((link) => link.textContent)).toEqual([
      "대시보드",
      "시장",
      "콜 기록",
      "기관",
      "애널리스트",
      "시장 지도",
      "스크리너",
      "방법론",
    ]);
    expect(within(navigation).getByRole("link", { name: "콜 기록" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "Wall Street Receipts 홈" })).toHaveAttribute(
      "href",
      "/",
    );
    expect(screen.getByText("DEMO")).toBeInTheDocument();
  });

  it("renders the English catalog from provider props", () => {
    render(
      <LocaleProvider locale="en">
        <SiteHeader current="methodology" dataMode="DEMO" />
      </LocaleProvider>,
    );

    const navigation = screen.getByRole("navigation", { name: "Primary navigation" });
    expect(within(navigation).getByRole("link", { name: "Dashboard" })).toHaveAttribute(
      "href",
      "/",
    );
    expect(within(navigation).getByRole("link", { name: "Methodology" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("submits a strict locale value through the accessible native form", async () => {
    render(
      <LocaleProvider locale="ko">
        <SiteHeader current="dashboard" dataMode="DEMO" />
      </LocaleProvider>,
    );

    const switcher = screen.getByRole("form", { name: "언어 선택" });
    const korean = within(switcher).getByRole("button", { name: "한국어" });
    const english = within(switcher).getByRole("button", { name: "English" });
    expect(korean).toHaveAttribute("aria-pressed", "true");
    expect(english).toHaveAttribute("aria-pressed", "false");
    expect(korean).toHaveAttribute("lang", "ko");
    expect(english).toHaveAttribute("lang", "en");
    expect(korean).toHaveTextContent("KO");
    expect(english).toHaveTextContent("EN");
    expect(within(switcher).queryByText("언어를 변경하는 중입니다.")).not.toBeInTheDocument();

    fireEvent.click(english);

    await waitFor(() => expect(actions.setLocaleAction).toHaveBeenCalledOnce());
    const submitted = actions.setLocaleAction.mock.calls[0]?.[0] as FormData;
    expect(submitted.get("locale")).toBe("en");
  });

  it("disables both choices and announces only while the Server Action is pending", async () => {
    let resolveAction: (() => void) | undefined;
    actions.setLocaleAction.mockReturnValue(
      new Promise<void>((resolve) => {
        resolveAction = resolve;
      }),
    );
    render(
      <LocaleProvider locale="en">
        <SiteHeader current="dashboard" dataMode="DEMO" />
      </LocaleProvider>,
    );

    const switcher = screen.getByRole("form", { name: "Language selection" });
    const korean = within(switcher).getByRole("button", { name: "한국어" });
    const english = within(switcher).getByRole("button", { name: "English" });
    fireEvent.click(korean);

    await waitFor(() => {
      expect(korean).toBeDisabled();
      expect(english).toBeDisabled();
      expect(within(switcher).getByText("Changing language.")).toBeInTheDocument();
    });

    await act(async () => {
      resolveAction?.();
    });
    await waitFor(() => {
      expect(korean).not.toBeDisabled();
      expect(english).not.toBeDisabled();
      expect(within(switcher).queryByText("Changing language.")).not.toBeInTheDocument();
    });
  });

  it("restores focus to the selected language after the server locale prop changes", async () => {
    const view = render(
      <LocaleProvider locale="ko">
        <SiteHeader current="dashboard" dataMode="DEMO" />
      </LocaleProvider>,
    );

    const korean = screen.getByRole("button", { name: "한국어" });
    korean.focus();
    expect(korean).toHaveFocus();

    view.rerender(
      <LocaleProvider locale="en">
        <SiteHeader current="dashboard" dataMode="DEMO" />
      </LocaleProvider>,
    );

    await waitFor(() => expect(screen.getByRole("button", { name: "English" })).toHaveFocus());
  });
});
