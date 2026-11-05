package dev.vfyjxf.cumulus.kernel.claim;

/** The only conflict predicate. */
public final class ClaimMatrix {

    public static boolean conflicts(ClaimRow a, ClaimRow b) {
        if (!a.cell().equals(b.cell())) {
            return false;
        }
        if (a.instance().equals(b.instance())) {
            return false;
        }
        return a.sharing().blocks() || b.sharing().blocks();
    }

    private ClaimMatrix() {}
}
