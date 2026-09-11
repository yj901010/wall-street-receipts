package com.wallstreetreceipts.api.application.scoring;

import static org.assertj.core.api.Assertions.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import com.github.dockerjava.api.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.WallStreetReceiptsApiApplication;
import com.wallstreetreceipts.api.infrastructure.persistence.*;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallProvider;

/** Explicit opt-in acceptance, not an automatically skipped test: packaged CLI -> PG -> Spring -> Next. */
class ScoringReceiptBrowserIT {
    @Test void ownedDemoCommandAndReadOnlyAuditWorkflow() throws Exception {
        assertThat(System.getProperty("wsr.scoring.browser.confirm")).isEqualTo("DISPOSABLE_DEMO_ONLY");
        Path root=Path.of("").toAbsolutePath().getParent().getParent();
        Path web=Path.of(System.getProperty("wsr.scoring.browser.web")).toRealPath();
        assertThat(web.startsWith(root.resolve(".cache").toRealPath())).isTrue();
        for(var tree:List.of("src","public","operator","scoring","e2e")) sameTree(root.resolve("apps/web/"+tree),web.resolve(tree));
        sameTree(root.resolve("fixtures"),web.getParent().getParent().resolve("fixtures"));
        for(var file:List.of("package.json","next.config.ts","tsconfig.json","playwright.config.ts")) assertThat(Files.mismatch(root.resolve("apps/web/"+file),web.resolve(file))).isEqualTo(-1);
        for(var directory:List.of(web,web.getParent(),web.getParent().getParent())) try(var entries=Files.list(directory)) {
            assertThat(entries.noneMatch(p->p.getFileName().toString().startsWith(".env"))).isTrue();
        }
        Path jar=Path.of(System.getProperty("wsr.scoring.browser.jar")).toRealPath();
        assertThat(jar.startsWith(root.resolve(".cache").toRealPath())).isTrue();
        var logs=Files.createTempDirectory(root.resolve(".cache"),"adr082-evidence-");
        child("node",List.of("node_modules/next/dist/bin/next","build"),web,logs.resolve("build.log"),Map.of(),120);
        try(var db=new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("wsr_scoring_demo").withUsername("receipt_demo_owner")
                .withPassword("DisposableDatabaseOnly").withReuse(false).withLabel("com.wallstreetreceipts.scoring-browser","ADR-082")
                .withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),ExposedPort.tcp(5432))))) {
            db.start();assertThat(InetAddress.getByName(db.getHost()).isLoopbackAddress()).isTrue();
            Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).load().migrate();
            var ds=new DriverManagerDataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword());var owner=new JdbcTemplate(ds);
            var calls=new JdbcAnalystCallRepository(new NamedParameterJdbcTemplate(ds));var provider=new FixtureAnalystCallProvider(new ObjectMapper());
            var transaction=new TransactionTemplate(new DataSourceTransactionManager(ds));
            var call=transaction.execute(s->{calls.importDataSet(provider.load());return ScoringReceiptFixture.seed(provider,calls);});
            // The older domain-only fixture uses a placeholder hash. This owned UI ledger needs the public SHA-256 contract.
            String demoHash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest("DEMO source terms".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertThat(owner.update("UPDATE source_documents SET content_hash=? WHERE source_document_id='receipt-doc'",demoHash)).isEqualTo(1);
            var revision=ScoringReceiptFixture.correction(call);
            transaction.execute(s->new JdbcAnalystCallRevisionRepository(new NamedParameterJdbcTemplate(ds)).saveIfAbsent(revision));
            owner.execute("CREATE ROLE receipt_ui_reader LOGIN PASSWORD 'DisposableReaderOnly'");
            owner.execute("GRANT USAGE ON SCHEMA public TO receipt_ui_reader");owner.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO receipt_ui_reader");
            try(var context=(ServletWebServerApplicationContext)new SpringApplicationBuilder(WallStreetReceiptsApiApplication.class).run(
                    "--spring.config.location=classpath:/application.yml","--spring.config.import=","--spring.profiles.active=receipt-browser-test","--spring.flyway.enabled=false",
                    "--spring.datasource.url="+db.getJdbcUrl(),"--spring.datasource.driver-class-name=org.postgresql.Driver","--spring.datasource.username=receipt_ui_reader",
                    "--spring.datasource.password=DisposableReaderOnly","--app.operator-api.enabled=false","--app.cpi.enabled=false","--app.public-data.sec.enabled=false",
                    "--app.providers.market=fixture","--app.providers.analyst=disabled","--server.address=127.0.0.1","--server.port=0","--server.shutdown=immediate")) {
                var reader=context.getBean(JdbcTemplate.class);assertThat(reader.queryForObject("SELECT current_user",String.class)).isEqualTo("receipt_ui_reader");
                assertThatThrownBy(()->reader.update("DELETE FROM demo_scoring_receipts WHERE FALSE")).isInstanceOf(org.springframework.dao.DataAccessException.class);
                var empty=inventory(owner);browser(web,logs,context.getWebServer().getPort(),"empty",null,null);assertThat(inventory(owner)).isEqualTo(empty);
                var input=ScoringReceiptFixture.input(call);Path file=logs.resolve("explicit-demo-input.wsr");Files.write(file,EndpointScoringInputCodec.encode(input));
                var saved=append(jar,file,logs.resolve("append.log"),db);assertThat(append(jar,file,logs.resolve("retry.log"),db)).isEqualTo(saved);
                for(var candidate:List.of(edit(input,"evaluationAsOf",BASIS.plusSeconds(1)),edit(input,"endpointCandidates",List.of()),ScoringReceiptFixture.corrected(input,revision))) {
                    var candidateFile=logs.resolve(UUID.randomUUID()+".wsr");Files.write(candidateFile,EndpointScoringInputCodec.encode(candidate));append(jar,candidateFile,logs.resolve(UUID.randomUUID()+".log"),db);
                }
                assertThat(owner.queryForObject("SELECT COUNT(*) FROM demo_scoring_receipts",Integer.class)).isEqualTo(4);
                var seeded=inventory(owner);var oldTables=new TreeMap<>(seeded);oldTables.put("demo_scoring_receipts",empty.get("demo_scoring_receipts"));assertThat(oldTables).isEqualTo(empty);
                browser(web,logs,context.getWebServer().getPort(),"ready",saved[0],saved[1]);assertThat(inventory(owner)).isEqualTo(seeded);
                owner.execute("REVOKE SELECT ON demo_scoring_receipts FROM receipt_ui_reader");
                browser(web,logs,context.getWebServer().getPort(),"unavailable",saved[0],saved[1]);assertThat(inventory(owner)).isEqualTo(seeded);
                owner.execute("GRANT SELECT ON demo_scoring_receipts TO receipt_ui_reader");
                browser(web,logs,context.getWebServer().getPort(),"recovered",saved[0],saved[1]);assertThat(inventory(owner)).isEqualTo(seeded);
            }
        }
        publicBrowser(web,logs);
        System.out.println("ADR-082 DEMO PASS: packaged explicit append, idempotent retry, unchanged parents and SELECT-only production UI; evidence "+logs);
    }
    private static String[] append(Path jar,Path input,Path log,PostgreSQLContainer<?> db) throws Exception {
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        child(java,List.of("-Dloader.main=com.wallstreetreceipts.api.application.scoring.DemoScoringReceiptCommand","-cp",jar.toString(),
                "org.springframework.boot.loader.launch.PropertiesLauncher","--confirm=APPEND_DEMO_SCORING_RECEIPT","--input="+input,"--snapshot=receipt-snapshot"),input.getParent(),log,
                Map.of("WSR_DEMO_SCORING_JDBC_URL","jdbc:postgresql://127.0.0.1:"+db.getMappedPort(5432)+"/wsr_scoring_demo","WSR_DEMO_SCORING_USER",db.getUsername(),"WSR_DEMO_SCORING_PASSWORD",db.getPassword()),30);
        String output=Files.readString(log).strip();assertThat(output).matches("DEMO_SCORING_RECEIPT: [0-9a-f-]{36} [0-9a-f]{64}");
        return output.substring("DEMO_SCORING_RECEIPT: ".length()).split(" ");
    }
    private static void browser(Path web,Path logs,int api,String phase,String id,String hash) throws Exception {
        int port;try(var socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
        var env=new HashMap<String,String>();env.put("WSR_SCORING_UI_PORT",""+port);env.put("WSR_SCORING_API_PORT",""+api);env.put("WSR_SCORING_PHASE",phase);
        if(id!=null){env.put("WSR_SCORING_RECEIPT_ID",id);env.put("WSR_SCORING_INPUT_HASH",hash);}
        child("node",List.of("node_modules/@playwright/test/cli.js","test","--config","scoring/full-stack.config.ts"),web,logs.resolve(phase+".log"),env,180);
        try(var socket=new ServerSocket(port,1,InetAddress.getByName("127.0.0.1"))){assertThat(socket.isBound()).isTrue();}
    }
    private static void publicBrowser(Path web,Path logs) throws Exception {
        int port;try(var socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
        child("node",List.of("node_modules/@playwright/test/cli.js","test","--config","scoring/public.config.ts"),web,logs.resolve("public.log"),
                Map.of("WSR_SCORING_UI_PORT",""+port),900);
        try(var socket=new ServerSocket(port,1,InetAddress.getByName("127.0.0.1"))){assertThat(socket.isBound()).isTrue();}
    }
    private static void child(String executable,List<String> arguments,Path cwd,Path log,Map<String,String> extra,int seconds) throws Exception {
        var command=new ArrayList<String>();command.add(executable);command.addAll(arguments);
        var builder=new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());builder.environment().clear();
        System.getenv().forEach((key,value)->{if(key.matches("(?i)PATH|SYSTEMROOT|WINDIR|TEMP|TMP|COMSPEC|PATHEXT|HOME|USERPROFILE|LOCALAPPDATA|APPDATA"))builder.environment().put(key,value);});
        builder.environment().put("NEXT_TELEMETRY_DISABLED","1");builder.environment().putAll(extra);
        var child=builder.start();var owned=new LinkedHashSet<ProcessHandle>();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        try{do{child.descendants().forEach(owned::add);if(Files.size(log)>4_000_000||System.nanoTime()>deadline)throw new IllegalStateException("Owned rehearsal exceeded bounds");}
            while(!child.waitFor(100,TimeUnit.MILLISECONDS));assertThat(child.exitValue()).withFailMessage("Inspect owned log %s",log).isZero();
        }finally{child.descendants().forEach(owned::add);for(var process:new ArrayList<>(owned).reversed())if(process.isAlive())process.destroyForcibly();
            if(child.isAlive())child.destroyForcibly();child.waitFor(10,TimeUnit.SECONDS);}
    }
    private static void sameTree(Path original,Path mirror) throws Exception {
        try(var sources=Files.walk(original);var targets=Files.walk(mirror)){
            var expected=sources.map(original::relativize).collect(java.util.stream.Collectors.toSet());assertThat(targets.map(mirror::relativize).collect(java.util.stream.Collectors.toSet())).isEqualTo(expected);
            for(var relative:expected){var a=original.resolve(relative);var b=mirror.resolve(relative);assertThat(Files.isSymbolicLink(a)||Files.isSymbolicLink(b)).isFalse();
                assertThat(b.toRealPath().startsWith(mirror.toRealPath())).isTrue();if(Files.isRegularFile(a))assertThat(Files.mismatch(a,b)).isEqualTo(-1);}
        }
    }
    private static Map<String,String> inventory(JdbcTemplate jdbc) {
        var result=new TreeMap<String,String>();for(var table:jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history'",String.class)){
            assertThat(table).matches("[a-z_]+");result.put(table,jdbc.queryForObject("SELECT COALESCE(string_agg(row_to_json(t)::text, E'\\n' ORDER BY row_to_json(t)::text),'') FROM "+table+" t",String.class));}return result;
    }
}
