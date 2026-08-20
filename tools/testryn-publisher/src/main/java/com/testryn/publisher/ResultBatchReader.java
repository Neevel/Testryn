package com.testryn.publisher;

import java.io.IOException;
import java.io.InputStream;

/**
 * The seam between a concrete input format and the publisher core (Abschnitt 17).
 * {@link JsonResultBatchReader} is the only implementation today; a later JUnit-XML
 * reader (explicitly NOT built in this block, Abschnitt 33) would implement this same
 * interface and plug into {@link TestrynApiClient} / {@link PublisherMain} unchanged.
 */
public interface ResultBatchReader {

    ResultBatch read(InputStream input) throws IOException;
}
