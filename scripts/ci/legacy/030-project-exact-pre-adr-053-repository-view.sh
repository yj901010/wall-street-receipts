python <<'PYTHON'
import hashlib
import json
import os
import re
import shutil
from pathlib import Path

WORKFLOW_SELF_DIGEST = "cf11a7cc462373c2634fce9393bc8312c2c30b1820eee855918afd1892e29f8c"  # ADR053_WORKFLOW_SELF_DIGEST

def require(condition, message):
    if not condition:
        raise ValueError(message)

def raw_digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def normalized_digest(path):
    content = path.read_bytes().replace(b"\r\n", b"\n")
    return hashlib.sha256(content).hexdigest()

def masked_workflow_digest(path):
    content = path.read_bytes().replace(b"\r\n", b"\n")
    patterns = (
        rb'(?m)^(          WORKFLOW_SELF_DIGEST = ")[0-9a-f]{64}("  # ADR053_WORKFLOW_SELF_DIGEST)$',
        rb'(?m)^(          CUSTODY_MANIFEST_DIGEST = ")[0-9a-f]{64}("  # ADR053_CUSTODY_MANIFEST_DIGEST)$',
    )
    for pattern in patterns:
        content, replacement_count = re.subn(
            pattern,
            rb'\1<ADR053-MASKED-SHA256>\2',
            content,
        )
        require(
            replacement_count == 1,
            "ADR-053 workflow self-hash slot changed",
        )
    return hashlib.sha256(content).hexdigest()

def text_metadata(raw_sha256, normalized_sha256):
    return {
        "kind": "text",
        "rawSha256": raw_sha256,
        "normalizedSha256": normalized_sha256,
    }

def binary_metadata(raw_sha256):
    return {
        "kind": "binary",
        "rawSha256": raw_sha256,
        "normalizedSha256": None,
    }

def self_metadata(masked_sha256):
    return {
        "kind": "self",
        "rawSha256": masked_sha256,
        "normalizedSha256": None,
    }

