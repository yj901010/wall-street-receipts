import { notFound } from "next/navigation";
import { isOperatorProcess } from "@/lib/operator-mode.server";
import { OperatorCpiView } from "./view";

export const dynamic = "force-dynamic";
export const metadata = { title: "CPI 수집 이력 · 운영자", robots: { index: false, follow: false } };
export default function OperatorCpiPage() {
  if (!isOperatorProcess()) notFound();
  return <OperatorCpiView />;
}
