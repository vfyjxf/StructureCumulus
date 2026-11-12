package dev.vfyjxf.cumulus.mc.preview.jei;

/**
 * The category's three view modes (design 16 §11). The mode is per-recipe view state, like the yaw
 * and the slice: the button cycles it, no payload carries it.
 */
public enum PreviewViewMode {
    isometric("isometric"), threeView("three-view"), sliceGrid("slice grid");

    private final String label;

    PreviewViewMode(String label) {
        this.label = label;
    }

    /** The name the face prints on its mode button. */
    public String label() {
        return label;
    }

    public PreviewViewMode next() {
        PreviewViewMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }
}