current_files = {
    Path(".env.example"): text_metadata(
        "a2f7c4f31f22c8d907557c1f769a439e36b818fdae7e331ef7607d3fa9ec0f22",
        "a2f7c4f31f22c8d907557c1f769a439e36b818fdae7e331ef7607d3fa9ec0f22",
    ),
    Path(".github/workflows/ci.yml"): self_metadata(
        WORKFLOW_SELF_DIGEST
    ),
    Path("README.md"): text_metadata(
        "2392fd1dcb3ec3de6d5a5f3f769f00dcd9dde9f7d6fd5ec6132a43728287963e",
        "2392fd1dcb3ec3de6d5a5f3f769f00dcd9dde9f7d6fd5ec6132a43728287963e",
    ),
    Path("apps/api/README.md"): text_metadata(
        "ea8a43858b7364181050467f2030f428c895e36c4675284c54b2816a37fa3805",
        "ea8a43858b7364181050467f2030f428c895e36c4675284c54b2816a37fa3805",
    ),
    Path("deploy/home-server/README.md"): text_metadata(
        "621f106b8d776d0d8bb7da34e46cde51f15b5584bee33d2f4e9b10569f516785",
        "621f106b8d776d0d8bb7da34e46cde51f15b5584bee33d2f4e9b10569f516785",
    ),
    Path("IMPLEMENTATION_LOG.md"): text_metadata(
        "50430cb8983da216176bab7168699e77a3b41f4dd47fb25426ae529257717b8e",
        "50430cb8983da216176bab7168699e77a3b41f4dd47fb25426ae529257717b8e",
    ),
    Path(
        "decisions/ADR-053-exact-sec-manifest-audit-web-consumer.md"
    ): text_metadata(
        "fac2a96942f362dc733f6cb4aaba14ba6910a8c1ce97c6a7a5ee22c5477d2d38",
        "fac2a96942f362dc733f6cb4aaba14ba6910a8c1ce97c6a7a5ee22c5477d2d38",
    ),
    Path("apps/web/src/app/layout.test.tsx"): text_metadata(
        "2fc4be513470c7c47d258ed9a7da465a4c3858b5951d96b2ea8c9218b884b00c",
        "2fc4be513470c7c47d258ed9a7da465a4c3858b5951d96b2ea8c9218b884b00c",
    ),
    Path("apps/web/src/app/layout.tsx"): text_metadata(
        "ae0b7b9ff2e631854908007119a9547b28684d814de1afabb1c1e5e9a4868374",
        "ae0b7b9ff2e631854908007119a9547b28684d814de1afabb1c1e5e9a4868374",
    ),
    Path("deploy/home-server/compose.yaml"): text_metadata(
        "b771125ee175e798086e0150634099323779fe9212880fc2727174facf257aed",
        "b771125ee175e798086e0150634099323779fe9212880fc2727174facf257aed",
    ),
    Path("deploy/home-server/web.Dockerfile"): text_metadata(
        "fda76b530e25ab50eee1f7766f3ad015d322e06a07bea45cd6a5ab304d61866c",
        "fda76b530e25ab50eee1f7766f3ad015d322e06a07bea45cd6a5ab304d61866c",
    ),
    Path("scripts/verify-home-server-deployment.py"): text_metadata(
        "b036c5d8d6a50f55282c639bef0d6cf8eb470588ff1e8fdfa22756d9b1dc09d8",
        "b036c5d8d6a50f55282c639bef0d6cf8eb470588ff1e8fdfa22756d9b1dc09d8",
    ),
    Path(
        "apps/api/src/test/java/com/wallstreetreceipts/api/web/"
        "filinghistory/SecAuditDemoFixtureParityTest.java"
    ): text_metadata(
        "36b4e4e9dee6534063ebce19a0115c48147aab330e968695afe352a916113e60",
        "36b4e4e9dee6534063ebce19a0115c48147aab330e968695afe352a916113e60",
    ),
    Path("apps/web/e2e/sec-manifest-audit.spec.ts"): text_metadata(
        "f8f6c6645a8a8ffed9880d9cd890a06dc75564c987f7d3d1b7e94cdd5c8ab282",
        "f8f6c6645a8a8ffed9880d9cd890a06dc75564c987f7d3d1b7e94cdd5c8ab282",
    ),
    Path("apps/web/public/og.png"): binary_metadata(
        "c7503e1ee98f594cea1f07cc49bb3066d6a9fb9e703c1fa6100eb2bedc389b48"
    ),
    Path(
        "apps/web/src/app/research/sec/filing-history/error.tsx"
    ): text_metadata(
        "dc9b356ef192a492c937466e7730d645cc16bc55bb49bd594fa6d01d1f72c6ee",
        "dc9b356ef192a492c937466e7730d645cc16bc55bb49bd594fa6d01d1f72c6ee",
    ),
    Path(
        "apps/web/src/app/research/sec/filing-history/loading.tsx"
    ): text_metadata(
        "3458b4e69e865421d86cdf6dca5a3e05b576ca2070b14f651a824e4c178f8599",
        "3458b4e69e865421d86cdf6dca5a3e05b576ca2070b14f651a824e4c178f8599",
    ),
    Path(
        "apps/web/src/app/research/sec/filing-history/messages.ts"
    ): text_metadata(
        "38c1ddb98a4e964d0d2812714e7070dbc28a21994ef3bd584e1637b708aadcc1",
        "38c1ddb98a4e964d0d2812714e7070dbc28a21994ef3bd584e1637b708aadcc1",
    ),
    Path(
        "apps/web/src/app/research/sec/filing-history/not-found.tsx"
    ): text_metadata(
        "0c18f8a4d7ecfd841e1e273ffbddb985a623651bd4ae2c87a4c5ff00b179d425",
        "0c18f8a4d7ecfd841e1e273ffbddb985a623651bd4ae2c87a4c5ff00b179d425",
    ),
    Path(
        "apps/web/src/app/research/sec/filing-history/page.test.tsx"
    ): text_metadata(
        "577ed4adb35246a3a1de5bf2f9a5ac8524b8ccae08faf163f678bd455257e212",
        "577ed4adb35246a3a1de5bf2f9a5ac8524b8ccae08faf163f678bd455257e212",
    ),
    Path(
        "apps/web/src/app/research/sec/filing-history/page.tsx"
    ): text_metadata(
        "7a7bddff85fef3d18543f3cdb9022b8239bf8fbe37f60c532d593015cb26727c",
        "7a7bddff85fef3d18543f3cdb9022b8239bf8fbe37f60c532d593015cb26727c",
    ),
    Path(
        "apps/web/src/app/research/sec/filing-history/"
        "sec-manifest-audit-locator.tsx"
    ): text_metadata(
        "433de8fd5ff46d33c0e6ac3711b88bea44734d765f09f19158961b2c7a1ed037",
        "433de8fd5ff46d33c0e6ac3711b88bea44734d765f09f19158961b2c7a1ed037",
    ),
    Path(
        "apps/web/src/app/research/sec/filing-history/"
        "sec-manifest-audit-view.tsx"
    ): text_metadata(
        "5165951eacfdd2fdfa66803a5d259facb0af91d0b805c2f015ea74a8a8f295ab",
        "5165951eacfdd2fdfa66803a5d259facb0af91d0b805c2f015ea74a8a8f295ab",
    ),
    Path(
        "apps/web/src/app/research/sec/filing-history/"
        "sec-manifest-audit.module.css"
    ): text_metadata(
        "31f7f68c2624c17b0bd7369f42c2182d7101dbd80f89c2f8d0553da731ef3e91",
        "31f7f68c2624c17b0bd7369f42c2182d7101dbd80f89c2f8d0553da731ef3e91",
    ),
    Path(
        "apps/web/src/lib/providers/"
        "api-sec-manifest-audit-provider.server.test.ts"
    ): text_metadata(
        "345b7692665ce547795e0f567bc5261d174e013959dd0e23664fc30914949e78",
        "345b7692665ce547795e0f567bc5261d174e013959dd0e23664fc30914949e78",
    ),
    Path(
        "apps/web/src/lib/providers/"
        "api-sec-manifest-audit-provider.server.ts"
    ): text_metadata(
        "6abbb44fad63e68be317f0eb6728e92e2c1dfed69e45cf8684492f7e80f73af5",
        "6abbb44fad63e68be317f0eb6728e92e2c1dfed69e45cf8684492f7e80f73af5",
    ),
    Path(
        "apps/web/src/lib/providers/"
        "fixture-sec-manifest-audit-provider.test.ts"
    ): text_metadata(
        "f1927e1908271f515d263bf0ebc34651145e8fb3f1382d62a8562fad266328e4",
        "f1927e1908271f515d263bf0ebc34651145e8fb3f1382d62a8562fad266328e4",
    ),
    Path(
        "apps/web/src/lib/providers/"
        "fixture-sec-manifest-audit-provider.ts"
    ): text_metadata(
        "e5846df7da74be3b216eea0952919b831d6cfb19f7d0d61820d92ac6be6a3793",
        "e5846df7da74be3b216eea0952919b831d6cfb19f7d0d61820d92ac6be6a3793",
    ),
    Path(
        "apps/web/src/lib/providers/fixtures/"
        "sec-manifest-audit-demo.json"
    ): text_metadata(
        "398c3b73f616fc97f42fa99c42808514ecf675eaaf5833dba45e1ae1d23226f5",
        "398c3b73f616fc97f42fa99c42808514ecf675eaaf5833dba45e1ae1d23226f5",
    ),
    Path(
        "apps/web/src/lib/providers/"
        "sec-manifest-audit-adapter.test.ts"
    ): text_metadata(
        "b31c4f7137a603a514d3f1c9274b8a55b7068def93914a9537d9fa9639014ede",
        "b31c4f7137a603a514d3f1c9274b8a55b7068def93914a9537d9fa9639014ede",
    ),
    Path(
        "apps/web/src/lib/providers/sec-manifest-audit-adapter.ts"
    ): text_metadata(
        "366a5450cbbf0ebe7f4a47e619b5c2aaf4f01ffcaf55b8ae2af1aee562022024",
        "366a5450cbbf0ebe7f4a47e619b5c2aaf4f01ffcaf55b8ae2af1aee562022024",
    ),
    Path(
        "apps/web/src/lib/providers/"
        "sec-manifest-audit-provider.server.test.ts"
    ): text_metadata(
        "1ad233ff731cc462eda294f4bd8645025616b72ebf7ca6b9a8686c7e6b03ce1c",
        "1ad233ff731cc462eda294f4bd8645025616b72ebf7ca6b9a8686c7e6b03ce1c",
    ),
    Path(
        "apps/web/src/lib/providers/"
        "sec-manifest-audit-provider.server.ts"
    ): text_metadata(
        "da313b486639c735d5fc2e988ae4f69bfb6cb5cabfdc176b5fdd2c1f9bfb3b7e",
        "da313b486639c735d5fc2e988ae4f69bfb6cb5cabfdc176b5fdd2c1f9bfb3b7e",
    ),
    Path(
        "apps/web/src/lib/providers/sec-manifest-audit-provider.ts"
    ): text_metadata(
        "90e92dedf6478aeda95c73982c638366586ad8bb42fd4f13d5a43acffd6f2e82",
        "90e92dedf6478aeda95c73982c638366586ad8bb42fd4f13d5a43acffd6f2e82",
    ),
    Path(
        "apps/web/src/lib/providers/"
        "sec-manifest-audit-query.test.ts"
    ): text_metadata(
        "67d40a72fddbf2db6d76c7a94d61d2448bb348c051f124c68a8daa35a231162a",
        "67d40a72fddbf2db6d76c7a94d61d2448bb348c051f124c68a8daa35a231162a",
    ),
    Path(
        "apps/web/src/lib/providers/sec-manifest-audit-query.ts"
    ): text_metadata(
        "f1866f5847f81a8b760bd4cbcfef6afa8d4fb56a86b529359e22c2c826cbe3f3",
        "f1866f5847f81a8b760bd4cbcfef6afa8d4fb56a86b529359e22c2c826cbe3f3",
    ),
}
modified_paths = {
    Path(".env.example"),
    Path(".github/workflows/ci.yml"),
    Path("README.md"),
    Path("apps/api/README.md"),
    Path("deploy/home-server/README.md"),
    Path("IMPLEMENTATION_LOG.md"),
    Path("apps/web/src/app/layout.test.tsx"),
    Path("apps/web/src/app/layout.tsx"),
    Path("deploy/home-server/compose.yaml"),
    Path("deploy/home-server/web.Dockerfile"),
    Path("scripts/verify-home-server-deployment.py"),
}
added_paths = set(current_files) - modified_paths
route_root = Path(
    "apps/web/src/app/research/sec/filing-history"
)
actual_added_paths = {
    path for path in route_root.rglob("*") if path.is_file()
}
actual_added_paths.update(
    Path("apps/web/src/lib/providers").glob("*sec-manifest-audit*")
)
actual_added_paths.update(
    Path("apps/web/src/lib/providers/fixtures").glob(
        "sec-manifest-audit-*"
    )
)
actual_added_paths.update(
    Path("apps/web/e2e").glob("*sec-manifest-audit*")
)
actual_added_paths.update(
    Path("decisions").glob("ADR-053-*.md")
)
actual_added_paths.update(
    Path(
        "apps/api/src/test/java/com/wallstreetreceipts/api/web/"
        "filinghistory"
    ).glob("SecAudit*.java")
)
public_root = Path("apps/web/public")
if public_root.exists():
    actual_added_paths.update(
        path for path in public_root.rglob("*") if path.is_file()
    )
