"use client";

import Link from "next/link";
import { LocaleSwitcher } from "@/components/locale-switcher";
import { useLocale } from "@/components/locale-provider";
import type { NavigationItem } from "@/lib/i18n/messages";

type SiteHeaderProps = {
  current?: NavigationItem;
  dataMode?: string;
};

export function SiteHeader({ current, dataMode }: SiteHeaderProps) {
  const { messages } = useLocale();

  return (
    <header className="site-header">
      <Link className="wordmark" href="/" aria-label={messages.siteHeader.homeLabel}>
        <span aria-hidden="true" className="wordmark-mark" />
        <span className="wordmark-text">WALL STREET RECEIPTS</span>
      </Link>
      <nav aria-label={messages.siteHeader.primaryNavigationLabel}>
        <Link aria-current={current === "dashboard" ? "page" : undefined} href="/">
          {messages.navigation.dashboard}
        </Link>
        <Link aria-current={current === "market" ? "page" : undefined} href="/market">
          {messages.navigation.market}
        </Link>
        <Link aria-current={current === "calls" ? "page" : undefined} href="/calls">
          {messages.navigation.calls}
        </Link>
        <Link
          aria-current={current === "institutions" ? "page" : undefined}
          href="/institutions"
        >
          {messages.navigation.institutions}
        </Link>
        <Link aria-current={current === "analysts" ? "page" : undefined} href="/analysts">
          {messages.navigation.analysts}
        </Link>
        <Link aria-current={current === "maps" ? "page" : undefined} href="/maps/sp500">
          {messages.navigation.maps}
        </Link>
        <Link aria-current={current === "screener" ? "page" : undefined} href="/screener">
          {messages.navigation.screener}
        </Link>
        <Link aria-current={current === "methodology" ? "page" : undefined} href="/methodology">
          {messages.navigation.methodology}
        </Link>
        <Link
          aria-current={current === "secEvidence" ? "page" : undefined}
          href="/research/sec/filing-history"
          prefetch={false}
        >
          {messages.navigation.secEvidence}
        </Link>
      </nav>
      <div className="site-header-actions">
        {dataMode ? <span className="mode-badge">{dataMode}</span> : null}
        <LocaleSwitcher />
      </div>
    </header>
  );
}
