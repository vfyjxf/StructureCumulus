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
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

/** The M1 in-memory document source: one 1x1x1 cell that accepts any occupant. */
@NullMarked
final class BuiltinDocuments {

    private static final NamespacedId airCell = NamespacedId.of(Constants.modId, "air_cell");
    private static final NamespacedId cell = NamespacedId.of(Constants.modId, "cell");
    private static final NamespacedId occupant = NamespacedId.of(Constants.modId, "occupant");

    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);

    private static final Document airCellDocument = new Document(
        Document.currentVersion,
        new BlueprintSpec(
            airCell,
            bounds,
            false,
            Value.object(),
            List.of(
                new BaySpec(
                    cell,
                    CuboidSet.of(bounds),
                    Coverage.everyCell,
                    Footprint.none(),
                    Sharing.shared,
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
                    Value.object(
                        Map.of("cell", Value.array(List.of(Value.number(0), Value.number(0), Value.number(0))))
                    ),
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
            null
        ),
        Map.of(),
        Map.of()
    );

    static NamespacedId airCellId() {
        return airCell;
    }

    static DocumentSource source() {
        return DocumentSource.of(Map.of(airCell, airCellDocument));
    }

    private BuiltinDocuments() {}
}