require(
    len(current_files) == 36
    and len(modified_paths) == 11
    and len(added_paths) == 25
    and actual_added_paths == added_paths,
    "ADR-053 source/config/test/asset/document delta inventory changed",
)
require(
    Path("apps/web/next-env.d.ts") not in current_files,
    "Generated Next.js declaration must stay outside ADR-053 custody",
)
for path, metadata in current_files.items():
    require(path.is_file(), f"ADR-053 current path is missing: {path}")
    if metadata["kind"] == "text":
        require(
            raw_digest(path) == metadata["rawSha256"]
            and
            normalized_digest(path) == metadata["normalizedSha256"],
            f"ADR-053 current text bytes changed: {path}",
        )
    elif metadata["kind"] == "binary":
        require(
            raw_digest(path) == metadata["rawSha256"]
            and metadata["normalizedSha256"] is None,
            f"ADR-053 binary custody metadata changed: {path}",
        )
    else:
        require(
            metadata["kind"] == "self"
            and path == Path(".github/workflows/ci.yml")
            and metadata["normalizedSha256"] is None
            and masked_workflow_digest(path)
            == metadata["rawSha256"],
            "ADR-053 workflow self-custody bytes changed",
        )

projection_root = (
    Path(os.environ["RUNNER_TEMP"]) / "wsr-adr053-current-view"
)
require(
    not projection_root.exists(),
    f"ADR-053 projection custody already exists: {projection_root}",
)
projection_root.mkdir(parents=True)
for path in current_files:
    custody_path = projection_root / path
    custody_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, custody_path)
