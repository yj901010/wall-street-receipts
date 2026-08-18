export default function CallDetailLoading() {
  return (
    <main className="state-page route-loading" aria-busy="true" aria-live="polite">
      <p className="eyebrow">Canonical event ledger</p>
      <h1>Loading call evidence…</h1>
      <p>Resolving identities, provenance, and the immutable point-in-time snapshot.</p>
    </main>
  );
}
