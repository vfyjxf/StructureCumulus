package dev.vfyjxf.cumulus.mc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.PresenceSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.document.StructureSetSpec;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/** Writes the canonical v1 structure-set text. */
@NullMarked
public final class StructureSetWriter {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping()
            .create();

    public String write(StructureSetDocument document) {
        Objects.requireNonNull(document, "document");
        if (document.schemaVersion() != StructureSetDocument.currentVersion) {
            throw new IllegalStateException(
                "the writer emits schema version " + StructureSetDocument.currentVersion + ", not "
                        + document.schemaVersion()
            );
        }
        StructureSetSpec spec = document.spec();
        JsonObject out = new JsonObject();
        out.addProperty("schemaVersion", document.schemaVersion());
        out.addProperty("kind", "structure_set");
        out.addProperty("id", JsonVocabulary.idText(spec.id()));
        out.addProperty("root", JsonVocabulary.idText(spec.root()));
        JsonArray satellites = new JsonArray();
        for (SatelliteSpec satellite : spec.satellites()) {
            satellites.add(satellite(satellite));
        }
        out.add("satellites", satellites);
        JsonValues.addExtensions(out, document.extensions());
        return gson.toJson(out);
    }

    private static JsonObject satellite(SatelliteSpec satellite) {
        JsonObject json = new JsonObject();
        json.addProperty("id", JsonVocabulary.idText(satellite.id()));
        json.addProperty("blueprint", JsonVocabulary.idText(satellite.blueprint()));
        json.add("at", JsonValues.point(satellite.at()));
        json.addProperty("orientation", JsonVocabulary.orientationName(satellite.orientation()));
        json.addProperty("required", satellite.required());
        JsonValues.addArguments(json, satellite.arguments());
        if (satellite.presence() != null) {
            json.add("presence", presence(satellite.presence()));
        }
        if (!satellite.links().isEmpty()) {
            JsonArray links = new JsonArray();
            for (LinkSpec link : satellite.links()) {
                links.add(link(link));
            }
            json.add("links", links);
        }
        return json;
    }

    private static JsonObject presence(PresenceSpec presence) {
        JsonObject json = new JsonObject();
        if (presence.cells().boxCount() == 1) {
            Cuboid box = presence.cells().decomposition().get(0);
            if (box.sizeX() == 1 && box.sizeY() == 1 && box.sizeZ() == 1) {
                json.add("cell", JsonValues.point(box.min()));
                return json;
            }
        }
        json.add("boxes", JsonValues.boxSet(presence.cells()));
        return json;
    }

    private static JsonObject link(LinkSpec link) {
        JsonObject json = new JsonObject();
        json.addProperty("mode", link.mode().name());
        if (link.mode() != LinkMode.none) {
            json.add("parent_cell", JsonValues.point(Objects.requireNonNull(link.parentCell(), "parentCell")));
            json.add("satellite_cell", JsonValues.point(Objects.requireNonNull(link.satelliteCell(), "satelliteCell")));
        }
        if (link.mode() == LinkMode.range) {
            json.addProperty("max_distance", link.maxDistance());
            json.addProperty("metric", link.metric().name());
        }
        return json;
    }
}
