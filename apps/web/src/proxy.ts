import { NextResponse } from "next/server";
import { isOperatorProcess } from "./lib/operator-mode.server";

/** Deny before SSR streams headers; page-level notFound alone can be a soft 404. */
export function proxy() {
  if (!isOperatorProcess()) return new NextResponse("Not Found", {
    status: 404, headers: { "Cache-Control": "no-store", "X-Robots-Tag": "noindex, nofollow" },
  });
  return NextResponse.next();
}
export const config = { matcher: "/operator/:path*" };
