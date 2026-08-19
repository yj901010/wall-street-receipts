import type { Metadata } from "next";
import type { ReactNode } from "react";
import "@/styles/tokens.css";
import "./globals.css";

export const metadata: Metadata = {
  title: "Wall Street Receipts",
  description: "Point-in-time analyst call evidence and outcome research.",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en" data-scroll-behavior="smooth">
      <body>{children}</body>
    </html>
  );
}
