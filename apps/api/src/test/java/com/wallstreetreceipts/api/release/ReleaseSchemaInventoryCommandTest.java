package com.wallstreetreceipts.api.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseSchemaInventoryCommandTest {

    private static final List<String> EXPECTED_PACKAGED_INVENTORY = List.of(
            "inventory_version|1",
            "flyway_version|11.7.2",
            "migration|1|1|626173656c696e65|SQL|56315f5f626173656c696e652e73716c|-1220488027|V1__baseline.sql|ca61840c9da7b493fda16ab5c694f674747858fbcaf9c29f1217ce17d16a0ed1|292",
            "migration|2|2|616e616c7973742063616c6c73|SQL|56325f5f616e616c7973745f63616c6c732e73716c|881870446|V2__analyst_calls.sql|9fc639eb46f28fc7c5067efe557ff234fb838fd6fe6f0b7a32138350a0499e93|6691",
            "migration|3|3|616e616c7973742063616c6c207265766973696f6e73|SQL|56335f5f616e616c7973745f63616c6c5f7265766973696f6e732e73716c|2022864324|V3__analyst_call_revisions.sql|ac9e979af5641eb9d015df2e75373559decfb2c2dc28a41e7cd68b989ad00a07|6177",
            "migration|4|4|63616c6c206f7574636f6d6573|SQL|56345f5f63616c6c5f6f7574636f6d65732e73716c|985604649|V4__call_outcomes.sql|694b90e980b03d553f9cebf50e29544ee747d753699265d6be8feae549b34b6c|9432",
            "migration|5|5|63616c6c20636f6e7465787473|SQL|56355f5f63616c6c5f636f6e74657874732e73716c|-1972073|V5__call_contexts.sql|5795d431b09635dda6a027379672df5dfc235a045fdbdfdf1e1aab08e5878c6b|8716",
            "migration|6|6|7365632066696c696e6720636174616c6f67206361707475726573|SQL|56365f5f7365635f66696c696e675f636174616c6f675f63617074757265732e73716c|-132924805|V6__sec_filing_catalog_captures.sql|d5393187bac4126f08d04a2a408bb4f8aafbef193deec16f0cd680e8ef5ad695|7253",
            "migration|7|7|73656320686973746f726963616c2066696c696e67207365676d656e74206361707475726573|SQL|56375f5f7365635f686973746f726963616c5f66696c696e675f7365676d656e745f63617074757265732e73716c|1901422043|V7__sec_historical_filing_segment_captures.sql|f5e9819c6b5c478216af1a103de90949ca79bdec20bdce722fb4ed2f02287a11|8494",
            "migration|8|8|7365632066696c696e6720686973746f727920636f6c6c656374696f6e206d616e696665737473|SQL|56385f5f7365635f66696c696e675f686973746f72795f636f6c6c656374696f6e5f6d616e6966657374732e73716c|-1360846600|V8__sec_filing_history_collection_manifests.sql|102da8bf341db5158ce2681f16083bcec69cb4b83f67d71b2508633bbb92a934|15000",
            "migration|9|9|7365632066696c696e6720636f6c6c656374696f6e20617474656d707473|SQL|56395f5f7365635f66696c696e675f636f6c6c656374696f6e5f617474656d7074732e73716c|-1286423454|V9__sec_filing_collection_attempts.sql|0e325a398aa14eebdd1d3be6eb76b968d09090c694b5505d89c238ea726a25ad|20142");

    @Test
    void emitsTheExactPackagedV1ThroughV9Inventory() {
        assertThat(ReleaseSchemaInventoryCommand.inventoryLines(getClass().getClassLoader()))
                .containsExactlyElementsOf(EXPECTED_PACKAGED_INVENTORY);
    }

    @Test
    void exactReservedArgumentRunsWithoutStartingTheApplication() {
        var stdoutBytes = new ByteArrayOutputStream();
        var stderrBytes = new ByteArrayOutputStream();

        var exitCode = ReleaseSchemaInventoryCommand.runIfRequested(
                new String[] { ReleaseSchemaInventoryCommand.OPTION },
                new PrintStream(stdoutBytes, true, StandardCharsets.UTF_8),
                new PrintStream(stderrBytes, true, StandardCharsets.UTF_8));

        assertThat(exitCode).hasValue(0);
        assertThat(stderrBytes.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(stdoutBytes.toString(StandardCharsets.UTF_8)).doesNotContain("\r");
        assertThat(stdoutBytes.toString(StandardCharsets.UTF_8).lines().toList())
                .containsExactlyElementsOf(EXPECTED_PACKAGED_INVENTORY);
    }

    @Test
    void malformedReservedArgumentFailsWithUsageExitCodeAndNoInventory() {
        var stdoutBytes = new ByteArrayOutputStream();
        var stderrBytes = new ByteArrayOutputStream();

        var exitCode = ReleaseSchemaInventoryCommand.runIfRequested(
                new String[] { ReleaseSchemaInventoryCommand.OPTION + "=true" },
                new PrintStream(stdoutBytes, true, StandardCharsets.UTF_8),
                new PrintStream(stderrBytes, true, StandardCharsets.UTF_8));

        assertThat(exitCode).hasValue(64);
        assertThat(stdoutBytes.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(stderrBytes.toString(StandardCharsets.UTF_8))
                .isEqualTo("release-schema-inventory|error|invalid_arguments\n");
    }

    @Test
    void unrelatedArgumentsRemainAvailableToSpringBoot() {
        assertThat(ReleaseSchemaInventoryCommand.runIfRequested(
                new String[] { "--spring.profiles.active=test" },
                System.out,
                System.err)).isEmpty();
    }

    @Test
    void rejectsAGapInIntegerMigrationVersions(@TempDir Path root) throws Exception {
        writeUtf8(root, "db/migration/V1__first.sql", "select 1;\n");
        writeUtf8(root, "db/migration/V3__third.sql", "select 3;\n");

        try (var classLoader = isolatedClassLoader(root)) {
            assertThatThrownBy(() -> ReleaseSchemaInventoryCommand.inventoryLines(classLoader))
                    .hasMessage("migration_version_gap_or_duplicate");
        }
    }

    @Test
    void rejectsUtf8BomBeforeCalculatingChecksum(@TempDir Path root) throws Exception {
        Path migration = root.resolve("db/migration/V1__first.sql");
        Files.createDirectories(migration.getParent());
        Files.write(migration, new byte[] { (byte) 0xef, (byte) 0xbb, (byte) 0xbf, 's', 'e', 'l', 'e', 'c', 't' });

        try (var classLoader = isolatedClassLoader(root)) {
            assertThatThrownBy(() -> ReleaseSchemaInventoryCommand.inventoryLines(classLoader))
                    .hasMessage("migration_bom");
        }
    }

    @Test
    void rejectsMalformedUtf8BeforeCalculatingChecksum(@TempDir Path root) throws Exception {
        Path migration = root.resolve("db/migration/V1__first.sql");
        Files.createDirectories(migration.getParent());
        Files.write(migration, new byte[] { (byte) 0xc3, (byte) 0x28 });

        try (var classLoader = isolatedClassLoader(root)) {
            assertThatThrownBy(() -> ReleaseSchemaInventoryCommand.inventoryLines(classLoader))
                    .hasMessage("migration_invalid_utf8");
        }
    }

    @Test
    void rejectsAnEmptyMigrationResource(@TempDir Path root) throws Exception {
        Path migration = root.resolve("db/migration/V1__first.sql");
        Files.createDirectories(migration.getParent());
        Files.write(migration, new byte[0]);

        try (var classLoader = isolatedClassLoader(root)) {
            assertThatThrownBy(() -> ReleaseSchemaInventoryCommand.inventoryLines(classLoader))
                    .hasMessage("migration_empty");
        }
    }

    @Test
    void rejectsNestedMigrationResources(@TempDir Path root) throws Exception {
        writeUtf8(root, "db/migration/V1__first.sql", "select 1;\n");
        writeUtf8(root, "db/migration/nested/V2__second.sql", "select 2;\n");

        try (var classLoader = isolatedClassLoader(root)) {
            assertThatThrownBy(() -> ReleaseSchemaInventoryCommand.inventoryLines(classLoader))
                    .hasMessage("nested_or_directory_resource");
        }
    }

    @Test
    void rejectsUnsupportedMigrationResourceNames(@TempDir Path root) throws Exception {
        writeUtf8(root, "db/migration/V1__first.sql", "select 1;\n");
        writeUtf8(root, "db/migration/R__repeatable.sql", "select 2;\n");

        try (var classLoader = isolatedClassLoader(root)) {
            assertThatThrownBy(() -> ReleaseSchemaInventoryCommand.inventoryLines(classLoader))
                    .hasMessage("unsupported_migration_filename");
        }
    }

    private static URLClassLoader isolatedClassLoader(Path root) throws Exception {
        return new URLClassLoader(new java.net.URL[] { root.toUri().toURL() }, null);
    }

    private static void writeUtf8(Path root, String relativePath, String value) throws Exception {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, value, StandardCharsets.UTF_8);
    }
}
