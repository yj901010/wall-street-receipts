import { adaptCallListResponse, effectiveCallListQuery } from "./call-list-adapter";
import type { CallListProvider } from "./call-list-provider";
import type { CallsProvider, CallsQuery } from "./calls-provider";
import { FixtureCallsProvider } from "./fixture-calls-provider";

export class FixtureCallListProvider implements CallListProvider {
  constructor(private readonly delegate: CallsProvider = new FixtureCallsProvider()) {}

  async list(query: CallsQuery = {}) {
    const effectiveQuery = effectiveCallListQuery(query);
    const [page, metadata] = await Promise.all([
      this.delegate.list(effectiveQuery),
      this.delegate.metadata(),
    ]);
    if (metadata.dataMode !== "DEMO") {
      throw new Error("Fixture call list metadata must remain DEMO in this phase.");
    }
    return adaptCallListResponse(page, effectiveQuery, {
      availability: "AVAILABLE",
      asOf: metadata.asOf,
      source: metadata.source,
      disclaimer: metadata.disclaimer,
    });
  }
}
