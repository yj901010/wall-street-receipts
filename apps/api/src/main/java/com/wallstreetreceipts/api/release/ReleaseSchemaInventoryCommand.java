package com.wallstreetreceipts.api.release;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.ResourceNameParser;
import org.flywaydb.core.internal.resource.StringResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Emits a fail-closed, machine-readable inventory of the SQL migrations that are
 * actually packaged in the API runtime class path.
 */
public final class ReleaseSchemaInventoryCommand {

    public static final String OPTION = "--wsr-release-schema-inventory";

    private static final String FLYWAY_VERSION_RESOURCE = "/org/flywaydb/core/internal/version.txt";
    private static final String EXPECTED_FLYWAY_VERSION = "11.7.2";
    private static final String MIGRATION_DIRECTORY = "db/migration/";
    private static final String DIRECT_RESOURCE_PATTERN = "classpath*:db/migration/*";
    private static final String RECURSIVE_RESOURCE_PATTERN = "classpath*:db/migration/**/*";
    private static final Pattern CANONICAL_FILENAME = Pattern.compile(
            "^V([1-9][0-9]*)__([a-z0-9]+(?:_[a-z0-9]+)*)\\.sql$");
    private static final Pattern CANONICAL_FLYWAY_VERSION = Pattern.compile("^[1-9][0-9]*\\.[0-9]+\\.[0-9]+$");
    private static final int MAX_MIGRATION_COUNT = 256;
    private static final int MAX_MIGRATION_BYTES = 16 * 1024 * 1024;
    private static final long MAX_TOTAL_MIGRATION_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_FILENAME_BYTES = 200;
    private static final int EX_USAGE = 64;
    private static final int EX_SOFTWARE = 70;
    private static final HexFormat HEX = HexFormat.of();

    private ReleaseSchemaInventoryCommand() {
    }

    /**
     * Runs the inventory command when its reserved argument is present. An empty
     * result means normal Spring Boot startup should continue.
     */
    public static OptionalInt runIfRequested(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");

        boolean reservedArgumentPresent = Arrays.stream(args)
                .filter(Objects::nonNull)
                .anyMatch(argument -> argument.startsWith(OPTION));
        if (!reservedArgumentPresent) {
            return OptionalInt.empty();
        }

        if (args.length != 1 || !OPTION.equals(args[0])) {
            writeCanonicalLine(err, "release-schema-inventory|error|invalid_arguments");
            return OptionalInt.of(EX_USAGE);
        }

        try {
            List<String> lines = inventoryLines(Thread.currentThread().getContextClassLoader());
            lines.forEach(line -> writeCanonicalLine(out, line));
            if (out.checkError()) {
                writeCanonicalLine(err, "release-schema-inventory|error|output_failed");
                return OptionalInt.of(EX_SOFTWARE);
            }
            return OptionalInt.of(0);
        } catch (ReleaseSchemaInventoryException exception) {
            writeCanonicalLine(err, "release-schema-inventory|error|" + exception.reason());
            return OptionalInt.of(EX_SOFTWARE);
        } catch (RuntimeException exception) {
            writeCanonicalLine(err, "release-schema-inventory|error|unexpected_failure");
            return OptionalInt.of(EX_SOFTWARE);
        }
    }

    static List<String> inventoryLines(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");

        String flywayVersion = readFlywayVersion();
        List<MigrationResource> migrations = discoverMigrations(classLoader);

        List<String> lines = new ArrayList<>(migrations.size() + 2);
        lines.add("inventory_version|1");
        lines.add("flyway_version|" + flywayVersion);

        int rank = 1;
        for (MigrationResource migration : migrations) {
            lines.add(String.join("|",
                    "migration",
                    Integer.toString(rank),
                    migration.version().toString(),
                    utf8Hex(migration.description()),
                    "SQL",
                    utf8Hex(migration.filename()),
                    Integer.toString(migration.checksum()),
                    migration.filename(),
                    migration.rawSha256(),
                    Integer.toString(migration.bytes())));
            rank++;
        }
        return List.copyOf(lines);
    }

    private static String readFlywayVersion() {
        try (InputStream input = ChecksumCalculator.class.getResourceAsStream(FLYWAY_VERSION_RESOURCE)) {
            if (input == null) {
                throw failure("flyway_version_missing");
            }
            byte[] raw = readBounded(input, 64, "flyway_version_too_large");
            rejectUtf8Bom(raw, "flyway_version_bom");
            String actual = decodeUtf8(raw, "flyway_version_invalid_utf8").strip();
            if (!CANONICAL_FLYWAY_VERSION.matcher(actual).matches()) {
                throw failure("flyway_version_invalid");
            }
            if (!EXPECTED_FLYWAY_VERSION.equals(actual)) {
                throw failure("flyway_version_unsupported");
            }
            return actual;
        } catch (IOException exception) {
            throw failure("flyway_version_unreadable", exception);
        }
    }

