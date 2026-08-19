import { describe, expect, it } from "vitest";
import {
  layoutTreemap,
  type TreemapLayoutNode,
  type TreemapNode,
  type TreemapRect,
} from "./treemap-layout";

type Label = { label: string };

const bounds: TreemapRect = { x: 0, y: 0, width: 1200, height: 700 };

function leaf(id: string, weight: number): TreemapNode<Label> {
  return { id, weight, value: { label: id } };
}

function area(rect: TreemapRect): number {
  return rect.width * rect.height;
}

function expectInside(child: TreemapRect, parent: TreemapRect): void {
  expect(child.x).toBeGreaterThanOrEqual(parent.x - 1e-8);
  expect(child.y).toBeGreaterThanOrEqual(parent.y - 1e-8);
  expect(child.x + child.width).toBeLessThanOrEqual(parent.x + parent.width + 1e-8);
  expect(child.y + child.height).toBeLessThanOrEqual(parent.y + parent.height + 1e-8);
}

function overlapArea(left: TreemapRect, right: TreemapRect): number {
  const width = Math.max(0, Math.min(left.x + left.width, right.x + right.width) - Math.max(left.x, right.x));
  const height = Math.max(0, Math.min(left.y + left.height, right.y + right.height) - Math.max(left.y, right.y));
  return width * height;
}

function expectSiblingPartition(nodes: readonly TreemapLayoutNode<Label>[], parent: TreemapRect): void {
  expect(nodes.reduce((sum, node) => sum + area(node.rect), 0)).toBeCloseTo(area(parent), 7);

  for (const [index, node] of nodes.entries()) {
    expectInside(node.rect, parent);
    for (const other of nodes.slice(index + 1)) {
      expect(overlapArea(node.rect, other.rect)).toBeCloseTo(0, 8);
    }
    if (node.children.length > 0) expectSiblingPartition(node.children, node.rect);
  }
}

function flattenLeaves(nodes: readonly TreemapLayoutNode<Label>[]): TreemapLayoutNode<Label>[] {
  return nodes.flatMap((node) => node.children.length === 0 ? [node] : flattenLeaves(node.children));
}