manifest = {
    path.as_posix(): metadata
    for path, metadata in current_files.items()
}
(projection_root / "manifest.json").write_text(
    json.dumps(manifest, sort_keys=True),
    encoding="utf-8",
)
(projection_root / ".prepared").write_text(
    "ADR-053 exact current bytes saved before historical projection\n",
    encoding="utf-8",
)

for path in added_paths:
    path.unlink()

def read_text(path):
    return path.read_bytes().replace(b"\r\n", b"\n").decode("utf-8")

def replace_once(path, current, historical):
    content = read_text(path)
    require(
        content.count(current) == 1,
        f"ADR-053 reverse-projection delta changed: {path}",
    )
    path.write_bytes(content.replace(current, historical, 1).encode("utf-8"))

def replace_region(path, start, end, replacement=""):
    content = read_text(path)
    require(
        content.count(start) == 1 and content.count(end) == 1,
        f"ADR-053 reverse-projection region changed: {path}",
    )
    start_index = content.index(start)
    end_index = content.index(end, start_index)
    path.write_bytes(
        (
            content[:start_index]
            + replacement
            + content[end_index:]
        ).encode("utf-8")
    )

env_path = Path(".env.example")
replace_once(env_path, "SITE_ORIGIN=http://localhost:3000\n", "")
replace_once(
    env_path,
    (
        "# Exact immutable SEC filing-history manifest audit surface. "
        "API mode uses the\n"
        "# private server-side API_BASE_URL and never falls back to "
        "the synthetic DEMO.\n"
        "SEC_MANIFEST_AUDIT_PROVIDER=api\n"
        "\n"
    ),
    "",
)

