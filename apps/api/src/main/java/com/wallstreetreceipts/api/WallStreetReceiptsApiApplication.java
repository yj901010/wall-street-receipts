package com.wallstreetreceipts.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.wallstreetreceipts.api.release.ReleaseSchemaInventoryCommand;

@SpringBootApplication
public class WallStreetReceiptsApiApplication {

    public static void main(String[] args) {
        if (java.util.Arrays.stream(args).anyMatch(arg -> arg.startsWith("--wsr-schedule-bls-cpi"))) {
            if (args.length != 1 || !"--wsr-schedule-bls-cpi".equals(args[0])) {
                throw new IllegalArgumentException("CPI scheduling accepts only --wsr-schedule-bls-cpi; use server environment for configuration");
            }
            com.wallstreetreceipts.api.application.cpi.ScheduleCpiCommand.runCli();
            return;
        }
        if (java.util.Arrays.stream(args).anyMatch(arg -> arg.startsWith("--wsr-collect-bls-cpi"))) {
            if (args.length != 1 || !"--wsr-collect-bls-cpi".equals(args[0])) {
                throw new IllegalArgumentException("CPI collection accepts only --wsr-collect-bls-cpi; use server environment for configuration");
            }
            com.wallstreetreceipts.api.application.cpi.CollectCpiCommand.runCli();
            return;
        }
        var commandExitCode = ReleaseSchemaInventoryCommand.runIfRequested(args, System.out, System.err);
        if (commandExitCode.isPresent()) {
            if (commandExitCode.getAsInt() != 0) {
                System.exit(commandExitCode.getAsInt());
            }
            return;
        }

        SpringApplication.run(WallStreetReceiptsApiApplication.class, args);
    }
}
