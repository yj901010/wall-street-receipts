import { describe, expect, it } from "vitest";
import { widgetMode, widgetAttributes, WIDGET_SCRIPT } from "./config";

describe("reference widget opt-in", () => {
  it.each([undefined, "disabled"])("keeps %s offline", raw => expect(widgetMode(raw)).toBe("disabled"));
  it("accepts only the explicit enabled value", () => expect(widgetMode("enabled")).toBe("enabled"));
  it.each(["", "true", "ENABLED", " enabled", "enabled ", "fixture", "api", "https://evil.example"])("rejects %s", raw => {
    expect(widgetMode(raw)).toBe("invalid");
  });
  it("pins one symbol and provider-owned timestamps/branding", () => {
    expect(widgetAttributes["data-symbol"]).toBe("NASDAQ:AAPL");
    expect(widgetAttributes["data-show-update-time"]).toBe("true");
    expect(widgetAttributes["data-remove-branding"]).toBe("false");
    expect(WIDGET_SCRIPT).toBe("https://vunelix.com/assets/bundles/js/widgets/charts/vunelix-symbol-overview.js?v=4.9.5");
  });
});
