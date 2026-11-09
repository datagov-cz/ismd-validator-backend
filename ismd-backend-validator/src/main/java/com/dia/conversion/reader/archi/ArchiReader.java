package com.dia.conversion.reader.archi;

import com.dia.conversion.data.OntologyData;
import com.dia.exception.FileParsingException;
import org.springframework.stereotype.Component;

/**
 * Reads Archi XML into {@link OntologyData}. A shared, stateless bean: every read gets its own
 * {@link ArchiParser}, so concurrent requests never share parsing state.
 */
@Component
public class ArchiReader {

    public OntologyData readArchiFromString(String content) throws FileParsingException {
        return new ArchiParser().readArchiFromString(content);
    }
}
