export default function CallsLoading() {
  return (
    <main className="state-page route-loading" aria-busy="true" aria-live="polite">
      <p className="eyebrow">Canonical event ledger</p>
      <h1>Loading analyst calls…</h1>
      <p>Reading the versioned fixture and source provenance.</p>
    </main>
  );
}
