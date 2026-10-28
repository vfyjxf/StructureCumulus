package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.PresenceSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Realistic multiblock shapes for the structure-set stress scenarios: IE, TEP, AE2, envelopes and rotation. */
final class StructureSetStressFixtures {

    static final NamespacedId casing = id("casing");
    static final NamespacedId port = id("port");
    static final NamespacedId shell = id("preheater_shell");
    static final NamespacedId plug = id("preheater_plug");
    static final NamespacedId stove = id("hot_blast_main");
    static final NamespacedId reactorCasing = id("reactor_casing");
    static final NamespacedId controller = id("reactor_controller");
    static final NamespacedId solar = id("solar_generator");
    static final NamespacedId networkCasing = id("network_casing");
    static final NamespacedId networkController = id("network_controller");
    static final NamespacedId drive = id("me_drive");
    static final NamespacedId crafting = id("crafting_storage");
    static final NamespacedId terminal = id("crafting_terminal");
    static final NamespacedId storage = id("storage_cell");
    static final NamespacedId elbowBody = id("elbow_body");
    static final NamespacedId elbowPlug = id("elbow_plug");

    static final Cuboid furnaceBounds = Cuboid.of(0, 0, 0, 3, 3, 3);
    static final LatticePoint furnaceHollow = LatticePoint.of(1, 1, 1);
    static final LatticePoint furnaceLeftPort = LatticePoint.of(0, 1, 1);
    static final LatticePoint furnaceRightPort = LatticePoint.of(2, 1, 1);
    static final Cuboid preheaterBounds = Cuboid.of(0, 0, 0, 2, 2, 2);
    static final LatticePoint preheaterPort = LatticePoint.of(1, 1, 1);
    static final Cuboid wideBounds = Cuboid.of(0, 0, 0, 3, 1, 1);
    static final LatticePoint wideOffset = LatticePoint.of(0, 0, 3);

    static final Cuboid reactorBounds = Cuboid.of(0, 0, 0, 3, 3, 3);
    static final LatticePoint reactorHollow = LatticePoint.of(1, 1, 1);
    static final LatticePoint reactorPort = LatticePoint.of(1, 1, 2);

    static final Cuboid networkBounds = Cuboid.of(0, 0, 0, 3, 1, 3);
    static final LatticePoint networkPort = LatticePoint.of(1, 0, 1);

    static final Cuboid rotationBounds = Cuboid.of(0, 0, 0, 3, 1, 3);
    static final LatticePoint rotationPort = LatticePoint.of(1, 0, 2);

    static final Cuboid envelopeBounds = Cuboid.of(0, 0, 0, 3, 3, 3);
    static final LatticePoint envelopePort = LatticePoint.of(1, 1, 2);

    static final NamespacedId rootId = id("improved_blast_furnace");
    static final NamespacedId preheaterId = id("preheater");
    static final NamespacedId wideId = id("hot_blast_main");
    static final NamespacedId reactorId = id("tep_reactor");
    static final NamespacedId solarId = id("solar_generator");
    static final NamespacedId networkId = id("ae2_network");
    static final NamespacedId driveId = id("me_drive");
    static final NamespacedId craftingId = id("crafting_storage");
    static final NamespacedId terminalId = id("crafting_terminal");
    static final NamespacedId storageId = id("storage_cell");
    static final NamespacedId rotationRootId = id("blast_furnace_controller");
    static final NamespacedId elbowId = id("hot_blast_elbow");
    static final NamespacedId portPipeId = id("port_pipe");
    static final NamespacedId envelopePortRootId = id("envelope_port");
    static final NamespacedId envelopeOnlyRootId = id("envelope_only");
    static final NamespacedId bayOnlyRootId = id("bay_only");

    static final Orientation half = StructureSetTestSupport.half;
    static final Orientation clockwise = StructureSetTestSupport.clockwise;
    static final Orientation counterclockwise = Orientation.of(Turn.counterclockwise, Tilt.level, Flip.none);

    private StructureSetStressFixtures() {}

    static NamespacedId id(String path) {
        return NamespacedId.of("setstress", path);
    }

    // ---------------------------------------------------------------- IE improved blast furnace

