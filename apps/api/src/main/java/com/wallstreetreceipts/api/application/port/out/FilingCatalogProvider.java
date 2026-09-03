package com.wallstreetreceipts.api.application.port.out;

import com.wallstreetreceipts.api.domain.filing.FilingCatalog;

/** Provider-neutral boundary for loading an issuer's published filing catalog. */
public interface FilingCatalogProvider {

    FilingCatalog loadRecentFilings(String cik);

    String providerName();
}
