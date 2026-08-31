import { formatKstInstant } from "@/lib/kst-time";

export function KstTimestamp({
  value,
  className,
}: {
  value: string;
  className?: string;
}) {
  return (
    <time className={className} dateTime={value}>
      {formatKstInstant(value)}
    </time>
  );
}
