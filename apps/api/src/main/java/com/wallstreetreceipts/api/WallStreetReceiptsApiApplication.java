package com.wallstreetreceipts.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.wallstreetreceipts.api.release.ReleaseSchemaInventoryCommand;

@SpringBootApplication
public class WallStreetReceiptsApiApplication {

    public static void main(String[] args) {
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
