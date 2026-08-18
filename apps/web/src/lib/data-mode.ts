export const DATA_MODES = ["REALTIME", "DELAYED", "EOD", "DEMO"] as const;

export type DataMode = (typeof DATA_MODES)[number];

export function readDataMode(value = process.env.NEXT_PUBLIC_DATA_MODE): DataMode {
  const normalized = value?.toUpperCase();

  if (DATA_MODES.some((mode) => mode === normalized)) {
    return normalized as DataMode;
  }

  return "DEMO";
}
