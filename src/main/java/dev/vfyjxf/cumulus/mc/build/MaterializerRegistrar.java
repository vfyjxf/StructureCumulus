package dev.vfyjxf.cumulus.mc.build;

/**
 * Collects materializer contributions until {@link #materializers()} freezes the chain. The built-in
 * vanilla materializer is always the chain's last entry, so a plugin answers first.
 */
public interface MaterializerRegistrar {

    /**
     * Adds one materializer under its own id. The same value registered twice collapses; a different
     * value under an id the chain already holds is rejected with {@code SC-SPI-0003}.
     */
    MaterializerRegistrar materializer(Materializer materializer);

    /** The frozen chain: the contributions in registration order, then the built-in materializer. */
    Materializers materializers();

    static MaterializerRegistrar builtin() {
        return new DefaultMaterializerRegistrar();
    }
}
