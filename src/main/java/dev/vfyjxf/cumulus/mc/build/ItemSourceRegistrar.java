package dev.vfyjxf.cumulus.mc.build;

/**
 * Collects item-source kinds until {@link #itemSources()} freezes them. A kind is a provider, not an
 * instance: the source needs the requesting player and level, so the host builds it per run.
 */
public interface ItemSourceRegistrar {

    /**
     * Adds one provider under its own id. The same value registered twice collapses; a different
     * value under an id the table already holds is rejected with {@code SC-SPI-0003}.
     */
    ItemSourceRegistrar itemSource(ItemSourceProvider provider);

    /** The frozen table of registered kinds. */
    ItemSources itemSources();

    static ItemSourceRegistrar empty() {
        return new DefaultItemSourceRegistrar();
    }
}
