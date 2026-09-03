import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LocaleProvider, useLocale } from "./locale-provider";

function Probe() {
  const { locale, messages } = useLocale();
  return <output>{`${locale}:${messages.navigation.dashboard}`}</output>;
}

describe("LocaleProvider", () => {
  it("derives locale and messages directly from its server-provided prop", () => {
    const view = render(
      <LocaleProvider locale="ko">
        <Probe />
      </LocaleProvider>,
    );
    expect(screen.getByText("ko:대시보드")).toBeInTheDocument();

    view.rerender(
      <LocaleProvider locale="en">
        <Probe />
      </LocaleProvider>,
    );
    expect(screen.getByText("en:Dashboard")).toBeInTheDocument();
  });

  it("fails closed when a client consumer is missing the server-wired provider", () => {
    expect(() => render(<Probe />)).toThrow(/within LocaleProvider/i);
  });
});
