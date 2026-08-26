import { expect, type BrowserContext, type Locator, type Page } from "@playwright/test";

export function collectRuntimeErrors(page: Page) {
  const errors: string[] = [];

  page.on("console", (message) => {
    if (message.type() === "error" || message.type() === "warning") {
      errors.push(`console ${message.type()}: ${message.text()}`);
    }
  });
  page.on("pageerror", (error) => {
    errors.push(`pageerror: ${error.message}`);
  });

  return errors;
}

export async function expectNoPageOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    body: document.body.scrollWidth,
    root: document.documentElement.scrollWidth,
    viewport: document.documentElement.clientWidth,
  }));

  expect(dimensions.root).toBeLessThanOrEqual(dimensions.viewport + 1);
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport + 1);
}

export function expectNoRuntimeErrors(errors: string[]) {
  expect(errors, errors.join("\n")).toEqual([]);
}

export async function expectVisibleKeyboardFocus(locator: Locator) {
  await expect(locator).toBeFocused();
  const focusStyle = await locator.evaluate((element) => {
    const style = getComputedStyle(element);
    return { outlineStyle: style.outlineStyle, outlineWidth: style.outlineWidth };
  });

  expect(focusStyle.outlineStyle).not.toBe("none");
  expect(Number.parseFloat(focusStyle.outlineWidth)).toBeGreaterThanOrEqual(2);
}

export async function activateEnglishLocale(
  context: BrowserContext,
  page: Page,
  englishButton: Locator,
) {
  const localProductionHttp = process.env.PLAYWRIGHT_LOCAL_PRODUCTION_HTTP;
  if (localProductionHttp === undefined) {
    await englishButton.press("Enter");
    return;
  }
  if (localProductionHttp !== "true") {
    throw new Error("PLAYWRIGHT_LOCAL_PRODUCTION_HTTP must be exactly true when configured.");
  }

  // Production cookies are Secure. The disposable HTTP harness injects only
  // this non-secret preference; the normal browser suite owns server-action coverage.
  await context.addCookies([{
    name: "wsr_locale",
    value: "en",
    url: new URL(page.url()).origin,
    httpOnly: true,
    sameSite: "Lax",
  }]);
  await page.reload();
}
