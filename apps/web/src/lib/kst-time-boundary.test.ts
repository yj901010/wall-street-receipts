import { readFileSync, readdirSync } from "node:fs";
import { dirname, extname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const sourceRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const uiRoots = [join(sourceRoot, "app"), join(sourceRoot, "components")];

function files(root: string): string[] {
  return readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const path = join(root, entry.name);
    return entry.isDirectory() ? files(path) : [path];
  });
}

const productionUiFiles = uiRoots
  .flatMap(files)
  .filter((path) => [".ts", ".tsx"].includes(extname(path)))
  .filter((path) => !path.endsWith(".test.ts") && !path.endsWith(".test.tsx"));

describe("site-wide KST presentation boundary", () => {
  it("has no page-local UTC formatter or legacy UTC display helper", () => {
    const violations = productionUiFiles.flatMap((path) => {
      const source = readFileSync(path, "utf8");
      return /timeZone\s*:\s*["']UTC["']|format[A-Za-z0-9_]*Utc\b/.test(source)
        ? [relative(sourceRoot, path)]
        : [];
    });

    expect(violations).toEqual([]);
  });

  it("routes semantic time rendering through the shared KST component", () => {
    const violations = productionUiFiles.flatMap((path) => {
      if (path.endsWith(join("components", "kst-timestamp.tsx"))) return [];
      return /<time\b/.test(readFileSync(path, "utf8"))
        ? [relative(sourceRoot, path)]
        : [];
    });

    expect(violations).toEqual([]);
  });
});