readme_path = Path("README.md")
replace_once(
    readme_path,
    (
        "remains P8 work. ADR-053 adds a Korean-default, same-origin "
        "exact SEC manifest\n"
        "audit consumer without adding a manifest list, latest "
        "selector, or live-data\n"
        "claim. P1 provides a\n"
    ),
    "remains P8 work. P1 provides a\n",
)
replace_once(
    readme_path,
    '$env:SEC_MANIFEST_AUDIT_PROVIDER = "fixture"\n',
    "",
)
replace_once(
    readme_path,
    '$env:SITE_ORIGIN = "http://localhost:3000"\n',
    "",
)
replace_region(
    readme_path,
    (
        "On macOS or Linux, use `cp .env.example .env`, then start "
        "the web process with:\n"
    ),
    "`SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run`.",
    (
        "On macOS or Linux, use `cp .env.example .env`, start the "
        "web process with\n"
        "`CALL_AUDIT_PROVIDER=api "
        "API_BASE_URL=http://localhost:8080 "
        "pnpm --dir apps/web dev`,\n"
        "then run `cd apps/api` followed by\n"
    ),
)
replace_once(
    readme_path,
    (
        "- Exact SEC manifest audit locator:\n"
        "  <http://localhost:3000/research/sec/filing-history>\n"
    ),
    "",
)
replace_region(
    readme_path,
    "The exact SEC manifest audit page uses an independent server-only selector.\n",
    "The completed `feature/p2-call-outcome-audit-api` slice",
)
replace_once(
    readme_path,
    (
        "ADR-053 establishes a Korean-default, same-origin web "
        "consumer and exact\n"
        "locator for those resources without adding a latest/"
        "company selector or\n"
        "fixture fallback.\n\n"
    ),
    "",
)
replace_once(
    readme_path,
    (
        "over already-persisted manifests. ADR-053 consumes one "
        "selected resource\n"
        "server-side through Next; there is still no scheduler, "
        "startup collector,\n"
        "command-line trigger, or current/latest company-filings "
        "selector.\n"
    ),
    (
        "over already-persisted manifests; there is still no "
        "scheduler, startup\n"
        "collector, command-line trigger, current/latest company-"
        "filings selector, or\n"
        "web publication.\n"
    ),
)
replace_region(
    readme_path,
    "This audit read makes no provider network request and needs no API key, SEC\n",
    "\n\nThe ADR-042 service itself remains an internal application boundary",
    (
        "This audit read makes no network request and needs no API "
        "key, SEC account,\n"
        "domain, server access, operator token, or "
        "`SEC_CONTACT_EMAIL`. Tests keep the\n"
        "SEC provider disabled. Production Caddy still routes "
        "public traffic only to\n"
        "Next, so ADR-052 is a backend contract for a later same-"
        "origin Korean UI rather\n"
        "than a claim that the Spring origin is already Internet-"
        "reachable."
    ),
)

