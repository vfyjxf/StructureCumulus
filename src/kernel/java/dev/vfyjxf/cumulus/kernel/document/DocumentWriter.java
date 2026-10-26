package dev.vfyjxf.cumulus.kernel.document;

/** Document to text; the host owns the format. */
public interface DocumentWriter {

    String write(Document document);
}
