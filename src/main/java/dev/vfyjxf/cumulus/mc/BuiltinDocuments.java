package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.BaySpec;
import dev.vfyjxf.cumulus.kernel.document.BlueprintSpec;
import dev.vfyjxf.cumulus.kernel.document.CheckSpec;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.document.StructureSetSpec;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

/**
 * The in-memory document source: the 1x1x1 {@code air_cell}, the two demo cells of the structure set
 * {@code set_demo}, and that set itself.
 */
@NullMarked
final class BuiltinDocuments {

    private static final NamespacedId airCell = NamespacedId.of(Constants.modId, "air_cell");
    private static final NamespacedId setRoot = NamespacedId.of(Constants.modId, "set_root");
    private static final NamespacedId setWing = NamespacedId.of(Constants.modId, "set_wing");
    private static final NamespacedId setDemo = NamespacedId.of(Constants.modId, "set_demo");
    private static final NamespacedId leftWing = NamespacedId.of(Constants.modId, "left_wing");
    private static final NamespacedId cell = NamespacedId.of(Constants.modId, "cell");
    private static final NamespacedId occupant = NamespacedId.of(Constants.modId, "occupant");

    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);

    private static final Value occupantArgs = Value
            .object(Map.of("cell", Value.array(List.of(Value.number(0), Value.number(0), Value.number(0)))));

    private static final Document airCellDocument = cellDocument(airCell, false);

    private static final Document setRootDocument = cellDocument(setRoot, true);

    private static final Document setWingDocument = cellDocument(setWing, true);

    private static final StructureSetDocument setDemoDocument = new StructureSetDocument(
        StructureSetDocument.currentVersion,
        new StructureSetSpec(
            setDemo,
            setRoot,
            List.of(
                new SatelliteSpec(
                    leftWing,
                    setWing,
                    LatticePoint.of(1, 0, 0),
                    Orientation.identity,
                    true,
                    Value.object(),
                    null,
                    List.of(LinkSpec.face(LatticePoint.origin, LatticePoint.origin)),
                    null
                )
            ),
            null
        ),
        Map.of()
    );

    static NamespacedId airCellId() {
        return airCell;
    }

    static NamespacedId setRootId() {
        return setRoot;
    }

    static NamespacedId setWingId() {
        return setWing;
    }

    static NamespacedId setDemoId() {
        return setDemo;
    }

    static NamespacedId leftWingId() {
        return leftWing;
    }

    static DocumentSource source() {
        return DocumentSource.of(
            Map.of(airCell, airCellDocument, setRoot, setRootDocument, setWing, setWingDocument),
            Map.of(setDemo, setDemoDocument)
        );
    }

    /** The built-in blueprint ids in listing order; the structure-set demo is not a blueprint. */
    static List<NamespacedId> blueprintIds() {
        return List.of(airCell, setRoot, setWing);
    }

    private static Document cellDocument(NamespacedId id, boolean claimable) {
        return new Document(
            Document.currentVersion,
            new BlueprintSpec(
                id,
                bounds,
                false,
                false,
                Value.object(),
                List.of(
                    new BaySpec(
                        cell,
                        CuboidSet.of(bounds),
                        Coverage.everyCell,
                        claimable ? Footprint.box() : Footprint.none(),
                        claimable ? Sharing.exclusive : Sharing.shared,
                        null,
                        null,
                        null
                    )
                ),
                List.of(),
                List.of(
                    new CheckSpec(
                        occupant,
                        cell,
                        NamespacedId.of(Constants.modId, "occupant"),
                        occupantArgs,
                        NamespacedId.of(Constants.modId, "any_occupant"),
                        Value.object(),
                        null,
                        null
                    )
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                Map.of(),
                null,
                null
            ),
            Map.of(),
            Map.of(),
            null
        );
    }

    private BuiltinDocuments() {}
}
