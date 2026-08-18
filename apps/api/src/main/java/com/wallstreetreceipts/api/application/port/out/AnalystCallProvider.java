package com.wallstreetreceipts.api.application.port.out;

public interface AnalystCallProvider {

    AnalystCallDataSet load();

    String providerName();
}
