package com.wallstreetreceipts.api.application.scoring;

import java.io.PrintStream;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.wallstreetreceipts.api.infrastructure.persistence.*;

/** Explicit offline-input append. No Spring startup, importer, migration, HTTP listener or provider. */
public final class DemoScoringReceiptCommand {
    private DemoScoringReceiptCommand() {}
    public static void main(String[] args) { System.exit(run(args, System.getenv(), System.out)); }

    public static int run(String[] args, Map<String, String> environment, PrintStream output) {
        EndpointScoringInput input;
        String snapshot;
        PGSimpleDataSource source;
        try {
            if (args.length != 3 || !args[0].equals("--confirm=APPEND_DEMO_SCORING_RECEIPT")
                    || !args[1].startsWith("--input=") || !args[2].startsWith("--snapshot=")) throw new IllegalArgumentException();
            snapshot = args[2].substring("--snapshot=".length());
            if (!snapshot.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")) throw new IllegalArgumentException();
            String url = environment.getOrDefault("WSR_DEMO_SCORING_JDBC_URL", "");
            var match = java.util.regex.Pattern.compile("jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{3,4})/wsr_scoring_demo").matcher(url);
            if (!match.matches() || Integer.parseInt(match.group(1)) < 1024 || Integer.parseInt(match.group(1)) > 65535) throw new IllegalArgumentException();
            String user = environment.getOrDefault("WSR_DEMO_SCORING_USER", "");
            String password = environment.getOrDefault("WSR_DEMO_SCORING_PASSWORD", "");
            if (!user.matches("[a-z_][a-z0-9_]{0,62}") || password.isBlank()) throw new IllegalArgumentException();
            Path path = Path.of(args[1].substring("--input=".length()));
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 1048576) throw new IllegalArgumentException();
            try (var stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                input = EndpointScoringInputCodec.decode(stream.readNBytes(1048577));
            }
            source = new PGSimpleDataSource(); source.setURL(url); source.setUser(user); source.setPassword(password);
            source.setConnectTimeout(3); source.setSocketTimeout(5); source.setApplicationName("wsr-demo-scoring-append");
        } catch (Exception invalid) {
            output.println("DEMO_SCORING_REJECTED: explicit confirmation, bounded canonical input and local DEMO configuration required");
            return 64;
        }
        try {
            var jdbc = new NamedParameterJdbcTemplate(source); jdbc.getJdbcTemplate().setQueryTimeout(3);
            var calls = new JdbcAnalystCallRepository(jdbc);
            var revisions = new JdbcAnalystCallRevisionRepository(jdbc);
            var service = new ScoringReceiptService(new JdbcScoringReceiptRepository(source), new ScoringLedgerVerifier(calls, revisions), calls, Clock.systemUTC());
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            transaction.setTimeout(10);
            var saved = Objects.requireNonNull(transaction.execute(status -> service.append(input, snapshot)));
            output.println("DEMO_SCORING_RECEIPT: " + saved.stored().receiptId() + " " + saved.stored().inputFingerprint());
            return 0;
        } catch (RuntimeException failure) {
            // A connection failure at commit is not proof of rollback. Retry the same input, never invent a new as-of.
            output.println("DEMO_SCORING_UNCONFIRMED: verify or retry the identical input; no successful append is claimed");
            return 69;
        }
    }
}
