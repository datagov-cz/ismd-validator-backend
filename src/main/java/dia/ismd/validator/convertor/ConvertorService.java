package dia.ismd.validator.convertor;

import dia.ismd.common.exception.ConvertionException;
import dia.ismd.common.exception.FileParsingException;
import org.springframework.boot.configurationprocessor.json.JSONException;

public interface ConvertorService {
    void parseArchiFromString(String value) throws FileParsingException;
    void convertArchi() throws ConvertionException;
    String exportArchiToJson() throws JSONException;
    String exportArchiToTurtle();
}