api_readme_path = Path("apps/api/README.md")
replace_once(
    api_readme_path,
    (
        "않는다. ADR-053의 same-origin web consumer가 Next server에서 "
        "이 private API를\n"
        "읽으며 browser는 Spring origin을 직접 호출하지 않는다.\n"
    ),
    (
        "않으므로 이 route는 후속 same-origin web consumer를 위한 "
        "backend contract다.\n"
    ),
)
replace_region(
    api_readme_path,
    "\n### Exact manifest audit web consumer\n",
    "\n### Operator-controlled collection attempt\n",
)

deployment_readme_path = Path("deploy/home-server/README.md")
replace_once(
    deployment_readme_path,
    (
        "fact collector for the public **DEMO** site. It also "
        "carries ADR-053's\n"
        "same-origin exact SEC manifest audit consumer without "
        "exposing Spring directly.\n"
        "These files are independent from the root development "
        "`compose.yaml` and never\n"
        "load the ignored root `.env`.\n"
    ),
    (
        "fact collector for the public **DEMO** site. They are "
        "independent from the\n"
        "root development `compose.yaml` and never load the ignored "
        "root `.env`.\n"
    ),
)
replace_region(
    deployment_readme_path,
    "  providers remain off. Existing market/call surfaces remain explicitly\n",
    "- PostgreSQL receives its password as a Compose file secret.",
    (
        "  providers remain off. Public pages are explicitly "
        "DEMO/fixture-backed.\n"
    ),
)
replace_once(
    deployment_readme_path,
    (
        "context, checks the allowlisted public routes and database "
        "evidence, and then\n"
        "removes its own containers, volumes, images, and temporary "
        "secret. The CA is\n"
        "never installed into host trust.\n"
    ),
    (
        "context, checks all 12 public routes and database evidence, "
        "and then removes\n"
        "its own containers, volumes, images, and temporary secret. "
        "The CA is never\n"
        "installed into host trust.\n"
    ),
)
replace_once(
    deployment_readme_path,
    (
        "the already installed workspace pnpm/Playwright "
        "dependencies and runs the full\n"
        "configured suite at 1440, 1280, and 390 pixels through the "
        "same loopback Caddy\n"
        "endpoint; retries are forced to zero, and its output stays "
        "inside the harness-\n"
        "owned temporary directory:\n"
    ),
    (
        "the already installed workspace pnpm/Playwright "
        "dependencies and runs all 72\n"
        "checks at 1440, 1280, and 390 pixels through the same "
        "loopback Caddy endpoint;\n"
        "retries are forced to zero, and its output stays inside the "
        "harness-owned\n"
        "temporary directory:\n"
    ),
)
replace_region(
    deployment_readme_path,
    "\n## Exact SEC audit web runtime\n",
    "\n## Future server baseline\n",
)
replace_once(
    deployment_readme_path,
    (
        "The Compose web service derives "
        "`SITE_ORIGIN=https://${WSR_DOMAIN}` from that\n"
        "same reviewed value. Do not point social metadata at a "
        "placeholder, loopback,\n"
        "different host, path, query, or fragment during public "
        "cutover.\n\n"
    ),
    "",
)
replace_once(
    deployment_readme_path,
    (
        "- Keep `SEC_MANIFEST_AUDIT_PROVIDER=api` in the production "
        "web container. The\n"
        "  committed fixture is only a visibly synthetic local test "
        "source and is never\n"
        "  a production fallback for absent, future, corrupt, or "
        "unavailable evidence.\n"
    ),
    "",
)