    static Blueprint improvedBlastFurnace() {
        CuboidSet shellCells = furnaceBounds.asSet().subtract(CuboidSet.of(Cuboid.of(furnaceHollow)));
        CuboidSet casingCells = shellCells
                .subtract(CuboidSet.of(Cuboid.of(furnaceLeftPort), Cuboid.of(furnaceRightPort)));
        Bay casingBay = Bay
                .of(id("furnace_casing"), casingCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay leftBay = Bay.of(
            id("furnace_left_port"),
            CuboidSet.of(Cuboid.of(furnaceLeftPort)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Bay rightBay = Bay.of(
            id("furnace_right_port"),
            CuboidSet.of(Cuboid.of(furnaceRightPort)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(rootId).bounds(furnaceBounds)
                .checks(
                    List.of(
                        Check.of(
                            id("furnace_casing_check"),
                            casingBay,
                            Focuses.bay(),
                            Expectations.eachCell(Expectations.block(casing))
                        ),
                        Check.of(
                            id("furnace_left_port_check"),
                            leftBay,
                            Focuses.occupant(furnaceLeftPort),
                            Expectations.block(port)
                        ),
                        Check.of(
                            id("furnace_right_port_check"),
                            rightBay,
                            Focuses.occupant(furnaceRightPort),
                            Expectations.block(port)
                        )
                    )
                ).build();
    }

    static Blueprint preheater() {
        CuboidSet body = preheaterBounds.asSet().subtract(CuboidSet.of(Cuboid.of(preheaterPort)));
        Bay bodyBay = Bay.of(id("preheater_body"), body, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay portBay = Bay.of(
            id("preheater_port"),
            CuboidSet.of(Cuboid.of(preheaterPort)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(preheaterId).bounds(preheaterBounds).checks(
            List.of(
                Check.of(
                    id("preheater_body_check"),
                    bodyBay,
                    Focuses.bay(),
                    Expectations.eachCell(Expectations.block(shell))
                ),
                Check.of(id("preheater_port_check"), portBay, Focuses.occupant(preheaterPort), Expectations.block(plug))
            )
        ).build();
    }

    static Blueprint hotBlastMain() {
        Bay bay = Bay.of(id("main_body"), wideBounds.asSet(), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(wideId).bounds(wideBounds).checks(
            List.of(
                Check.of(id("main_body_check"), bay, Focuses.bay(), Expectations.eachCell(Expectations.block(stove)))
            )
        ).build();
    }

    static SatelliteSpec leftPreheater(boolean required, LinkSpec... links) {
        return preheaterSpec(
            StructureSetTestSupport.leftId,
            LatticePoint.of(-2, 0, 0),
            Orientation.identity,
            required,
            null,
            links
        );
    }

    static SatelliteSpec rightPreheater(boolean required, LinkSpec... links) {
        return preheaterSpec(StructureSetTestSupport.rightId, LatticePoint.of(4, 0, 2), half, required, null, links);
    }

    static SatelliteSpec preheaterSpec(
        NamespacedId id,
        LatticePoint at,
        Orientation orientation,
        boolean required,
        @Nullable PresenceSpec presence,
        LinkSpec... links
    ) {
        return new SatelliteSpec(
            id,
            preheaterId,
            at,
            orientation,
            required,
            Value.object(),
            presence,
            List.of(links),
            null
        );
    }

    static SatelliteSpec wideMain(boolean required, LinkSpec... links) {
        return new SatelliteSpec(
            wideId,
            wideId,
            wideOffset,
            Orientation.identity,
            required,
            Value.object(),
            null,
            List.of(links),
            null
        );
    }

    static LinkSpec leftLink() {
        return LinkSpec.face(furnaceLeftPort, preheaterPort);
    }

    static LinkSpec rightLink() {
        return LinkSpec.face(furnaceRightPort, preheaterPort);
    }

    static List<LinkSpec> wideLinks() {
        return List.of(
            LinkSpec.face(LatticePoint.of(0, 0, 2), LatticePoint.of(0, 0, 0)),
            LinkSpec.face(LatticePoint.of(1, 0, 2), LatticePoint.of(1, 0, 0)),
            LinkSpec.face(LatticePoint.of(2, 0, 2), LatticePoint.of(2, 0, 0))
        );
    }

    static CompiledSatellite compiledPreheater(SatelliteSpec spec) {
        return new CompiledSatellite(spec, preheater());
    }

    static CompiledSatellite compiledWideMain(SatelliteSpec spec) {
        return new CompiledSatellite(spec, hotBlastMain());
    }

    static CompiledStructureSet blastFurnaceSet(CompiledSatellite... satellites) {
        return new CompiledStructureSet(StructureSetTestSupport.setId, improvedBlastFurnace(), List.of(satellites));
    }

    static void fillBlastFurnace(StructureSetTestSupport.Fixture fixture) {
        fillRoot(fixture, furnaceBounds, local -> {
            if (local.equals(furnaceHollow)) {
                return null;
            }
            if (local.equals(furnaceLeftPort) || local.equals(furnaceRightPort)) {
                return port;
            }
            return casing;
        });
    }

    static void fillPreheater(StructureSetTestSupport.Fixture fixture, NamespacedId satelliteId) {
        StructureSet.Satellite satellite = Objects.requireNonNull(fixture.plan.satellite(satelliteId));
        fill(fixture, satellite.siting(), preheaterBounds, local -> local.equals(preheaterPort) ? plug : shell);
    }

    static void fillWideMain(StructureSetTestSupport.Fixture fixture, NamespacedId satelliteId) {
        StructureSet.Satellite satellite = Objects.requireNonNull(fixture.plan.satellite(satelliteId));
        fill(fixture, satellite.siting(), wideBounds, local -> stove);
    }

    // ---------------------------------------------------------------- TEP reactor

    static Blueprint reactor() {
        CuboidSet shellCells = reactorBounds.asSet().subtract(CuboidSet.of(Cuboid.of(reactorHollow)));
        CuboidSet casingCells = shellCells.subtract(CuboidSet.of(Cuboid.of(reactorPort)));
        Bay casingBay = Bay
                .of(id("reactor_casing"), casingCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay portBay = Bay.of(
            id("reactor_controller"),
            CuboidSet.of(Cuboid.of(reactorPort)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(reactorId).bounds(reactorBounds)
                .checks(
                    List.of(
                        Check.of(
                            id("reactor_casing_check"),
                            casingBay,
                            Focuses.bay(),
                            Expectations.eachCell(Expectations.block(reactorCasing))
                        ),
                        Check.of(
                            id("reactor_controller_check"),
                            portBay,
                            Focuses.occupant(reactorPort),
                            Expectations.block(controller)
                        )
                    )
                ).build();
    }

    static Blueprint solarGenerator() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(id("solar_cell"), bounds.asSet(), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(solarId).bounds(bounds).checks(
            List.of(
                Check.of(id("solar_cell_check"), bay, Focuses.occupant(LatticePoint.origin), Expectations.block(solar))
            )
        ).build();
    }

    static SatelliteSpec solarSpec(NamespacedId id, LatticePoint at, boolean required, LinkSpec link) {
        return new SatelliteSpec(
            id,
            solarId,
            at,
            Orientation.identity,
            required,
            Value.object(),
            null,
            List.of(link),
            null
        );
    }

    static SatelliteSpec solarSpec(NamespacedId id, LatticePoint at, LinkSpec link) {
        return solarSpec(id, at, false, link);
    }

    static CompiledStructureSet reactorSet(CompiledSatellite... satellites) {
        return new CompiledStructureSet(StructureSetTestSupport.setId, reactor(), List.of(satellites));
    }

    static CompiledSatellite compiledSolar(SatelliteSpec spec) {
        return new CompiledSatellite(spec, solarGenerator());
    }

    static void fillReactor(StructureSetTestSupport.Fixture fixture) {
        fillRoot(fixture, reactorBounds, local -> {
            if (local.equals(reactorHollow)) {
                return null;
            }
            return local.equals(reactorPort) ? controller : reactorCasing;
        });
    }

    static void fillSolar(StructureSetTestSupport.Fixture fixture, NamespacedId satelliteId) {
        StructureSet.Satellite satellite = Objects.requireNonNull(fixture.plan.satellite(satelliteId));
        fixture.view.put(StructureSetTestSupport.world(satellite.siting(), LatticePoint.origin), solar);
    }

    // ---------------------------------------------------------------- AE2 subnetwork

    static Blueprint network() {
        CuboidSet casingCells = networkBounds.asSet().subtract(CuboidSet.of(Cuboid.of(networkPort)));
        Bay casingBay = Bay
                .of(id("network_casing"), casingCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay controllerBay = Bay.of(
            id("network_controller"),
            CuboidSet.of(Cuboid.of(networkPort)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(networkId).bounds(networkBounds)
                .checks(
                    List.of(
                        Check.of(
                            id("network_casing_check"),
                            casingBay,
                            Focuses.bay(),
                            Expectations.eachCell(Expectations.block(networkCasing))
                        ),
                        Check.of(
                            id("network_controller_check"),
                            controllerBay,
                            Focuses.occupant(networkPort),
                            Expectations.block(networkController)
                        )
                    )
                ).build();
    }

    static Blueprint singleCell(NamespacedId blueprintId, NamespacedId block) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(id("single_cell"), bounds.asSet(), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(blueprintId).bounds(bounds).checks(
            List.of(
                Check.of(id("single_cell_check"), bay, Focuses.occupant(LatticePoint.origin), Expectations.block(block))
            )
        ).build();
    }

    static Blueprint storageCell() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        LatticePoint occupied = LatticePoint.of(1, 0, 0);
        Bay bay = Bay.of(
            id("storage_cell"),
            CuboidSet.of(Cuboid.of(occupied)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(storageId).bounds(bounds)
                .checks(
                    List.of(Check.of(id("storage_check"), bay, Focuses.occupant(occupied), Expectations.block(storage)))
                ).build();
    }

    static SatelliteSpec logicalSatellite(
        NamespacedId id,
        NamespacedId blueprint,
        LatticePoint at,
        boolean required,
        @Nullable PresenceSpec presence,
        LinkSpec... links
    ) {
        return new SatelliteSpec(
            id,
            blueprint,
            at,
            Orientation.identity,
            required,
            Value.object(),
            presence,
            List.of(links),
            null
        );
    }

    static CompiledStructureSet networkSet(CompiledSatellite... satellites) {
        return new CompiledStructureSet(StructureSetTestSupport.setId, network(), List.of(satellites));
    }

    static void fillNetwork(StructureSetTestSupport.Fixture fixture) {
        fillRoot(fixture, networkBounds, local -> local.equals(networkPort) ? networkController : networkCasing);
    }

    static void fillAt(
        StructureSetTestSupport.Fixture fixture,
        NamespacedId satelliteId,
        LatticePoint local,
        NamespacedId block
    ) {
        StructureSet.Satellite satellite = Objects.requireNonNull(fixture.plan.satellite(satelliteId));
        fixture.view.put(StructureSetTestSupport.world(satellite.siting(), local), block);
    }

    // ---------------------------------------------------------------- controller/port envelope

    static Blueprint envelopePortRoot() {
        Bay portBay = Bay.of(
            id("envelope_port"),
            CuboidSet.of(Cuboid.of(envelopePort)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(envelopePortRootId).bounds(envelopeBounds).envelope().checks(
            List.of(
                Check.of(id("envelope_port_check"), portBay, Focuses.occupant(envelopePort), Expectations.block(port))
            )
        ).build();
    }

    static Blueprint envelopeOnlyRoot() {
        return Blueprint.builder(envelopeOnlyRootId).bounds(envelopeBounds).envelope().build();
    }

    static Blueprint bayOnlyRoot() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 3, 1, 3);
        CuboidSet bayCells = CuboidSet.of(Cuboid.of(LatticePoint.of(1, 0, 2)), Cuboid.of(LatticePoint.of(2, 0, 2)));
        Bay bay = Bay.of(id("read_bay"), bayCells, Coverage.oneSubject, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(bayOnlyRootId).bounds(bounds)
                .checks(
                    List.of(
                        Check.of(
                            id("read_bay_check"),
                            bay,
                            Focuses.occupant(LatticePoint.of(1, 0, 2)),
                            Expectations.block(port)
                        )
                    )
                ).build();
    }

    static Blueprint portPipe() {
        return singleCell(portPipeId, plug);
    }

    static CompiledSatellite compiledPortPipe(SatelliteSpec spec) {
        return new CompiledSatellite(spec, portPipe());
    }

    static CompiledStructureSet portSet(Blueprint root, CompiledSatellite... satellites) {
        return new CompiledStructureSet(StructureSetTestSupport.setId, root, List.of(satellites));
    }

    // ---------------------------------------------------------------- rotation

    static Blueprint rotationRoot() {
        CuboidSet casingCells = rotationBounds.asSet().subtract(CuboidSet.of(Cuboid.of(rotationPort)));
        Bay casingBay = Bay
                .of(id("controller_casing"), casingCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay portBay = Bay.of(
            id("controller_port"),
            CuboidSet.of(Cuboid.of(rotationPort)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(rotationRootId).bounds(rotationBounds).checks(
            List.of(
                Check.of(
                    id("controller_casing_check"),
                    casingBay,
                    Focuses.bay(),
                    Expectations.eachCell(Expectations.block(casing))
                ),
                Check.of(id("controller_port_check"), portBay, Focuses.occupant(rotationPort), Expectations.block(port))
            )
        ).build();
    }

    static Blueprint hotBlastElbow() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        Bay body = Bay.of(
            id("elbow_body"),
            CuboidSet.of(Cuboid.of(LatticePoint.origin)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Bay portBay = Bay.of(
            id("elbow_port"),
            CuboidSet.of(Cuboid.of(LatticePoint.of(1, 0, 0))),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(elbowId).bounds(bounds)
                .checks(
                    List.of(
                        Check.of(
                            id("elbow_body_check"),
                            body,
                            Focuses.occupant(LatticePoint.origin),
                            Expectations.block(elbowBody)
                        ),
                        Check.of(
                            id("elbow_port_check"),
                            portBay,
                            Focuses.occupant(LatticePoint.of(1, 0, 0)),
                            Expectations.block(elbowPlug)
                        )
                    )
                ).build();
    }

    static CompiledStructureSet rotationSet(Blueprint satellite, SatelliteSpec spec) {
        return new CompiledStructureSet(
            StructureSetTestSupport.setId,
            rotationRoot(),
            List.of(new CompiledSatellite(spec, satellite))
        );
    }

    static SatelliteSpec elbowSpec(NamespacedId id, LatticePoint at, Orientation orientation, LinkSpec link) {
        return new SatelliteSpec(id, elbowId, at, orientation, false, Value.object(), null, List.of(link), null);
    }

    static void fillRotationRoot(StructureSetTestSupport.Fixture fixture) {
        fillRoot(fixture, rotationBounds, local -> local.equals(rotationPort) ? port : casing);
    }

    static void fillElbow(StructureSetTestSupport.Fixture fixture, NamespacedId satelliteId) {
        StructureSet.Satellite satellite = Objects.requireNonNull(fixture.plan.satellite(satelliteId));
        fixture.view.put(StructureSetTestSupport.world(satellite.siting(), LatticePoint.origin), elbowBody);
        fixture.view.put(StructureSetTestSupport.world(satellite.siting(), LatticePoint.of(1, 0, 0)), elbowPlug);
    }

    // ---------------------------------------------------------------- shared helpers

    static void fillRoot(
        StructureSetTestSupport.Fixture fixture,
        Cuboid bounds,
        Function<LatticePoint, NamespacedId> blocks
    ) {
        fill(fixture, fixture.plan.rootSiting(), bounds, blocks);
    }

    static void fill(
        StructureSetTestSupport.Fixture fixture,
        Siting siting,
        Cuboid bounds,
        Function<LatticePoint, NamespacedId> blocks
    ) {
        for (LatticePoint local : cells(bounds)) {
            NamespacedId block = blocks.apply(local);
            if (block != null) {
                fixture.view.put(StructureSetTestSupport.world(siting, local), block);
            }
        }
    }

    static List<FindingCode> codes(List<Finding> findings) {
        List<FindingCode> codes = new ArrayList<>(findings.size());
        for (Finding finding : findings) {
            codes.add(finding.code());
        }
        return List.copyOf(codes);
    }

    static List<FindingCode> codes(StructureSetResult result) {
        return codes(result.setFindings());
    }

    static Map<NamespacedId, Set<LatticePoint>> claimCells(StructureSetResult result) {
        Map<NamespacedId, Set<LatticePoint>> cells = new LinkedHashMap<>();
        result.claims().forEach((id, rows) -> {
            Set<LatticePoint> points = new LinkedHashSet<>();
            for (ClaimRow row : rows) {
                points.add(row.cell().point());
            }
            cells.put(id, Set.copyOf(points));
        });
        return Map.copyOf(cells);
    }

    static List<LatticePoint> cells(Cuboid bounds) {
        List<LatticePoint> cells = new ArrayList<>();
        for (int x = bounds.min().x(); x < bounds.max().x(); x++) {
            for (int y = bounds.min().y(); y < bounds.max().y(); y++) {
                for (int z = bounds.min().z(); z < bounds.max().z(); z++) {
                    cells.add(LatticePoint.of(x, y, z));
                }
            }
        }
        return List.copyOf(cells);
    }
}