describe("layoutTreemap", () => {
  it("returns an empty layout for an explicit empty hierarchy", () => {
    expect(layoutTreemap([], bounds)).toEqual([]);
  });

  it("gives a singleton the complete canvas", () => {
    expect(layoutTreemap([leaf("only", 12)], bounds)).toEqual([
      expect.objectContaining({ id: "only", weight: 12, rect: bounds, children: [] }),
    ]);
  });

  it("keeps nested sector, industry, and ticker siblings in bounds without overlap", () => {
    const hierarchy: TreemapNode<Label>[] = [
      {
        id: "sector-technology",
        value: { label: "Technology" },
        children: [
          {
            id: "industry-semiconductors",
            value: { label: "Semiconductors" },
            children: [leaf("NVDA", 144), leaf("AMD", 36)],
          },
          {
            id: "industry-software",
            value: { label: "Software" },
            children: [leaf("MSFT", 121)],
          },
        ],
      },
      {
        id: "sector-consumer",
        value: { label: "Consumer" },
        children: [
          {
            id: "industry-hardware",
            value: { label: "Hardware" },
            children: [leaf("AAPL", 100)],
          },
        ],
      },
    ];

    const result = layoutTreemap(hierarchy, bounds);
    expectSiblingPartition(result, bounds);
    expect(result.map((node) => node.id)).toEqual(["sector-technology", "sector-consumer"]);

    const sectors = Object.fromEntries(result.map((node) => [node.id, node]));
    expect(area(sectors["sector-technology"].rect) / area(sectors["sector-consumer"].rect))
      .toBeCloseTo(301 / 100, 7);

    const technologyIndustries = Object.fromEntries(
      sectors["sector-technology"].children.map((node) => [node.id, node]),
    );
    expect(
      area(technologyIndustries["industry-semiconductors"].rect) /
      area(technologyIndustries["industry-software"].rect),
    ).toBeCloseTo(180 / 121, 7);

    const leaves = Object.fromEntries(flattenLeaves(result).map((node) => [node.id, node]));
    expect(area(leaves.NVDA.rect) / area(leaves.AAPL.rect)).toBeCloseTo(144 / 100, 7);
    expect(area(leaves.AMD.rect) / area(leaves.MSFT.rect)).toBeCloseTo(36 / 121, 7);
  });

  it("preserves explicit proxy area ratios, including a highly skewed distribution", () => {
    const result = layoutTreemap(
      [leaf("large", 10_000), leaf("medium", 10), leaf("tiny", 1)],
      bounds,
    );
    const leaves = Object.fromEntries(flattenLeaves(result).map((node) => [node.id, node]));

    expect(area(leaves.large.rect) / area(leaves.medium.rect)).toBeCloseTo(1_000, 7);
    expect(area(leaves.medium.rect) / area(leaves.tiny.rect)).toBeCloseTo(10, 7);
    expectSiblingPartition(result, bounds);
  });

  it("keeps the contract's maximum proxy ratio representable without zeroing the small leaf", () => {
    const result = layoutTreemap([leaf("maximum", 1_000_000_000_000), leaf("minimum", 1)], bounds);
    const leaves = Object.fromEntries(flattenLeaves(result).map((node) => [node.id, node]));

    expect(area(leaves.minimum.rect)).toBeGreaterThan(0);
    const ratio = area(leaves.maximum.rect) / area(leaves.minimum.rect);
    expect(Math.abs(ratio / 1_000_000_000_000 - 1)).toBeLessThan(1e-9);
    expectSiblingPartition(result, bounds);
  });

  it("is deterministic regardless of source ordering", () => {
    const forward = [leaf("B", 2), leaf("C", 1), leaf("A", 2)];
    const reverse = [...forward].reverse();
    expect(layoutTreemap(forward, bounds)).toEqual(layoutTreemap(reverse, bounds));
    expect(forward.map(({ id }) => id)).toEqual(["B", "C", "A"]);
  });

  it("returns finite zero-area rectangles for a zero-size canvas", () => {
    const result = layoutTreemap([leaf("A", 2), leaf("B", 1)], {
      x: 8,
      y: 13,
      width: 0,
      height: 0,
    });

    for (const node of result) {
      expect(node.rect).toEqual(expect.objectContaining({ width: 0, height: 0 }));
      expect(Object.values(node.rect).every(Number.isFinite)).toBe(true);
    }
  });

  it.each([0, -1, 1.5, Number.NaN, Number.POSITIVE_INFINITY])(
    "rejects unsupported leaf proxy %s instead of inventing visible area",
    (weight) => {
      expect(() => layoutTreemap([leaf("invalid", weight)], bounds)).toThrow(
        /safe integer greater than zero/i,
      );
    },
  );

  it("rejects malformed bounds, empty branches, and duplicate sibling identities", () => {
    expect(() => layoutTreemap([leaf("A", 1)], { ...bounds, width: -1 })).toThrow(/non-negative/);
    expect(() => layoutTreemap([leaf("A", 1)], { ...bounds, x: Number.NaN })).toThrow(/finite/);
    expect(() => layoutTreemap([leaf("A", 1)], {
      x: Number.MAX_VALUE,
      y: 0,
      width: Number.MAX_VALUE,
      height: 1,
    })).toThrow(/edges must remain finite/);
    expect(() => layoutTreemap([{
      id: "empty",
      value: { label: "Empty" },
      children: [],
    }], bounds)).toThrow(/at least one child/);
    expect(() => layoutTreemap([leaf("same", 1), leaf("same", 2)], bounds)).toThrow(/duplicate id/);
    expect(() => layoutTreemap([{
      id: "unsafe-aggregate",
      value: { label: "Unsafe" },
      children: [leaf("maximum-safe", Number.MAX_SAFE_INTEGER), leaf("one-more", 1)],
    }], bounds)).toThrow(/safe positive integer total weight/i);
  });
});
