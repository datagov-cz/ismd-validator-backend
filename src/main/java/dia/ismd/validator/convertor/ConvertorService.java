package dia.ismd.validator.convertor;

import dia.ismd.common.exceptions.ConversionException;
import dia.ismd.common.exceptions.FileParsingException;
import dia.ismd.common.exceptions.JsonExportException;
import dia.ismd.common.exceptions.TurtleExportException;

public interface ConvertorService {
    void parseArchiFromString(String value) throws FileParsingException;

    void convertArchi(Boolean removeInvalidSources) throws ConversionException;

    String exportArchiToJson() throws JsonExportException;

    String exportArchiToTurtle() throws TurtleExportException;
}
