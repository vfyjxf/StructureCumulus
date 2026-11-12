package dev.vfyjxf.cumulus.mc.preview.scene;

/**
 * A box in cell units: the hull of the cells that draw something (design 17 §3.3), never a declared
 * bounds. A cell occupies its unit cube, so a one-cell hull runs corner to corner one unit long.
 */
public record Hull(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

    /** The box that holds both boxes; an empty operand contributes nothing. */
    public Hull union(Hull other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        return new Hull(
            Math.min(minX, other.minX),
            Math.min(minY, other.minY),
            Math.min(minZ, other.minZ),
            Math.max(maxX, other.maxX),
            Math.max(maxY, other.maxY),
            Math.max(maxZ, other.maxZ)
        );
    }

    /** True when an axis is inverted, which is how "no cell draws" is carried. */
    public boolean isEmpty() {
        return minX > maxX || minY > maxY || minZ > maxZ;
    }

    /** The box the fit uses: every extent at least one cell; the one-cell box when this hull is empty. */
    Hull fitBox() {
        if (isEmpty()) {
            return new Hull(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        }
        float halfX = Math.max((maxX - minX) * 0.5F, 0.5F);
        float halfY = Math.max((maxY - minY) * 0.5F, 0.5F);
        float halfZ = Math.max((maxZ - minZ) * 0.5F, 0.5F);
        float centreX = (minX + maxX) * 0.5F;
        float centreY = (minY + maxY) * 0.5F;
        float centreZ = (minZ + maxZ) * 0.5F;
        return new Hull(
            centreX - halfX,
            centreY - halfY,
            centreZ - halfZ,
            centreX + halfX,
            centreY + halfY,
            centreZ + halfZ
        );
    }

    float centreX() {
        return (minX + maxX) * 0.5F;
    }

    float centreY() {
        return (minY + maxY) * 0.5F;
    }

    float centreZ() {
        return (minZ + maxZ) * 0.5F;
    }

    /** The distance from the centre to a far corner: the sphere the perspective fit uses. */
    public float radius() {
        float x = maxX - minX;
        float y = maxY - minY;
        float z = maxZ - minZ;
        return 0.5F * (float) Math.sqrt(x * x + y * y + z * z);
    }

    /** The corner-to-corner length; the axis gizmo's length is a quarter of it. */
    float diagonal() {
        return 2.0F * radius();
    }
}
