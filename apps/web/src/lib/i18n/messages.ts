import type { Locale } from "./config";

export const NAVIGATION_ITEMS = [
  "dashboard",
  "market",
  "calls",
  "institutions",
  "analysts",
  "maps",
  "screener",
  "methodology",
  "secEvidence",
] as const;

export type NavigationItem = (typeof NAVIGATION_ITEMS)[number];

export type CommonMessages = {
  metadata: {
    title: string;
    description: string;
  };
  notFound: {
    eyebrow: string;
    title: string;
    body: string;
    dashboard: string;
    calls: string;
  };
  siteHeader: {
    homeLabel: string;
    primaryNavigationLabel: string;
    localeSwitcherLabel: string;
    koreanOptionLabel: string;
    englishOptionLabel: string;
    localeChangePending: string;
  };
  navigation: Record<NavigationItem, string>;
};

const ko = {
  metadata: {
    title: "Wall Street Receipts",
    description: "시점 일관성을 지킨 애널리스트 콜 증거와 결과 연구.",
  },
  notFound: {
    eyebrow: "찾을 수 없는 경로",
    title: "페이지를 찾을 수 없습니다.",
    body: "요청한 경로는 게시되지 않았습니다. 증거나 대체 데이터를 추정해 표시하지 않습니다.",
    dashboard: "대시보드로 돌아가기",
    calls: "콜 기록 열기",
  },
  siteHeader: {
    homeLabel: "Wall Street Receipts 홈",
    primaryNavigationLabel: "주요 탐색",
    localeSwitcherLabel: "언어 선택",
    koreanOptionLabel: "한국어",
    englishOptionLabel: "English",
    localeChangePending: "언어를 변경하는 중입니다.",
  },
  navigation: {
    dashboard: "대시보드",
    market: "시장",
    calls: "콜 기록",
    institutions: "기관",
    analysts: "애널리스트",
    maps: "시장 지도",
    screener: "스크리너",
    methodology: "방법론",
    secEvidence: "SEC 증거",
  },
} satisfies CommonMessages;

const en = {
  metadata: {
    title: "Wall Street Receipts",
    description: "Point-in-time analyst call evidence and outcome research.",
  },
  notFound: {
    eyebrow: "Unknown route",
    title: "Page not found.",
    body: "The requested route is not published. No evidence or substitute data is inferred.",
    dashboard: "Return to dashboard",
    calls: "Open call records",
  },
  siteHeader: {
    homeLabel: "Wall Street Receipts home",
    primaryNavigationLabel: "Primary navigation",
    localeSwitcherLabel: "Language selection",
    koreanOptionLabel: "한국어",
    englishOptionLabel: "English",
    localeChangePending: "Changing language.",
  },
  navigation: {
    dashboard: "Dashboard",
    market: "Market",
    calls: "Calls",
    institutions: "Institutions",
    analysts: "Analysts",
    maps: "Maps",
    screener: "Screener",
    methodology: "Methodology",
    secEvidence: "SEC evidence",
  },
} satisfies CommonMessages;

export const COMMON_MESSAGES = {
  ko,
  en,
} as const satisfies Record<Locale, CommonMessages>;

export function getCommonMessages(locale: Locale): CommonMessages {
  return COMMON_MESSAGES[locale];
}
