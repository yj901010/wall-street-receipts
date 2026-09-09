/** Not an environment switch: only the dedicated loopback listener sets this process marker. */
export function isOperatorProcess(): boolean {
  return typeof window === "undefined"
    && (globalThis as Record<symbol, unknown>)[Symbol.for("wsr.cpi.operator.loopback.v1")] === true;
}
