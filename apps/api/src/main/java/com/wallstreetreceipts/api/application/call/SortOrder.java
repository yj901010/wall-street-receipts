package com.wallstreetreceipts.api.application.call;

public enum SortOrder {
    ASC,
    DESC;

    public static SortOrder fromApiName(String value) {
        return switch (value) {
            case "asc" -> ASC;
            case "desc" -> DESC;
            default -> throw new IllegalArgumentException("Unsupported sort order: " + value);
        };
    }

    public String apiName() {
        return name().toLowerCase();
    }
}
