package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import org.jspecify.annotations.Nullable;

import java.util.List;
/** Text to document; the host owns the format. */
public interface DocumentReader {

    @Nullable Document read(NamespacedId source, String text, List<Finding> errors);
}