    private static List<MigrationResource> discoverMigrations(ClassLoader classLoader) {
        var resolver = new PathMatchingResourcePatternResolver(classLoader);
        Set<String> seenOrigins = new LinkedHashSet<>();
        Map<String, MigrationResource> byFilename = new HashMap<>();
        long totalBytes = 0;

        for (String resourcePattern : List.of(DIRECT_RESOURCE_PATTERN, RECURSIVE_RESOURCE_PATTERN)) {
            Resource[] resources;
            try {
                resources = resolver.getResources(resourcePattern);
            } catch (IOException exception) {
                throw failure("migration_discovery_failed", exception);
            }

            for (Resource resource : resources) {
                String origin = resourceOrigin(resource);
                if (!seenOrigins.add(origin)) {
                    continue;
                }

                String relativePath = migrationRelativePath(origin);
                if (relativePath.isEmpty() || relativePath.endsWith("/") || relativePath.contains("/")) {
                    throw failure("nested_or_directory_resource");
                }

                Matcher filenameMatcher = CANONICAL_FILENAME.matcher(relativePath);
                if (!filenameMatcher.matches()
                        || relativePath.getBytes(StandardCharsets.UTF_8).length > MAX_FILENAME_BYTES) {
                    throw failure("unsupported_migration_filename");
                }
                if (byFilename.containsKey(relativePath)) {
                    throw failure("duplicate_migration_filename");
                }
                if (byFilename.size() >= MAX_MIGRATION_COUNT) {
                    throw failure("too_many_migrations");
                }

                byte[] raw = readMigration(resource);
                if (raw.length == 0) {
                    throw failure("migration_empty");
                }
                totalBytes += raw.length;
                if (totalBytes > MAX_TOTAL_MIGRATION_BYTES) {
                    throw failure("migration_inventory_too_large");
                }
                rejectUtf8Bom(raw, "migration_bom");
                String sql = decodeUtf8(raw, "migration_invalid_utf8");

                BigInteger version = new BigInteger(filenameMatcher.group(1));
                var parsedName = new ResourceNameParser(new ClassicConfiguration(classLoader)).parse(relativePath);
                if (!parsedName.isValid()
                        || !"V".equals(parsedName.getPrefix())
                        || !".sql".equals(parsedName.getSuffix())
                        || parsedName.getVersion() == null
                        || !version.toString().equals(parsedName.getVersion().getVersion())
                        || !relativePath.equals(parsedName.getFilename())) {
                    throw failure("flyway_filename_disagreement");
                }

                int checksum = ChecksumCalculator.calculate(new StringResource(sql));
                MigrationResource migration = new MigrationResource(
                        version,
                        parsedName.getDescription(),
                        relativePath,
                        checksum,
                        sha256(raw),
                        raw.length);
                byFilename.put(relativePath, migration);
            }
        }

        if (byFilename.isEmpty()) {
            throw failure("no_migrations_found");
        }

        List<MigrationResource> migrations = new ArrayList<>(byFilename.values());
        migrations.sort(Comparator.comparing(MigrationResource::version));
        BigInteger expectedVersion = BigInteger.ONE;
        for (MigrationResource migration : migrations) {
            if (!expectedVersion.equals(migration.version())) {
                throw failure("migration_version_gap_or_duplicate");
            }
            expectedVersion = expectedVersion.add(BigInteger.ONE);
        }
        return List.copyOf(migrations);
    }

    private static String resourceOrigin(Resource resource) {
        try {
            return resource.getURL().toExternalForm().replace('\\', '/');
        } catch (IOException exception) {
            throw failure("migration_resource_url_unreadable", exception);
        }
    }

    private static String migrationRelativePath(String origin) {
        int marker = origin.lastIndexOf(MIGRATION_DIRECTORY);
        if (marker < 0) {
            throw failure("migration_resource_path_invalid");
        }
        String relativePath = origin.substring(marker + MIGRATION_DIRECTORY.length());
        if (relativePath.indexOf('?') >= 0
                || relativePath.indexOf('#') >= 0
                || relativePath.indexOf('!') >= 0
                || relativePath.indexOf('%') >= 0
                || relativePath.indexOf('\\') >= 0) {
            throw failure("migration_resource_path_invalid");
        }
        return relativePath;
    }

    private static byte[] readMigration(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            return readBounded(input, MAX_MIGRATION_BYTES, "migration_too_large");
        } catch (IOException exception) {
            throw failure("migration_unreadable", exception);
        }
    }

    private static byte[] readBounded(InputStream input, int maximumBytes, String overflowReason) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximumBytes) {
                throw failure(overflowReason);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void rejectUtf8Bom(byte[] raw, String reason) {
        if (raw.length >= 3
                && (raw[0] & 0xff) == 0xef
                && (raw[1] & 0xff) == 0xbb
                && (raw[2] & 0xff) == 0xbf) {
            throw failure(reason);
        }
    }

    private static String decodeUtf8(byte[] raw, String reason) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw failure(reason, exception);
        }
    }

    private static String utf8Hex(String value) {
        return HEX.formatHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeCanonicalLine(PrintStream stream, String value) {
        stream.print(value);
        stream.print('\n');
    }

    private static String sha256(byte[] raw) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    private static ReleaseSchemaInventoryException failure(String reason) {
        return new ReleaseSchemaInventoryException(reason, null);
    }

    private static ReleaseSchemaInventoryException failure(String reason, Throwable cause) {
        return new ReleaseSchemaInventoryException(reason, cause);
    }

    private record MigrationResource(
            BigInteger version,
            String description,
            String filename,
            int checksum,
            String rawSha256,
            int bytes) {
    }

    private static final class ReleaseSchemaInventoryException extends RuntimeException {

        private final String reason;

        private ReleaseSchemaInventoryException(String reason, Throwable cause) {
            super(reason, cause);
            this.reason = reason;
        }

        private String reason() {
            return reason;
        }
    }
}
