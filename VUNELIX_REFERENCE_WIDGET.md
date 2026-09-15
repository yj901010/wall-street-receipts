# Vunelix reference-display pilot

Status: local implementation only; no deployment domain has been selected.
This does not complete live-data acceptance, P3 scoring or public deployment.

## Route and switches

Open `/market/reference/AAPL` on the local Web server. The route is intentionally
absent from public navigation and carries `noindex, nofollow` (not access control).
`VUNELIX_REFERENCE_WIDGET` is a **server-only** runtime setting:

| Value | Result |
| --- | --- |
| Unset or `disabled` | No vendor script, element or market-data request |
| `enabled` | Consent button only; loading starts after a visitor click |
| Any other value, including empty/whitespace/case variants | Configuration error; connection stays off |

No API key, credential, user-supplied embed, arbitrary symbol or provider URL is
accepted. The one literal route requests `NASDAQ:AAPL`. Query parameters do not
change that identifier. This is not a canonical instrument mapping or coverage
claim. Keep the provider's branding and links visible.

The KO/EN wrapper is server-rendered using the existing locale preference. The
vendor component is explicitly English/light. Its update time and timezone remain
provider-owned; the wrapper does not convert them to KST, fabricate a timestamp,
or label the connection LIVE. DEMO prices are never a fallback.

## Loading and trust boundary

After opt-in the page loads the public module URL declared in `config.ts`, then
mounts the documented native `vunelix-symbol-overview` element. The fixed version
query is **not a cryptographic content pin**: remote code can change. Shadow DOM
is not a security sandbox; third-party JavaScript executes in the first-party
page with its ambient browser permissions. The initial script request limits its
referrer to the origin, but that does not restrict subsequent vendor activity.
Do not enable this on an authenticated/sensitive origin without a separate-origin
design and security review. No reverse-engineered endpoints or demo authorization
tokens are used.

The 15-second deadline bounds only the wrapper's script wait. `loaded` proves
neither a rendered widget nor a received quote; an empty component or provider
domain/permission error remains unverified. Error/timeout never retries or invents
a price. A timed-out module might still execute later. Exit/retry links use full
document navigation; removing a script tag cannot terminate already executed
vendor code. Unmount cleanup detaches wrapper callbacks only. Changing the server
flag does not stop already-open pages; they must unload/reload.

No vendor values are read into our providers, API, DB, fixtures, snapshots,
receipts or calculators. There is no proxy, scraping, export, raw-feed import,
return/alpha/MFE/MAE/target-hit calculation, ranking or new dependency.

## Local verification, without connecting Vunelix

Run Web lint and Vitest normally. Build a secret-free source-identical Web mirror
under `.cache`, preserving the user's `apps/web/next-env.d.ts`; copy source,
fixtures and explicit configs only, never `.env*`, credentials or generated output.
From that mirror's `apps/web`, with a clean non-secret environment:

```text
node node_modules/next/dist/bin/next build
node node_modules/@playwright/test/cli.js test --config reference-widget/playwright.config.ts
node node_modules/@playwright/test/cli.js test --config reference-widget/public.config.ts
```

The test config owns a fresh loopback production server on port 3118 (no reuse),
enables only this pilot and tests 1440/1280/390 widths. Every non-local browser
request is intercepted: only the exact script URL gets an explicit **test transport
module with no quote**. Empty/error/hung responses are also tested. No real vendor
JavaScript or market connection runs. Normal public E2E additionally verifies the
default-off route using an owned loopback development server on port 3119. Invoke
Node directly: a package-manager auto-install through a shared dependency junction
can change module identity mid-test. These checks cannot establish actual provider responsiveness,
internal errors, accessibility, quote accuracy or live widget layout.

## Before any actual public connection

1. Choose a non-sensitive deployment origin and register it with Vunelix. Do not
   assume localhost is accepted; no account/domain registration was performed here.
2. Confirm public-display/commercial rights, branding, privacy requirements and
   underlying US feed/coverage/delay with the provider for this site.
3. Review remote-code exposure and current vendor instructions, then explicitly
   enable the server flag for a bounded pilot. A visitor still has to click.
4. During market hours verify domain authorization, rendered AAPL identity,
   timestamp/timezone, actual delay, mobile/keyboard behavior, empty/disconnect and
   reload behavior. Record evidence separately from mocked tests. If unresolved,
   leave the flag disabled. Never treat the widget as scoring evidence.

Official pages reviewed 2026-09-15: [widget installation](https://vunelix.com/widgets/symbol-overview)
asks for signup/domain registration while another section says signup is not
required; we follow the stricter installation prerequisite pending confirmation.
The [FAQ](https://vunelix.com/faq) advertises free personal/commercial embedding.
Neither statement is our independent verification of feed rights or latency.
Review the [terms](https://vunelix.com/terms) and
[disclaimer](https://vunelix.com/disclaimer) before public activation; embedding
permission must not be inferred to authorize extraction or redistribution.
