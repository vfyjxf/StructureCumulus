package dev.vfyjxf.cumulus.kernel.space;

public enum Flip {
    NONE,
    LEFT_RIGHT,
    FRONT_BACK;

    public boolean isMirror() {
        return this != NONE;
    }
}
