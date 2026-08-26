#!/usr/bin/env python3
"""Semantic guard for the ADR-046 home-server deployment boundary."""

from __future__ import annotations

import copy
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEPLOY = ROOT / "deploy" / "home-server"
COMPOSE = DEPLOY / "compose.yaml"


class ContractError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def render_compose() -> dict[str, Any]:
    cache_root = ROOT / ".cache"
    cache_root.mkdir(exist_ok=True)
    temporary_path = cache_root / f"wsr-home-config-{secrets.token_hex(8)}"
    temporary_path.mkdir(mode=0o777 if os.name == "nt" else 0o700)
    try:
        secret_path = temporary_path / "postgres_password"
        secret_path.write_text("configuration-only-secret\n", encoding="utf-8")
        env_path = temporary_path / "compose.env"
        env_path.write_text(
            "\n".join(
                (
                    "WSR_DOMAIN=wsr.invalid",
                    "WSR_ACME_EMAIL=operator@wsr.invalid",
                    "WSR_IMAGE_TAG=0123456789abcdef",
                    f"WSR_POSTGRES_PASSWORD_FILE={secret_path.as_posix()}",
                    "WSR_REHEARSAL_PORT=18080",
                    "WSR_INGRESS_MODE=unknown",
                    "WSR_PUBLIC_IP_POLICY=unknown",
                    "WSR_PUBLIC_IPV4=unknown",
                    "WSR_PUBLIC_IPV6=unknown",
                )
            )
            + "\n",
            encoding="utf-8",
        )
        environment = os.environ.copy()
        for key in tuple(environment):
            upper_key = key.upper()
            if (
                upper_key.startswith(("WSR_", "COMPOSE_", "DOCKER_"))
                or upper_key in {"HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY"}
            ):
                environment.pop(key)
        process = subprocess.run(
            (
                "docker",
                "compose",
                "--env-file",
                str(env_path),
                "--file",
                str(COMPOSE),
                "--profile",
                "production",
                "--profile",
                "rehearsal",
                "config",
                "--format",
                "json",
            ),
            cwd=ROOT,
            env=environment,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        if process.returncode != 0:
            raise ContractError(
                "Docker Compose could not render the independent deployment file:\n"
                + process.stderr.strip()
            )
        return json.loads(process.stdout)
    finally:
        shutil.rmtree(temporary_path)


def service_networks(service: dict[str, Any]) -> set[str]:
    networks = service.get("networks", {})
    return set(networks if isinstance(networks, dict) else networks)


def environment_map(service: dict[str, Any]) -> dict[str, str]:
    environment = service.get("environment", {})
    require(isinstance(environment, dict), "Rendered service environment must be a map")
    return {str(key): "" if value is None else str(value) for key, value in environment.items()}


def validate_compose(document: dict[str, Any]) -> None:
    services = document.get("services", {})
    require(
        document.get("name") == "wall-street-receipts-home",
        "The fixed production project/volume namespace changed",
    )
    require(
        set(services)
        == {"postgres", "api", "web", "caddy-production", "caddy-rehearsal"},
        "The deployment service set changed",
    )

    networks = document.get("networks", {})
    require(
        set(networks) == {"public-egress", "edge-internal", "app-internal", "db-internal"},
        "The deployment network set changed",
    )
    for name in ("edge-internal", "app-internal", "db-internal"):
        require(networks[name].get("internal") is True, f"{name} must remain internal")
    require(
        networks["public-egress"].get("internal") is not True,
        "public-egress must remain the Caddy-only host-facing network",
    )
    require(
        set(document.get("volumes", {})) == {"postgres-data", "caddy-data", "caddy-config"},
        "The named-volume allowlist changed",
    )
    require(set(document.get("secrets", {})) == {"postgres_password"}, "The secret allowlist changed")
    secret_file = document["secrets"]["postgres_password"].get("file", "")
    require(Path(secret_file).is_absolute(), "The PostgreSQL secret source must be an absolute file")

    expected_membership = {
        "postgres": {"db-internal"},
        "api": {"app-internal", "db-internal"},
        "web": {"edge-internal", "app-internal"},
        "caddy-production": {"public-egress", "edge-internal"},
        "caddy-rehearsal": {"public-egress", "edge-internal"},
    }
    for service_name, expected in expected_membership.items():
        require(
            service_networks(services[service_name]) == expected,
            f"{service_name} network membership changed",
        )

    expected_dependencies = {
        "postgres": {},
        "api": {"postgres": {"condition": "service_healthy", "restart": True, "required": True}},
        "web": {"api": {"condition": "service_healthy", "restart": True, "required": True}},
        "caddy-production": {"web": {"condition": "service_healthy", "restart": True, "required": True}},
        "caddy-rehearsal": {"web": {"condition": "service_healthy", "restart": True, "required": True}},
    }
    for service_name, expected in expected_dependencies.items():
        require(
            services[service_name].get("depends_on", {}) == expected,
            f"{service_name} health-gated dependency chain changed",
        )

    for service_name in ("postgres", "api", "web"):
        require(not services[service_name].get("ports"), f"{service_name} must publish no host port")

    production_ports = services["caddy-production"].get("ports", [])
    production_port_pairs = {
        (int(item["target"]), int(item["published"]), item.get("protocol", "tcp"))
        for item in production_ports
    }
    require(
        production_port_pairs == {(80, 80, "tcp"), (443, 443, "tcp")},
        "Production Caddy must publish only TCP 80 and 443",
    )
    require(
        all(item.get("host_ip") in (None, "0.0.0.0") for item in production_ports),
        "Production Caddy must not be loopback-only",
    )

    rehearsal_ports = services["caddy-rehearsal"].get("ports", [])
    require(len(rehearsal_ports) == 1, "Rehearsal Caddy must publish exactly one port")
    rehearsal_port = rehearsal_ports[0]
    require(
        int(rehearsal_port["target"]) == 8443
        and int(rehearsal_port["published"]) == 18080
        and rehearsal_port.get("host_ip") == "127.0.0.1"
        and rehearsal_port.get("protocol", "tcp") == "tcp",
        "Rehearsal Caddy must use one numeric-loopback HTTPS port",
    )
    require(services["caddy-production"].get("profiles") == ["production"], "Production profile changed")
    require(services["caddy-rehearsal"].get("profiles") == ["rehearsal"], "Rehearsal profile changed")

    expected_resources = {
        "postgres": {"pids_limit": 256, "mem_limit": "1073741824", "cpus": 1},
        "api": {"pids_limit": 256, "mem_limit": "1073741824", "cpus": 1},
        "web": {"pids_limit": 256, "mem_limit": "805306368", "cpus": 1},
        "caddy-production": {"pids_limit": 128, "mem_limit": "268435456", "cpus": 0.5},
        "caddy-rehearsal": {"pids_limit": 128, "mem_limit": "268435456", "cpus": 0.5},
    }
    for service_name, service in services.items():
        require(service.get("privileged") is not True, f"{service_name} must not be privileged")
        for forbidden_key in (
            "network_mode",
            "pid",
            "ipc",
            "uts",
            "userns_mode",
            "devices",
            "device_cgroup_rules",
            "links",
            "external_links",
            "extra_hosts",
            "dns",
        ):
            require(not service.get(forbidden_key), f"{service_name} may not set {forbidden_key}")
        mounts = service.get("volumes", [])
        require(
            all("docker.sock" not in json.dumps(mount) for mount in mounts),
            f"{service_name} must not mount the Docker socket",
        )
        require(service.get("restart") == "unless-stopped", f"{service_name} restart policy changed")
        require("healthcheck" in service, f"{service_name} healthcheck is required")
        actual_resources = {
            "pids_limit": service.get("pids_limit"),
            "mem_limit": str(service.get("mem_limit", "")),
            "cpus": service.get("cpus"),
        }
        require(
            actual_resources == expected_resources[service_name],
            f"{service_name} fixed resource envelope changed",
        )
        require(
            service.get("logging")
            == {"driver": "local", "options": {"max-file": "3", "max-size": "10m"}},
            f"{service_name} bounded logging changed",
        )

    for service_name in ("api", "web", "caddy-production", "caddy-rehearsal"):
        service = services[service_name]
        require(service.get("read_only") is True, f"{service_name} root filesystem must be read-only")
        require(
            "no-new-privileges:true" in service.get("security_opt", []),
            f"{service_name} must set no-new-privileges",
        )
        require("ALL" in service.get("cap_drop", []), f"{service_name} must drop all capabilities")

    require(services["caddy-production"].get("user") == "65532:65532", "Production Caddy must be non-root")
    require(services["caddy-rehearsal"].get("user") == "65532:65532", "Rehearsal Caddy must be non-root")
    for service_name in ("caddy-production", "caddy-rehearsal"):
        require(
            services[service_name].get("cap_add") == ["NET_BIND_SERVICE"],
            f"{service_name} may add only NET_BIND_SERVICE",
        )
    for service_name in ("api", "web"):
        require(not services[service_name].get("cap_add"), f"{service_name} may add no capability")
        require(services[service_name].get("init") is True, f"{service_name} must use an init process")

    expected_health_commands = {
        "postgres": ["CMD-SHELL", 'pg_isready -U "$${POSTGRES_USER}" -d "$${POSTGRES_DB}"'],
        "api": [
            "CMD", "curl", "--fail", "--silent", "--show-error", "--max-time", "3",
            "http://127.0.0.1:8080/actuator/health",
        ],
        "web": [
            "CMD", "node", "-e",
            "fetch('http://127.0.0.1:3000/').then((response) => process.exit(response.ok ? 0 : 1)).catch(() => process.exit(1))",
        ],
        "caddy-production": [
            "CMD", "curl", "--fail", "--silent", "--output", "/dev/null", "--max-time", "3",
            "http://127.0.0.1:2019/config/",
        ],
        "caddy-rehearsal": [
            "CMD", "curl", "--fail", "--silent", "--output", "/dev/null", "--max-time", "3",
            "http://127.0.0.1:2019/config/",
        ],
    }
    for service_name, command in expected_health_commands.items():
        require(
            services[service_name]["healthcheck"].get("test") == command,
            f"{service_name} health command changed",
        )

    expected_mounts = {
        "postgres": {("volume", "postgres-data", "/var/lib/postgresql/data", False)},
        "api": set(),
        "web": set(),
        "caddy-production": {
            ("bind", str((DEPLOY / "Caddyfile").resolve()), "/etc/caddy/Caddyfile", True),
            ("volume", "caddy-data", "/data", False),
            ("volume", "caddy-config", "/config", False),
        },
        "caddy-rehearsal": {
            ("bind", str((DEPLOY / "Caddyfile").resolve()), "/etc/caddy/Caddyfile", True),
        },
    }
    for service_name, expected in expected_mounts.items():
        actual = {
            (
                mount.get("type"),
                str(Path(mount.get("source", "")).resolve())
                if mount.get("type") == "bind"
                else mount.get("source"),
                mount.get("target"),
                mount.get("read_only") is True,
            )
            for mount in services[service_name].get("volumes", [])
        }
        require(actual == expected, f"{service_name} volume allowlist changed")

    expected_environments = {
        "postgres": {
            "POSTGRES_DB": "wsr",
            "POSTGRES_USER": "wsr",
            "POSTGRES_PASSWORD_FILE": "/run/secrets/postgres_password",
            "POSTGRES_INITDB_ARGS": "--data-checksums",
            "PGTZ": "UTC",
            "TZ": "UTC",
        },
        "api": {
        "APP_ENV": "production",
        "DATA_MODE": "DEMO",
        "POSTGRES_HOST": "postgres",
        "POSTGRES_PORT": "5432",
        "POSTGRES_DB": "wsr",
        "POSTGRES_USER": "wsr",
        "SPRING_CONFIG_LOCATION": "classpath:/",
        "SPRING_CONFIG_IMPORT": "configtree:/run/secrets/",
        "SERVER_ADDRESS": "0.0.0.0",
        "SERVER_PORT": "8080",
        "MARKET_PROVIDER": "fixture",
        "ANALYST_PROVIDER": "fixture",
        "SEC_PROVIDER_ENABLED": "false",
        "SEC_BASE_URL": "http://127.0.0.1:9",
        "SEC_CONTACT_EMAIL": "",
        "OPERATOR_API_ENABLED": "false",
        "OPERATOR_API_TOKEN_SHA256": "",
        "JAVA_TOOL_OPTIONS": "-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=UTC",
        "TZ": "UTC",
        },
        "web": {
        "NODE_ENV": "production",
        "NEXT_TELEMETRY_DISABLED": "1",
        "NEXT_PUBLIC_DATA_MODE": "DEMO",
        "CALL_AUDIT_PROVIDER": "api",
        "API_BASE_URL": "http://api:8080",
        "MARKET_PROVIDER": "fixture",
        "ANALYST_PROVIDER": "fixture",
        "SP500_HISTORY_PROVIDER": "fixture",
        "MARKET_BOARD_PROVIDER": "fixture",
        "METHODOLOGY_PROVIDER": "fixture",
        "INSTITUTION_DIRECTORY_PROVIDER": "fixture",
        "ANALYST_DIRECTORY_PROVIDER": "fixture",
        "MARKET_MAP_PROVIDER": "fixture",
        "MARKET_TREEMAP_PROVIDER": "fixture",
        "MACRO_PROVIDER": "fixture",
        "MEDIA_PROVIDER": "fixture",
        "HOSTNAME": "0.0.0.0",
        "PORT": "3000",
        "TZ": "UTC",
        },
        "caddy-production": {
            "WSR_DOMAIN": "wsr.invalid",
            "WSR_ACME_EMAIL": "operator@wsr.invalid",
            "WSR_DEFAULT_SNI": "wsr.invalid",
        },
        "caddy-rehearsal": {
            "WSR_DOMAIN": "https://127.0.0.1:8443",
            "WSR_ACME_EMAIL": "operator@wsr.invalid",
            "WSR_DEFAULT_SNI": "127.0.0.1",
        },
    }
    for service_name, expected in expected_environments.items():
        actual = environment_map(services[service_name])
        require(actual == expected, f"{service_name} environment allowlist changed")
        if service_name == "web":
            require(
                {key for key in actual if key.startswith("NEXT_PUBLIC_")}
                == {"NEXT_PUBLIC_DATA_MODE"},
                "An unapproved public Next variable reached the web runtime",
            )

    all_environment = {
        service_name: environment_map(service)
        for service_name, service in services.items()
    }
    for service_name, values in all_environment.items():
        require(
            not any("PASSWORD" in key and not key.endswith("_FILE") for key in values),
            f"{service_name} exposes a password through environment configuration",
        )

    postgres_secrets = services["postgres"].get("secrets", [])
    api_secrets = services["api"].get("secrets", [])
    require(
        any(item.get("target") == "postgres_password" for item in postgres_secrets),
        "PostgreSQL must receive its password as a file secret",
    )
    require(
        any(item.get("target") == "spring.datasource.password" for item in api_secrets),
        "Spring must receive its password through the configuration-tree secret",
    )
    require(not services["web"].get("secrets"), "Web must receive no database secret")
    require(not services["caddy-production"].get("secrets"), "Caddy must receive no database secret")

    require(services["postgres"].get("image") == "postgres:17-alpine", "PostgreSQL major pin changed")
    require(
        services["postgres"].get("labels")
        == {
            "com.wallstreetreceipts.release-sha": "0123456789abcdef",
            "com.wallstreetreceipts.role": "production-primary-database",
        },
        "PostgreSQL production identity labels changed",
    )
    require(
        services["api"].get("image") == "wall-street-receipts-api:0123456789abcdef",
        "API release tag interpolation changed",
    )
    require(
        services["web"].get("image") == "wall-street-receipts-web:0123456789abcdef",
        "Web release tag interpolation changed",
    )
    for service_name in ("caddy-production", "caddy-rehearsal"):
        require(
            services[service_name].get("image")
            == "wall-street-receipts-caddy:0123456789abcdef",
            "Caddy release tag interpolation changed",
        )

    expected_builds = {
        "api": (ROOT, "deploy/home-server/api.Dockerfile"),
        "web": (ROOT, "deploy/home-server/web.Dockerfile"),
        "caddy-production": (DEPLOY, "caddy.Dockerfile"),
        "caddy-rehearsal": (DEPLOY, "caddy.Dockerfile"),
    }
    require(not services["postgres"].get("build"), "PostgreSQL must remain an upstream image")
    for service_name, (expected_context, expected_dockerfile) in expected_builds.items():
        build = services[service_name].get("build", {})
        require(
            Path(build.get("context", "")).resolve() == expected_context.resolve(),
            f"{service_name} build context changed",
        )
        require(build.get("dockerfile") == expected_dockerfile, f"{service_name} Dockerfile changed")
        require(build.get("target") == "runtime", f"{service_name} runtime target changed")
        require(
            build.get("args") == {"WSR_GIT_SHA": "0123456789abcdef"},
            f"{service_name} build provenance arguments changed",
        )


def expect_rejected(document: dict[str, Any], mutate: Any, label: str) -> None:
    candidate = copy.deepcopy(document)
    mutate(candidate)
    try:
        validate_compose(candidate)
    except ContractError:
        return
    raise ContractError(f"Negative self-test was accepted: {label}")


def validate_negative_matrix(document: dict[str, Any]) -> None:
    expect_rejected(
        document,
        lambda value: value["services"]["api"].__setitem__(
            "ports", [{"target": 8080, "published": "8080"}]
        ),
        "public API port",
    )
    expect_rejected(
        document,
        lambda value: value["networks"]["app-internal"].__setitem__("internal", False),
        "backend egress",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["caddy-production"]["networks"].__setitem__(
            "app-internal", None
        ),
        "Caddy-to-API network",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["api"]["environment"].__setitem__(
            "SEC_PROVIDER_ENABLED", "true"
        ),
        "live SEC enablement",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["postgres"]["environment"].__setitem__(
            "POSTGRES_PASSWORD", "leak"
        ),
        "password environment leak",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["caddy-rehearsal"]["ports"][0].__setitem__(
            "host_ip", "0.0.0.0"
        ),
        "public rehearsal listener",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["web"]["environment"].__setitem__(
            "NEXT_PUBLIC_API_BASE_URL", "http://api:8080"
        ),
        "public API origin",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["api"]["environment"].__setitem__(
            "SPRING_APPLICATION_JSON", "{}"
        ),
        "unapproved Spring override",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["api"].__setitem__("network_mode", "host"),
        "host network mode",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["web"].__setitem__("privileged", True),
        "privileged web",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["caddy-production"]["healthcheck"].__setitem__(
            "test", ["CMD", "true"]
        ),
        "false-positive Caddy healthcheck",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["postgres"].pop("mem_limit"),
        "missing database memory limit",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["postgres"]["labels"].__setitem__(
            "com.wallstreetreceipts.role", "rehearsal-database"
        ),
        "ambiguous production database identity",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["web"]["depends_on"]["api"].__setitem__(
            "condition", "service_started"
        ),
        "ungated API dependency",
    )
    expect_rejected(
        document,
        lambda value: value["services"]["caddy-production"]["volumes"].append(
            {"type": "bind", "source": "/", "target": "/host"}
        ),
        "arbitrary host bind mount",
    )


def validate_text_surfaces() -> None:
    required_paths = {
        "compose.yaml",
        "Caddyfile",
        ".env.example",
        "preflight.sh",
        "compose-production.sh",
        "api.Dockerfile",
        "api.Dockerfile.dockerignore",
        "web.Dockerfile",
        "web.Dockerfile.dockerignore",
        "caddy.Dockerfile",
        "caddy.Dockerfile.dockerignore",
        "README.md",
    }
    require(
        required_paths.issubset({path.name for path in DEPLOY.iterdir()}),
        "The documented ADR-046 deployment surface is incomplete",
    )

    expected_final_users = {
        "api.Dockerfile": "10001:10001",
        "web.Dockerfile": "node",
        "caddy.Dockerfile": "65532:65532",
    }
    for dockerfile_name, expected_user in expected_final_users.items():
        dockerfile = (DEPLOY / dockerfile_name).read_text(encoding="utf-8")
        require(
            re.search(r"(?mi)^\s*ADD\s", dockerfile) is None
            and re.search(r"(?mi)^\s*COPY(?:\s+--\S+)*\s+(?:\.|\[\s*\"\.\")", dockerfile) is None,
            f"{dockerfile_name} has a broad copy or ADD",
        )
        require(":latest" not in dockerfile, f"{dockerfile_name} uses latest")
        require(re.search(r"(?m)^FROM .+ AS runtime$", dockerfile) is not None, f"{dockerfile_name} runtime stage is missing")
        users = re.findall(r"(?m)^USER\s+([^\s#]+)", dockerfile)
        require(users and users[-1] == expected_user, f"{dockerfile_name} final USER changed")

    caddy_dockerfile = (DEPLOY / "caddy.Dockerfile").read_text(encoding="utf-8")
    require(
        "FROM caddy:2.11.4-alpine AS runtime" in caddy_dockerfile,
        "The upstream Caddy patch pin changed",
    )
    require(
        "chown 65532:65532 /data /config" in caddy_dockerfile
        and "chmod 0700 /data /config" in caddy_dockerfile,
        "Caddy named-volume ownership initialization changed",
    )

    require(
        (DEPLOY / "api.Dockerfile.dockerignore").read_text(encoding="utf-8").startswith("**\n"),
        "API build context must start deny-all",
    )
    require(
        (DEPLOY / "web.Dockerfile.dockerignore").read_text(encoding="utf-8").startswith("**\n"),
        "Web build context must start deny-all",
    )
    require(
        (DEPLOY / "caddy.Dockerfile.dockerignore").read_text(encoding="utf-8")
        == "**\n!caddy.Dockerfile\n",
        "Caddy build context must contain only its Dockerfile",
    )
    web_ignore = (DEPLOY / "web.Dockerfile.dockerignore").read_text(encoding="utf-8").splitlines()
    web_allow_index = web_ignore.index("!apps/web/**")
    for protected_path in (
        "apps/web/.env",
        "apps/web/.env.*",
        "apps/web/next-env.d.ts",
        "apps/web/node_modules/",
        "apps/web/.next/",
    ):
        require(
            protected_path in web_ignore and web_ignore.index(protected_path) > web_allow_index,
            f"Web build context no longer re-excludes {protected_path}",
        )

    production_caddy = (DEPLOY / "Caddyfile").read_text(encoding="utf-8")
    require("reverse_proxy web:3000" in production_caddy, "Production Caddy upstream changed")
    require("api:8080" not in production_caddy and "actuator" not in production_caddy, "Caddy exposes Spring")
    require("{$WSR_DOMAIN}" in production_caddy and "tls internal" not in production_caddy, "Production TLS boundary changed")
    for marker in (
        "default_sni {$WSR_DEFAULT_SNI}",
        "admin localhost:2019",
        "@unsupported not method GET HEAD POST",
        'respond @unsupported "Method Not Allowed" 405',
        "max_size 1MB",
        'X-Content-Type-Options "nosniff"',
        'X-Frame-Options "DENY"',
        'Referrer-Policy "strict-origin-when-cross-origin"',
        'Permissions-Policy "camera=(), microphone=(), geolocation=()"',
        "read_body 15s",
        "read_header 10s",
        "health_uri /",
    ):
        require(marker in production_caddy, f"Caddy policy marker changed: {marker}")

    preflight = (DEPLOY / "preflight.sh").read_text(encoding="utf-8")
    wrapper = (DEPLOY / "compose-production.sh").read_text(encoding="utf-8")
    for forbidden in ("apt install", "apt-get", "sudo ", "systemctl ", "ufw ", "iptables ", "nft "):
        require(forbidden not in preflight, f"Preflight became mutating: {forbidden}")
    require("docker compose up" not in preflight and "docker compose down" not in preflight, "Preflight starts containers")
    require("PENDING_EXTERNAL_INGRESS" in preflight, "Preflight overclaims public ingress")
    require(
        'git_command=(git -c "safe.directory=$repo_root" -C "$repo_root")' in preflight
        and preflight.count('"${git_command[@]}"') == 3,
        "Root production checks must use one exact per-command Git safe.directory boundary",
    )
    require(
        "git config --global" not in preflight and "safe.directory=*" not in preflight,
        "Preflight must not broaden persistent Git trust",
    )
    for marker in (
        "Docker Compose 2.20.0 or newer is required",
        "WSR_IMAGE_TAG must exactly equal the checked-out 40-character Git HEAD",
        "The deployment checkout has tracked or untracked changes",
        "Inherited WSR_* and COMPOSE_* variables are forbidden",
        "Rootless or daemon-level user-namespace remapping is outside",
        "mode 400 and owner 10001:10001",
        "secret directory must have traversal-only mode 711 and owner root:root",
        "fixed /etc/wall-street-receipts parent must have traversal-only mode 711",
        "DNS must exactly match the configured public address",
        "refusing to run production Compose against a remote Docker endpoint",
    ):
        source = preflight + wrapper
        require(marker in source, f"Publication guard marker changed: {marker}")
    require(
        'bash "$script_dir/preflight.sh"' in wrapper and "--mode contract" in wrapper,
        "Production Compose must revalidate the exact contract immediately before execution",
    )
    for exact_action in (
        "compose_arguments=(build --pull api web caddy-production)",
        "compose_arguments=(up --detach --wait)",
        "compose_arguments=(ps)",
        "compose_arguments=(logs --tail 200)",
        "compose_arguments=(stop --timeout 30)",
        "compose_arguments=(down --timeout 30)",
    ):
        require(exact_action in wrapper, f"Production action allowlist changed: {exact_action}")
    require(
        '"${compose_arguments[@]}"' in wrapper
        and '"$@"' not in wrapper
        and "arbitrary Compose arguments are forbidden" in wrapper,
        "Production wrapper regained arbitrary Compose argument forwarding",
    )
    require(
        "BUILDKIT_*|BUILDX_*" in wrapper,
        "Production builds no longer clear inherited BuildKit/Buildx overrides",
    )

    env_example = (DEPLOY / ".env.example").read_text(encoding="utf-8")
    require(
        "WSR_PUBLIC_IPV4=unknown" in env_example and "WSR_PUBLIC_IPV6=unknown" in env_example,
        "Public address attestations disappeared from the env template",
    )
    compose_source = COMPOSE.read_text(encoding="utf-8")
    for forbidden_resource_override in (
        "WSR_POSTGRES_MEMORY_LIMIT",
        "WSR_POSTGRES_CPU_LIMIT",
        "WSR_API_MEMORY_LIMIT",
        "WSR_API_CPU_LIMIT",
        "WSR_WEB_MEMORY_LIMIT",
        "WSR_WEB_CPU_LIMIT",
        "WSR_CADDY_MEMORY_LIMIT",
        "WSR_CADDY_CPU_LIMIT",
    ):
        require(
            forbidden_resource_override not in compose_source
            and forbidden_resource_override not in env_example
            and forbidden_resource_override not in preflight,
            f"Unbounded resource override returned: {forbidden_resource_override}",
        )
    playwright_config = (ROOT / "apps" / "web" / "playwright.config.ts").read_text(
        encoding="utf-8"
    )
    require(
        "PLAYWRIGHT_LOCAL_PRODUCTION_HTTPS" in playwright_config
        and "ignoreHTTPSErrors: true" in playwright_config,
        "The loopback production-TLS browser gate disappeared",
    )

    required_docs = {
        ROOT / "README.md": "verify-home-server-deployment.ps1",
        ROOT / "decisions" / "ADR-046-ubuntu-home-server-deployment-foundation.md": "ADR-046",
        ROOT / "IMPLEMENTATION_LOG.md": "ADR-046",
        DEPLOY / "README.md": "compose-production.sh",
    }
    for path, marker in required_docs.items():
        require(path.is_file(), f"Required ADR-046 documentation is missing: {path.name}")
        require(marker in path.read_text(encoding="utf-8"), f"{path.name} lost its ADR-046 command or marker")


def main() -> int:
    try:
        validate_text_surfaces()
        document = render_compose()
        validate_compose(document)
        validate_negative_matrix(document)
    except (ContractError, FileNotFoundError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print("PASS: ADR-046 home-server topology, secrets, profiles, and negative matrix are exact.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
