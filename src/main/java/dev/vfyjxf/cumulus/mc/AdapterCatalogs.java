package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.spi.CatalogRegistrar;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.mc.build.BuildCodes;

/** Seeds the registrar with the adapter's host tables so a plugin cannot replace a host id. */
final class AdapterCatalogs {

    static void seed(CatalogRegistrar registrar) {
        Catalog<ExpectationFactory> expectations = AdapterExpectations.catalog();
        for (NamespacedId id : expectations.ids()) {
            registrar.expectation(id, expectations.require(id));
        }
        Catalog<CodeEntry> codes = AdapterCodes.catalog();
        for (NamespacedId id : codes.ids()) {
            registrar.code(codes.require(id));
        }
        for (CodeEntry entry : BuildCodes.entries()) {
            registrar.code(entry);
        }
    }

    private AdapterCatalogs() {}
}
