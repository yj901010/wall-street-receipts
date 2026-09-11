package com.wallstreetreceipts.api.application.scoring;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DemoScoringReceiptCommandTest {
    @TempDir Path temporary;
    private final Map<String,String> environment = Map.of("WSR_DEMO_SCORING_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/wsr_scoring_demo",
            "WSR_DEMO_SCORING_USER", "demo_reader", "WSR_DEMO_SCORING_PASSWORD", "PRIVATE_VALUE_DO_NOT_ECHO");
    private String[] args(Path path) { return new String[]{"--confirm=APPEND_DEMO_SCORING_RECEIPT", "--input=" + path, "--snapshot=receipt-snapshot"}; }
    private void rejected(String[] args, Map<String,String> env) {
        var output = new ByteArrayOutputStream();
        assertThat(DemoScoringReceiptCommand.run(args, env, new PrintStream(output))).isEqualTo(64);
        assertThat(output.toString()).startsWith("DEMO_SCORING_REJECTED:").doesNotContain("PRIVATE_VALUE", "jdbc:", temporary.toString(), "Exception");
    }
    @ParameterizedTest @ValueSource(strings={"", "jdbc:postgresql://localhost:5432/wsr_scoring_demo", "jdbc:postgresql://127.0.0.1:5432/wsr",
            "jdbc:postgresql://192.0.2.1:5432/wsr_scoring_demo", "jdbc:postgresql://127.0.0.1:5432/wsr_scoring_demo?user=secret",
            "jdbc:postgresql://127.0.0.1:80/wsr_scoring_demo", "jdbc:postgresql://127.0.0.1:65536/wsr_scoring_demo"})
    void databaseMustBeExplicitLoopbackDedicatedDemoWithoutUrlOptions(String url) {
        var env = new HashMap<>(environment); env.put("WSR_DEMO_SCORING_JDBC_URL", url); rejected(args(temporary.resolve("missing")), env);
    }
    @Test void confirmationExactArgumentsAndCompleteEnvironmentAreMandatory() throws Exception {
        Path file = temporary.resolve("demo.wsr"); Files.write(file, EndpointScoringInputCodec.encode(EndpointScoringFixture.input()));
        rejected(new String[]{}, environment);
        var wrong = args(file); wrong[0] = "--confirm=true"; rejected(wrong, environment);
        wrong = Arrays.copyOf(args(file), 4); wrong[3] = "--spring.profiles.active=production"; rejected(wrong, environment);
        wrong = args(file); wrong[2] = "--snapshot=../../PRIVATE_VALUE"; rejected(wrong, environment);
        for (var key : environment.keySet()) { var env = new HashMap<>(environment); env.remove(key); rejected(args(file), env); }
        rejected(args(file), Map.of("POSTGRES_HOST", "127.0.0.1", "POSTGRES_PASSWORD", "PRIVATE_VALUE"));
    }
    @ParameterizedTest @ValueSource(ints={0,3,1048577})
    void inputMustBeBoundedCanonicalBytesBeforeAnyDatabaseConnection(int size) throws Exception {
        var file = temporary.resolve("bad.wsr"); Files.write(file, new byte[size]); rejected(args(file), environment);
        rejected(args(temporary), environment);
    }
}
