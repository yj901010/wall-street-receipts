export const SITE_TIME_ZONE = "Asia/Seoul" as const;
export const SITE_TIME_ZONE_LABEL = "KST" as const;

const RFC3339_INSTANT =
  /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|([+-])(\d{2}):(\d{2}))$/;
const CALENDAR_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

const kstPartsFormatter = new Intl.DateTimeFormat("en-US", {
  timeZone: SITE_TIME_ZONE,
  calendar: "gregory",
  numberingSystem: "latn",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  era: "short",
  hourCycle: "h23",
});

function invalidInstant(value: string): never {
  throw new Error(`Cannot display a noncanonical RFC 3339 instant in KST: ${value}.`);
}

function instantMilliseconds(value: string): { milliseconds: number; fraction: string | undefined } {
  const match = RFC3339_INSTANT.exec(value);
  if (!match || match[1].startsWith("0000-")) invalidInstant(value);

  const localMilliseconds = Date.parse(`${match[1]}Z`);
  if (
    !Number.isFinite(localMilliseconds) ||
    new Date(localMilliseconds).toISOString() !== `${match[1]}.000Z`
  ) {
    invalidInstant(value);
  }

  const offsetHours = match[3] === "Z" ? 0 : Number(match[5]);
  const offsetMinutes = match[3] === "Z" ? 0 : Number(match[6]);
  if (offsetHours > 18 || offsetMinutes > 59 || (offsetHours === 18 && offsetMinutes !== 0)) {
    invalidInstant(value);
  }

  const offsetDirection = match[4] === "+" ? 1 : match[4] === "-" ? -1 : 0;
  const fraction = match[2];
  const milliseconds = localMilliseconds
    - offsetDirection * (offsetHours * 60 + offsetMinutes) * 60_000
    + Number((fraction ?? "").slice(0, 3).padEnd(3, "0"));

  if (!Number.isFinite(milliseconds)) invalidInstant(value);
  return { milliseconds, fraction };
}

function parts(value: Date): Record<string, string> {
  return Object.fromEntries(
    kstPartsFormatter
      .formatToParts(value)
      .filter(({ type }) => type !== "literal")
      .map(({ type, value: part }) => [type, part]),
  );
}

/**
 * Formats an observed RFC 3339 instant for every user-facing site surface.
 * The source offset and fractional precision remain available in the caller's
 * raw value (for example, through a <time dateTime> attribute); this function
 * changes presentation only.
 */
export function formatKstInstant(value: string): string {
  const { milliseconds, fraction } = instantMilliseconds(value);
  const result = parts(new Date(milliseconds));
  const resultYear = Number(result.year);
  if (result.era !== "AD" || !Number.isInteger(resultYear) || resultYear < 1 || resultYear > 9999) {
    invalidInstant(value);
  }

  const year = result.year.padStart(4, "0");
  const subsecond = fraction ? `.${fraction}` : "";

  return `${year}-${result.month}-${result.day} ${result.hour}:${result.minute}:${result.second}${subsecond} ${SITE_TIME_ZONE_LABEL}`;
}

function calendarDate(value: string): Date {
  const match = CALENDAR_DATE.exec(value);
  if (!match || match[1] === "0000") {
    throw new Error(`Cannot convert an invalid KST calendar date: ${value}.`);
  }

  const utcDate = new Date(`${value}T00:00:00.000Z`);
  if (!Number.isFinite(utcDate.getTime()) || utcDate.toISOString().slice(0, 10) !== value) {
    throw new Error(`Cannot convert an invalid KST calendar date: ${value}.`);
  }
  return utcDate;
}

/** Converts a Korean calendar day's inclusive start to its canonical UTC API boundary. */
export function kstCalendarDateStartUtc(value: string): string {
  calendarDate(value);
  return new Date(`${value}T00:00:00.000+09:00`).toISOString();
}

/** Converts the day after a Korean calendar date to its exclusive canonical UTC API boundary. */
export function nextKstCalendarDateStartUtc(value: string): string {
  const nextDate = calendarDate(value);
  nextDate.setUTCDate(nextDate.getUTCDate() + 1);
  const next = nextDate.toISOString().slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(next)) {
    throw new Error(`Cannot convert the KST calendar date beyond a four-digit year: ${value}.`);
  }
  return kstCalendarDateStartUtc(next);
}
