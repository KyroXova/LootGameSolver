package com.kyroxova.lootgamesolver.client;

import java.util.Locale;

final class OilAvoidance {
    private OilAvoidance() {}

    static boolean isOil(String name) {
        if (name == null) return false;
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.startsWith("oil") || (normalized.endsWith("oil") && !normalized.endsWith("soil"))
            || normalized.contains("crudeoil");
    }
}