implementation_log_path = Path("IMPLEMENTATION_LOG.md")
implementation_log = read_text(implementation_log_path)
implementation_marker = (
    "\n## 2026-08-31 — ADR-053 exact SEC manifest audit web consumer\n"
)
require(
    implementation_log.count(implementation_marker) == 1,
    "ADR-053 implementation-log projection marker changed",
)
implementation_log_path.write_bytes(
    implementation_log[
        :implementation_log.index(implementation_marker)
    ].encode("utf-8")
)

layout_path = Path("apps/web/src/app/layout.tsx")
layout = read_text(layout_path)
layout_start = "export function readSiteOrigin(): URL {"
layout_end = "\n\nexport default async function RootLayout"
require(
    layout.count(layout_start) == 1 and layout.count(layout_end) == 1,
    "ADR-053 layout metadata projection region changed",
)
start_index = layout.index(layout_start)
end_index = layout.index(layout_end, start_index)
historical_metadata = (
    "export async function generateMetadata(): Promise<Metadata> {\n"
    "  const messages = getCommonMessages(await getLocale());\n"
    "\n"
    "  return {\n"
    "    title: messages.metadata.title,\n"
    "    description: messages.metadata.description,\n"
    "  };\n"
    "}"
)
layout_path.write_bytes(
    (
        layout[:start_index]
        + historical_metadata
        + layout[end_index:]
    ).encode("utf-8")
)

layout_test_path = Path("apps/web/src/app/layout.test.tsx")
replace_once(
    layout_test_path,
    'import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";\n',
    'import { beforeEach, describe, expect, it, vi } from "vitest";\n',
)
replace_once(
    layout_test_path,
    'import RootLayout, { generateMetadata, readSiteOrigin } from "./layout";\n',
    'import RootLayout, { generateMetadata } from "./layout";\n',
)
replace_once(
    layout_test_path,
    (
        "  beforeEach(() => {\n"
        "    i18nServer.getLocale.mockReset();\n"
        "    delete process.env.SITE_ORIGIN;\n"
        "  });\n"
        "\n"
        "  afterEach(() => {\n"
        "    delete process.env.SITE_ORIGIN;\n"
        "  });\n"
    ),
    (
        "  beforeEach(() => {\n"
        "    i18nServer.getLocale.mockReset();\n"
        "  });\n"
    ),
)
layout_test = read_text(layout_test_path)
metadata_test_start = "    const metadata = await generateMetadata();"
require(
    layout_test.count(metadata_test_start) == 1
    and layout_test.endswith("  });\n});\n"),
    "ADR-053 layout metadata test tail changed",
)
metadata_index = layout_test.index(metadata_test_start)
layout_test_path.write_bytes(
    (
        layout_test[:metadata_index]
        + (
            "    await expect(generateMetadata()).resolves.toEqual({\n"
            '      title: "Wall Street Receipts",\n'
            "      description,\n"
            "    });\n"
            "  });\n"
            "});\n"
        )
    ).encode("utf-8")
)

compose_path = Path("deploy/home-server/compose.yaml")
replace_once(
    compose_path,
    "      SEC_MANIFEST_AUDIT_PROVIDER: api\n",
    "",
)
replace_once(
    compose_path,
    "      SITE_ORIGIN: https://${WSR_DOMAIN:-invalid.example}\n",
    "",
)

