package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;

import java.util.Locale;

final class BuiltinCodes {

    static Catalog<CodeEntry> catalog() {
        Catalog.Builder<CodeEntry> builder = Catalog.builder(NamespacedId.of("structurecumulus", "codes"));
        for (CodeEntry entry : Codes.all) {
            builder.put(NamespacedId.of("structurecumulus", entry.code().text().toLowerCase(Locale.ROOT)), entry);
        }
        return builder.build();
    }

    private BuiltinCodes() {}
}
