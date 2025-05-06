package dia.ismd.validator.convertor;

import org.springframework.boot.configurationprocessor.json.JSONException;

public interface ConvertorService {
    void parseArchiFromString(String value) throws Exception;
    void convertArchi();
    String exportArchiToJson() throws JSONException;
    String exportArchiToTurtle();
}
