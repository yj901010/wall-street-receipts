// @vitest-environment node
import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { fileURLToPath } from "node:url";
import { expect, it } from "vitest";

const cwd = fileURLToPath(new URL("../..", import.meta.url));
function run(port: number, extra: Record<string, string> = {}) {
  return new Promise<{ code: number | null; signal: string | null; output: string }>((resolve, reject) => {
    const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => /^(PATH|SYSTEMROOT|WINDIR|TEMP|TMP|PATHEXT)$/i.test(key)));
    const child = spawn(process.execPath, ["--import", "./operator/tests/next-start-fixture.mjs", "operator/start.mjs"], {
      cwd, env: { ...env, CPI_OPERATOR_UI_PORT: String(port), CPI_OPERATOR_API_PORT: String(port === 8080 ? 8081 : 8080), ...extra },
      stdio: ["ignore", "pipe", "pipe"],
    });
    let output = "";
    const deadline = setTimeout(() => { child.kill(); reject(new Error("Owned startup test timed out")); }, 10000);
    const read = (part: Buffer) => {
      output += part.toString();
      if (output.length > 8192) { child.kill(); reject(new Error("Owned startup output limit")); }
    };
    child.stdout.on("data", read); child.stderr.on("data", read);
    child.once("error", error => { clearTimeout(deadline); reject(error); });
    child.once("close", (code, signal) => { clearTimeout(deadline); resolve({ code, signal, output }); });
  });
}
function failed(result: Awaited<ReturnType<typeof run>>, reason = "") {
  expect(result.code).toBe(1); expect(result.signal).toBeNull();
  expect(result.output).toContain("CPI_OPERATOR_STARTUP_FAILED" + reason);
  expect(result.output).not.toContain("CPI operator view:");
  expect(result.output).not.toContain("DEMO_PRIVATE_");
}
it("fails nonzero on duplicate bind even when the framework swallows uncaught exceptions", async () => {
  const owner = createServer((_req, res) => res.end("original owner"));
  await new Promise<void>(resolve => owner.listen(0, "127.0.0.1", resolve));
  try {
    const port = (owner.address() as { port: number }).port;
    failed(await run(port), " EADDRINUSE");
    expect(await (await fetch(`http://127.0.0.1:${port}`)).text()).toBe("original owner");
  } finally { await new Promise<void>(resolve => owner.close(() => resolve())); }
});
it("sanitizes prepare failure and exits nonzero", async () => {
  failed(await run(34567, { WSR_TEST_PREPARE_FAIL: "1" }));
});
it("keeps startup failure nonzero even when renderer cleanup rejects", async () => {
  failed(await run(34567, { WSR_TEST_PREPARE_FAIL: "1", WSR_TEST_CLOSE: "reject" }));
});
it("bounds a hung renderer cleanup without becoming a successful exit", async () => {
  failed(await run(34567, { WSR_TEST_PREPARE_FAIL: "1", WSR_TEST_CLOSE: "hang" }));
}, 15000);
