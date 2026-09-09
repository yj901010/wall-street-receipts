package com.wallstreetreceipts.api.web.operator;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CpiBrowserProcessTest {
    @TempDir Path root;
    @Test void doesNotInheritProviderOrDatabaseSecretsOrNodeInjection() {
        var env = CpiBrowserProcess.cleanEnvironment(Map.of("PATH", "runtime", "BLS_REGISTRATION_KEY", "hidden", "POSTGRES_PASSWORD", "hidden",
                "NODE_OPTIONS", "--require arbitrary", "API_BASE_URL", "https://outside", "OPERATOR_API_TOKEN_SHA256", "hidden"));
        assertThat(env).containsExactlyInAnyOrderEntriesOf(Map.of("PATH", "runtime", "NEXT_TELEMETRY_DISABLED", "1"));
    }
    @Test void refusesMissingAndOutsideMirrors() throws Exception {
        Files.createDirectory(root.resolve(".cache"));
        assertThatThrownBy(() -> CpiBrowserProcess.verifyMirror(root, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CpiBrowserProcess.verifyMirror(root, root.toString())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsOnlyMatchingSourceAndFixtures() throws Exception {
        Path web = mirror();
        assertThat(CpiBrowserProcess.verifyMirror(root, web.toString())).isEqualTo(web.toRealPath());
    }
    @Test void rejectsExtraSourceInMirror() throws Exception {
        Path web = mirror();
        Files.writeString(web.resolve("src/unexpected.ts"), "extra");
        assertThatThrownBy(() -> CpiBrowserProcess.verifyMirror(root, web.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inventory mismatch");
    }
    @Test void rejectsStaleFixturesAndConfiguration() throws Exception {
        Path web = mirror();
        Path fixture = web.getParent().getParent().resolve("fixtures/demo.json");
        Files.writeString(fixture, "stale");
        assertThatThrownBy(() -> CpiBrowserProcess.verifyMirror(root, web.toString())).isInstanceOf(IllegalArgumentException.class);
        Files.writeString(fixture, "{}");
        Files.writeString(web.resolve("next.config.ts"), "stale");
        assertThatThrownBy(() -> CpiBrowserProcess.verifyMirror(root, web.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("configuration mismatch");
    }
    @Test void rejectsEnvironmentFilesInEveryBuildAncestor() throws Exception {
        Path web = mirror();
        for (Path directory : List.of(web, web.getParent(), web.getParent().getParent())) {
            Path environment = directory.resolve(".env.local");
            Files.writeString(environment, "SYNTHETIC_ONLY=never-load");
            assertThatThrownBy(() -> CpiBrowserProcess.verifyMirror(root, web.toString()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("environment files");
            Files.delete(environment);
        }
    }
    private Path mirror() throws Exception {
        Path web = root.resolve(".cache/mirror/apps/web");
        for (Path base : List.of(root, root.resolve(".cache/mirror"))) {
            for (String tree : List.of("src", "operator", "public")) {
                Path directory = Files.createDirectories(base.resolve("apps/web").resolve(tree));
                Files.writeString(directory.resolve("example.ts"), "// DEMO");
            }
            for (String file : List.of("package.json", "next.config.ts", "tsconfig.json")) {
                Files.writeString(base.resolve("apps/web").resolve(file), "{}");
            }
            Files.createDirectories(base.resolve("fixtures"));
            Files.writeString(base.resolve("fixtures/demo.json"), "{}");
        }
        return web;
    }
}
