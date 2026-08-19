import Link from "next/link";

export default function ScreenerNotFound() {
  return (
    <main className="state-page route-error">
      <p className="eyebrow">Unsupported screener request</p>
      <h1>This screener request is not published.</h1>
      <p>
        The shell accepts no query parameters. No query was executed and no filter, result, or
        alternate screening state was substituted.
      </p>
      <div className="state-actions">
        <Link className="text-action" href="/calls">Open recorded call evidence</Link>
        <Link className="text-action" href="/methodology">Open methodology definitions</Link>
      </div>
    </main>
  );
}
