// Mounted only in ADR-078's owned container; fixed loopback destinations, never a public endpoint.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { adaptAttempts } from "/workspace/apps/web/src/lib/operator-cpi.ts";

let step = "input";
try {
  const input = JSON.parse(readFileSync(0, "utf8"));
  assert.match(input.token, /^[A-Za-z0-9+/]{43}=$/);
  const id = "00000000-0000-0000-0000-000000000001";
  const api = "/internal/v1/cpi/collection-attempts", ui = "/operator/cpi/query";
  const routes = {
    "api-list": [8080, "GET", api], "api-selected": [8080, "GET", api + "/" + id],
    "api-head-list": [8080, "HEAD", api], "api-head-selected": [8080, "HEAD", api + "/" + id],
    "ui-list": [3000, "GET", ui], "ui-selected": [3000, "GET", ui + "/" + id],
    "invalid": [8080, "GET", api + "/invalid"], "unauthorized": [8080, "GET", api],
  };
  assert.ok(Object.hasOwn(routes, input.route));
  assert.ok([200, 400, 401, 503].includes(input.expected));
  const [port, method, path] = routes[input.route];
  step = input.route;
  const started = performance.now();
  const response = await fetch(`http://127.0.0.1:${port}${path}`, {
    method, redirect: "error", signal: AbortSignal.timeout(9000),
    headers: { Authorization: `Bearer ${input.route === "unauthorized" ? Buffer.alloc(32, 255).toString("base64") : input.token}`,
      "X-WSR-Operator": "cpi-read-v1" },
  });
  assert.equal(response.headers.get("cache-control"), "no-store");
  const chunks = [];
  let size = 0;
  if (response.body) for await (const part of response.body) {
    size += part.length; assert.ok(size <= 262144); chunks.push(part);
  }
  const body = Buffer.concat(chunks).toString("utf8");
  step += ":response";
  assert.ok(!body.includes(input.token));
  assert.equal(response.status, input.expected);
  let evidenceHash = null;
  if (method === "HEAD") assert.equal(body, "");
  else {
    const value = JSON.parse(body);
    if (input.expected === 200) {
      step += ":evidence";
      const selected = input.route.endsWith("selected");
      const evidence = adaptAttempts(value, selected ? id : "");
      assert.deepEqual(evidence.attempts.map(row => row.status), selected ? ["UNKNOWN"] : ["UNKNOWN", "FAILED", "RATE_LIMITED"]);
      assert.equal(evidence.attempts[0].startedAtKst, "2020-01-01T23:59:59.123456+09:00");
      evidenceHash = createHash("sha256").update(JSON.stringify(selected ? [value.attempt] : value.attempts)).digest("hex");
    } else if (input.expected === 503 && port === 3000) assert.deepEqual(value, { error: "CPI_OPERATOR_QUERY_UNAVAILABLE" });
    else if (input.expected === 503) {
      assert.deepEqual(Object.keys(value).sort(), ["code", "detail", "instance", "status", "timestampKst", "title", "type"]);
      assert.equal(value.code, "CPI_ATTEMPT_QUERY_UNAVAILABLE");
      assert.equal(value.detail, "CPI attempt query did not return evidence.");
      assert.equal(value.instance, api); assert.equal(value.type, "about:blank");
      assert.equal(value.title, "Service Unavailable"); assert.equal(value.status, 503);
      assert.match(value.timestampKst, /\+09:00$/);
    }
  }
  console.log(JSON.stringify({ route: input.route, status: response.status, elapsedMs: Math.round(performance.now() - started), evidenceHash }));
} catch { console.error("DEMO transport probe failed: " + step); process.exitCode = 1; }
