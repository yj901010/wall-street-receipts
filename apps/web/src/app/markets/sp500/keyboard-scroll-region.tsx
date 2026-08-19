"use client";

import type { KeyboardEvent, ReactNode } from "react";

type KeyboardScrollRegionProps = {
  ariaLabel: string;
  children: ReactNode;
  className: string;
};

export function KeyboardScrollRegion({
  ariaLabel,
  children,
  className,
}: KeyboardScrollRegionProps) {
  function moveHorizontally(event: KeyboardEvent<HTMLDivElement>) {
    if (
      event.target !== event.currentTarget ||
      event.altKey ||
      event.ctrlKey ||
      event.metaKey ||
      event.shiftKey
    ) return;

    const direction = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
    if (direction === 0) return;

    const region = event.currentTarget;
    const maximum = Math.max(0, region.scrollWidth - region.clientWidth);
    if (maximum === 0) return;

    const step = Math.max(80, Math.round(region.clientWidth / 4));
    const next = Math.min(maximum, Math.max(0, region.scrollLeft + direction * step));
    if (next === region.scrollLeft) return;

    region.scrollLeft = next;
    event.preventDefault();
  }

  return (
    <div
      className={className}
      role="region"
      aria-label={ariaLabel}
      tabIndex={0}
      onKeyDown={moveHorizontally}
    >
      {children}
    </div>
  );
}
