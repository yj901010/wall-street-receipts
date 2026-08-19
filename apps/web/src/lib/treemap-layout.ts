export type TreemapRect = {
  x: number;
  y: number;
  width: number;
  height: number;
};

export type TreemapLeaf<T> = {
  id: string;
  value: T;
  weight: number;
};

export type TreemapBranch<T> = {
  id: string;
  value: T;
  children: readonly TreemapNode<T>[];
};

export type TreemapNode<T> = TreemapLeaf<T> | TreemapBranch<T>;

export type TreemapLayoutNode<T> = {
  id: string;
  value: T;
  weight: number;
  rect: TreemapRect;
  children: readonly TreemapLayoutNode<T>[];
};

type WeightedNode<T> = {
  node: TreemapNode<T>;
  weight: number;
};

function compareIds(left: string, right: string): number {
  if (left < right) return -1;
  if (left > right) return 1;
  return 0;
}

function assertRect(rect: TreemapRect): void {
  for (const [field, value] of Object.entries(rect)) {
    if (!Number.isFinite(value)) {
      throw new Error(`Treemap bounds ${field} must be finite.`);
    }
  }

  if (rect.width < 0 || rect.height < 0) {
    throw new Error("Treemap bounds width and height must be non-negative.");
  }
  if (!Number.isFinite(rect.x + rect.width) || !Number.isFinite(rect.y + rect.height)) {
    throw new Error("Treemap bounds edges must remain finite.");
  }
}

function measureNode<T>(node: TreemapNode<T>, path: string): WeightedNode<T> {
  if (node.id.trim().length === 0) {
    throw new Error(`Treemap node at ${path} must have a non-empty id.`);
  }

  if ("children" in node) {
    if (node.children.length === 0) {
      throw new Error(`Treemap branch ${node.id} must contain at least one child.`);
    }

    const siblingIds = new Set<string>();
    let weight = 0;

    for (const child of node.children) {
      if (siblingIds.has(child.id)) {
        throw new Error(`Treemap branch ${node.id} contains duplicate child id ${child.id}.`);
      }
      siblingIds.add(child.id);
      weight += measureNode(child, `${path}/${node.id}`).weight;
    }

    if (!Number.isSafeInteger(weight) || weight <= 0) {
      throw new Error(`Treemap branch ${node.id} must have a safe positive integer total weight.`);
    }

    return { node, weight };
  }

  if (!Number.isSafeInteger(node.weight) || node.weight <= 0) {
    throw new Error(`Treemap leaf ${node.id} weight must be a safe integer greater than zero.`);
  }

  return { node, weight: node.weight };
}

function splitIndex<T>(nodes: readonly WeightedNode<T>[]): number {
  const total = nodes.reduce((sum, item) => sum + item.weight, 0);
  let prefix = 0;
  let bestIndex = 1;
  let bestDistance = Number.POSITIVE_INFINITY;

  for (let index = 1; index < nodes.length; index += 1) {
    prefix += nodes[index - 1].weight;
    const distance = Math.abs(total / 2 - prefix);
    if (distance < bestDistance) {
      bestDistance = distance;
      bestIndex = index;
    }
  }

  return bestIndex;
}

function partitionRect(
  rect: TreemapRect,
  firstWeight: number,
  secondWeight: number,
): [TreemapRect, TreemapRect] {
  const totalWeight = firstWeight + secondWeight;
  const firstRatio = firstWeight / totalWeight;
  const secondRatio = secondWeight / totalWeight;

  if (rect.width >= rect.height) {
    const directFirst = firstWeight <= secondWeight;
    const firstWidth = directFirst
      ? rect.width * firstRatio
      : rect.width - rect.width * secondRatio;
    const secondWidth = directFirst
      ? rect.width - firstWidth
      : rect.width * secondRatio;
    return [
      { ...rect, width: firstWidth },
      {
        x: rect.x + firstWidth,
        y: rect.y,
        width: secondWidth,
        height: rect.height,
      },
    ];
  }

  const directFirst = firstWeight <= secondWeight;
  const firstHeight = directFirst
    ? rect.height * firstRatio
    : rect.height - rect.height * secondRatio;
  const secondHeight = directFirst
    ? rect.height - firstHeight
    : rect.height * secondRatio;
  return [
    { ...rect, height: firstHeight },
    {
      x: rect.x,
      y: rect.y + firstHeight,
      width: rect.width,
      height: secondHeight,
    },
  ];
}

function layoutMeasured<T>(nodes: readonly WeightedNode<T>[], rect: TreemapRect): TreemapLayoutNode<T>[] {
  if (nodes.length === 0) return [];

  if (nodes.length === 1) {
    const [{ node, weight }] = nodes;
    const children = "children" in node
      ? layoutSiblings(node.children, rect)
      : [];

    return [{ id: node.id, value: node.value, weight, rect, children }];
  }

  const index = splitIndex(nodes);
  const first = nodes.slice(0, index);
  const second = nodes.slice(index);
  const firstWeight = first.reduce((sum, item) => sum + item.weight, 0);
  const secondWeight = second.reduce((sum, item) => sum + item.weight, 0);
  const [firstRect, secondRect] = partitionRect(rect, firstWeight, secondWeight);

  return [...layoutMeasured(first, firstRect), ...layoutMeasured(second, secondRect)];
}

function layoutSiblings<T>(nodes: readonly TreemapNode<T>[], rect: TreemapRect): TreemapLayoutNode<T>[] {
  const siblingIds = new Set<string>();
  const measured = nodes.map((node) => {
    if (siblingIds.has(node.id)) {
      throw new Error(`Treemap siblings contain duplicate id ${node.id}.`);
    }
    siblingIds.add(node.id);
    return measureNode(node, "root");
  });

  measured.sort((left, right) => right.weight - left.weight || compareIds(left.node.id, right.node.id));
  return layoutMeasured(measured, rect);
}

/**
 * Produces a deterministic nested binary treemap. Every positive leaf receives
 * area proportional to its explicit weight within floating-point precision;
 * no minimum visual size is introduced by the layout engine.
 */
export function layoutTreemap<T>(
  nodes: readonly TreemapNode<T>[],
  rect: TreemapRect,
): readonly TreemapLayoutNode<T>[] {
  assertRect(rect);
  return layoutSiblings(nodes, rect);
}
