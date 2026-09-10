// Test-only probe inside the owned Linux namespace. Never part of the runtime image.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { adaptAttempts } from "/workspace/apps/web/src/lib/operator-cpi.ts";

const input = JSON.parse(readFileSync(0, "utf8"));
assert.match(input.token, /^[A-Za-z0-9+/]{43}=$/);
assert.ok(["ready", "read", "unavailable", "closed"].includes(input.mode));
const origin = "http://127.0.0.1:3000";
async function request(path, credential = input.token) {
  const response = await fetch(origin + path, { redirect: "error", signal: AbortSignal.timeout(7000),
    headers: { Authorization: `Bearer ${credential}`, "X-WSR-Operator": "cpi-read-v1" } });
  assert.equal(response.headers.get("cache-control"), "no-store");
  const chunks = [];
  let total = 0;
  for await (const part of response.body) {
    total += part.byteLength;
    assert.ok(total <= 262144, "Bounded probe body");
    chunks.push(part);
  }
  const body = Buffer.concat(chunks).toString("utf8");
  assert.ok(!body.includes(input.token), "Credential reflected in response");
  return { status: response.status, body };
}
function listeners(port) {
  const hex = port.toString(16).toUpperCase().padStart(4, "0");
  return ["/proc/net/tcp", "/proc/net/tcp6"].flatMap(file => readFileSync(file, "utf8").trim().split("\n").slice(1)
    .map(line => line.trim().split(/\s+/)).filter(fields => fields[3] === "0A" && fields[1].endsWith(":" + hex))
    .map(fields => fields[1].split(":")[0])
    // Linux /proc stores Java's IPv4-mapped IPv6 loopback in native word order.
    // Normalize only this exact 127.0.0.1 encoding, never a wildcard or ::1 bind.
    .map(address => address === "0000000000000000FFFF00000100007F" ? "0100007F" : address));
}
let step = "listeners";
try {
  if (input.mode === "closed") {
    assert.deepEqual(listeners(3000), []);
    console.log(JSON.stringify({ mode: "closed" }));
  } else {
    assert.deepEqual(listeners(3000), ["0100007F"]);
    step = "api-listener";
    if (input.mode !== "unavailable") assert.deepEqual(listeners(8080), ["0100007F"]);
    step = "shell";
    const shell = await request("/operator/cpi");
    assert.equal(shell.status, 200);
    assert.ok(shell.body.includes("CPI 수집 이력") && shell.body.includes("UNVERIFIED"));
    if (input.mode === "ready") console.log(JSON.stringify({ mode: "ready" }));
    else {
      // A previous probe ends with an authenticated-route request. Respect the
      // real process-wide limiter even when API shutdown completes in < 1 sec.
      await new Promise(resolve => setTimeout(resolve, 1100));
      const query = await request("/operator/cpi/query");
      step = "query-status-" + query.status;
      if (input.mode === "unavailable") {
        assert.deepEqual(listeners(8080), []);
        assert.equal(query.status, 503);
        assert.deepEqual(JSON.parse(query.body), { error: "CPI_OPERATOR_QUERY_UNAVAILABLE" });
        console.log(JSON.stringify({ mode: "unavailable" }));
      } else {
        assert.equal(query.status, 200);
        const value = JSON.parse(query.body), evidence = adaptAttempts(value);
        assert.deepEqual(evidence.attempts.map(row => row.status), ["UNKNOWN", "FAILED", "RATE_LIMITED"]);
        assert.equal(evidence.attempts[0].startedAtKst, "2020-01-01T23:59:59.123456+09:00");
        assert.match(evidence.observedAtKst, /\+09:00$/);
        await new Promise(resolve => setTimeout(resolve, 1100));
        assert.equal((await request("/operator/cpi/query", Buffer.alloc(32, 255).toString("base64"))).status, 401);
        assert.equal((await request("/internal/v1/sec/collection-attempts")).status, 404);
        step = "api-cpi-only-authority";
        // Test the API directly: the UI's closed proxy alone cannot prove bearer scope.
        for (const [method, path] of [["POST", "root"], ["POST", "exact-root"],
          ["GET", "00000000-0000-0000-0000-000000000001"]]) {
          const denied = await fetch(`http://127.0.0.1:8080/internal/v1/sec/collection-attempts/${path}`, {
            method, redirect: "error", signal: AbortSignal.timeout(7000),
            headers: { Authorization: `Bearer ${input.token}` },
          });
          try {
            assert.equal(denied.status, 403);
            assert.equal(denied.headers.get("cache-control"), "no-store");
          } finally { await denied.body?.cancel(); }
        }
        console.log(JSON.stringify({ mode: "read", evidenceHash: createHash("sha256").update(JSON.stringify(value.attempts)).digest("hex"),
          observedAtKst: evidence.observedAtKst, attempts: evidence.attempts.length }));
      }
    }
  }
} catch {
  // No assertion operands, bearer, response bodies or connection details in diagnostics.
  console.error("DEMO lifecycle probe failed: " + input.mode + " / " + step);
  console.error(JSON.stringify({ apiListeners: listeners(8080), uiListeners: listeners(3000) }));
  process.exitCode = 1;
}
