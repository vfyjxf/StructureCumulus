package dev.vfyjxf.cumulus.mc.build;

/** Whether an item operation may change the source. */
public enum ExtractionMode {
    simulate, execute;

    public boolean simulated() {
        return this == simulate;
    }
}
