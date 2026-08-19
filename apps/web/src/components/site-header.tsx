import Link from "next/link";

type SiteHeaderProps = {
  current:
    | "dashboard"
    | "market"
    | "calls"
    | "institutions"
    | "analysts"
    | "maps"
    | "screener"
    | "methodology";
  dataMode: string;
};

export function SiteHeader({ current, dataMode }: SiteHeaderProps) {
  return (
    <header className="site-header">
      <Link className="wordmark" href="/" aria-label="Wall Street Receipts home">
        WALL STREET <span>RECEIPTS</span>
      </Link>
      <nav aria-label="Primary navigation">
        <Link aria-current={current === "dashboard" ? "page" : undefined} href="/">
          Dashboard
        </Link>
        <Link aria-current={current === "market" ? "page" : undefined} href="/market">
          Market
        </Link>
        <Link aria-current={current === "calls" ? "page" : undefined} href="/calls">
          Calls
        </Link>
        <Link
          aria-current={current === "institutions" ? "page" : undefined}
          href="/institutions"
        >
          Institutions
        </Link>
        <Link aria-current={current === "analysts" ? "page" : undefined} href="/analysts">
          Analysts
        </Link>
        <Link aria-current={current === "maps" ? "page" : undefined} href="/maps/sp500">
          Maps
        </Link>
        <Link aria-current={current === "screener" ? "page" : undefined} href="/screener">
          Screener
        </Link>
        <Link aria-current={current === "methodology" ? "page" : undefined} href="/methodology">
          Methodology
        </Link>
      </nav>
      <span className="mode-badge">{dataMode}</span>
    </header>
  );
}
