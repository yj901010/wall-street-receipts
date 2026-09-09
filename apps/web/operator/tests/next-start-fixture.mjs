// Test-only Next lifecycle stub. Never copied into the operator runtime image.
import { registerHooks } from "node:module";
const source = `export default function next() {
  return {
    async prepare() {
      process.on('uncaughtException', () => {});
      process.on('unhandledRejection', () => {});
      if (process.env.WSR_TEST_PREPARE_FAIL === '1') throw new Error('DEMO_PRIVATE_STARTUP_DETAIL');
    },
    getRequestHandler() { return async (_req, res) => res.end('DEMO'); },
    async close() {
      if (process.env.WSR_TEST_CLOSE === 'reject') throw new Error('DEMO_PRIVATE_CLOSE_DETAIL');
      if (process.env.WSR_TEST_CLOSE === 'hang') await new Promise(() => {});
    }
  };
}`;
registerHooks({
  resolve(specifier, context, nextResolve) {
    return specifier === "next" ? { url: "data:text/javascript," + encodeURIComponent(source), shortCircuit: true }
      : nextResolve(specifier, context);
  },
});
