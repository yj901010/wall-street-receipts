package com.wallstreetreceipts.api.web.operator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

/** Test process ownership: current source only, no ambient provider/DB credentials or shell commands. */
final class CpiBrowserProcess {
    static Path verifyMirror(Path root, String requested) throws Exception {
        if (requested == null) throw new IllegalArgumentException("Provide a secret-free source mirror under repository .cache");
        Path web = Path.of(requested).toRealPath();
        Path cache = root.resolve(".cache").toRealPath();
        if (!web.startsWith(cache) || !web.endsWith(Path.of("apps", "web"))) throw new IllegalArgumentException("Mirror must be an owned .cache apps/web directory");
        for (String tree : List.of("src", "operator", "public")) verifyTree(root.resolve("apps/web").resolve(tree), web.resolve(tree));
        verifyTree(root.resolve("fixtures"), web.getParent().getParent().resolve("fixtures"));
        for (String file : List.of("package.json", "next.config.ts", "tsconfig.json")) {
            if (Files.mismatch(root.resolve("apps/web").resolve(file), web.resolve(file)) != -1) throw new IllegalArgumentException("Mirror build configuration mismatch");
        }
        for (Path directory : List.of(web, web.getParent(), web.getParent().getParent())) {
            try (var entries = Files.list(directory)) {
                if (entries.anyMatch(path -> path.getFileName().toString().startsWith(".env"))) throw new IllegalArgumentException("Mirror must not contain environment files");
            }
        }
        return web;
    }
    private static void verifyTree(Path source, Path target) throws Exception {
        try (var sources = Files.walk(source); var targets = Files.walk(target)) {
            var expected = sources.map(source::relativize).collect(java.util.stream.Collectors.toSet());
            var actual = targets.map(target::relativize).collect(java.util.stream.Collectors.toSet());
            if (!expected.equals(actual)) throw new IllegalArgumentException("Mirror tree inventory mismatch");
            for (Path relative : expected) {
                Path original = source.resolve(relative), copy = target.resolve(relative);
                if (Files.isSymbolicLink(original) || Files.isSymbolicLink(copy)
                        || !copy.toRealPath().startsWith(target.toRealPath())
                        || Files.isDirectory(original) != Files.isDirectory(copy)
                        || (Files.isRegularFile(original) && Files.mismatch(original, copy) != -1)) {
                    throw new IllegalArgumentException("Mirror source mismatch: " + relative);
                }
            }
        }
    }
    static Map<String, String> cleanEnvironment(Map<String, String> source) {
        var result = new java.util.HashMap<String, String>();
        source.forEach((key, value) -> {
            if (key.matches("(?i)PATH|SYSTEMROOT|WINDIR|TEMP|TMP|COMSPEC|PATHEXT|HOME|USERPROFILE|LOCALAPPDATA|APPDATA")) result.put(key, value);
        });
        result.put("NEXT_TELEMETRY_DISABLED", "1");
        return result;
    }
    static void run(String node, Path web, Path log, List<String> arguments, Map<String, String> extra, int seconds) throws Exception {
        var command = new ArrayList<String>(); command.add(node); command.addAll(arguments);
        var builder = new ProcessBuilder(command).directory(web.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().clear(); builder.environment().putAll(cleanEnvironment(System.getenv())); builder.environment().putAll(extra);
        Process child = builder.start();
        var owned = new LinkedHashSet<ProcessHandle>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        try {
            do {
                child.descendants().forEach(owned::add);
                if (Files.size(log) > 4_000_000) throw new IllegalStateException("CPI rehearsal log size exceeded");
                if (System.nanoTime() >= deadline) throw new IllegalStateException("Bounded CPI rehearsal process timed out");
            } while (!child.waitFor(100, TimeUnit.MILLISECONDS));
            if (Files.size(log) > 4_000_000 || child.exitValue() != 0) throw new IllegalStateException("CPI rehearsal process failed; inspect owned log " + log);
        } finally {
            // Only descendants of the exact child launched above; never find/kill processes by name or port.
            child.descendants().forEach(owned::add);
            for (var descendant : new ArrayList<>(owned).reversed()) if (descendant.isAlive()) descendant.destroyForcibly();
            if (child.isAlive()) child.destroyForcibly();
            child.waitFor(10, TimeUnit.SECONDS);
        }
    }
    private CpiBrowserProcess() {}
}