dockerfile_path = Path("deploy/home-server/web.Dockerfile")
replace_once(
    dockerfile_path,
    "    SEC_MANIFEST_AUDIT_PROVIDER=api \\\n",
    "",
)
replace_once(
    dockerfile_path,
    "    SITE_ORIGIN=http://localhost:3000 \\\n",
    "",
)

deployment_verifier_path = Path(
    "scripts/verify-home-server-deployment.py"
)
replace_once(
    deployment_verifier_path,
    '        "SEC_MANIFEST_AUDIT_PROVIDER": "api",\n',
    "",
)
replace_once(
    deployment_verifier_path,
    '        "SITE_ORIGIN": "https://wsr.invalid",\n',
    "",
)
replace_once(
    deployment_verifier_path,
    (
        "    expect_rejected(\n"
        "        document,\n"
        "        lambda value: "
        'value["services"]["web"]["environment"].__setitem__(\n'
        '            "SEC_MANIFEST_AUDIT_PROVIDER", "fixture"\n'
        "        ),\n"
        '        "production SEC manifest fixture fallback",\n'
        "    )\n"
        "    expect_rejected(\n"
        "        document,\n"
        "        lambda value: "
        'value["services"]["web"]["environment"].__setitem__(\n'
        '            "SITE_ORIGIN", "http://localhost:3000"\n'
        "        ),\n"
        '        "non-public social metadata origin",\n'
        "    )\n"
    ),
    "",
)

workflow_path = Path(".github/workflows/ci.yml")
replace_region(
    workflow_path,
    "\n      - name: Project exact pre-ADR-053 repository view\n",
    "\n      - name: Project exact pre-ADR-052 repository view\n",
)
replace_region(
    workflow_path,
    "\n      - name: Restore exact ADR-053 repository view\n",
    "\n      - name: Validate analyst-call revision lineage\n",
)
replace_region(
    workflow_path,
    "\n      - name: Verify SEC manifest API-mode failure boundary\n",
    "\n  call-audit-integration:\n",
)

historical_hashes = {
    env_path:
        "513b03a1685870c7295fab836c0f345f5c74998850c59730045d5e098944f4eb",
    workflow_path:
        "b2f716b7578ffc4bb8754fd4e266df2aec61e8c3af6d0fc2e29a72db1ec78c6c",
    readme_path:
        "0d6f1d93ee6abbd8474081e1d135084d9353fbf144396c75e1a9cee3f2add31e",
    api_readme_path:
        "f845309b1b99493da4a0508243b0100ed98d45c4d73bc51c115bf2e0fadfe1bc",
    deployment_readme_path:
        "fd887e14084b87d8bd8bacade9cb299d189fa5c542f2f32484222e1e9984b73d",
    implementation_log_path:
        "a756acfa42b67e5fbc275e4c600a3633ceab8ffcf49aeacf66a7fcf211666aec",
    layout_test_path:
        "e95a8a78ee4a8665096e5020eb9628e87dda1f28e92381b15f9a7b5ae5eb442e",
    layout_path:
        "f80d40bd6dcb30efb746f0605308f907c6b0c425c34b250b76db02bd522cf3ff",
    compose_path:
        "684ab8310edcb8e40d3734f8382ca555742330636b871dc7dddb11f70c040a22",
    dockerfile_path:
        "20e249476460bb3b6f54ecaf3d20c617f7ff236744ff12cd537ed40061b9e1a0",
    deployment_verifier_path:
        "9ac71cb21fa21f85c8118eedcb4e91dd15a1d8cd99b0fd5637670ad5d15fa7d8",
}
require(
    all(not path.exists() for path in added_paths),
    "ADR-053 added file survived historical projection",
)
for path, expected_hash in historical_hashes.items():
    require(
        raw_digest(path) == expected_hash
        and normalized_digest(path) == expected_hash,
        f"ADR-053 pre-4af6cbd reverse projection changed: {path}",
    )
print(
    "Projected the exact pre-ADR-053 4af6cbd source/config/test/asset/"
    "document view before the ADR-052 historical projection"
)
PYTHON
