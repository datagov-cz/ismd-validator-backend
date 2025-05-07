package dia.ismd.validator.convertor;

import dia.ismd.common.exception.FileParsingException;
import lombok.RequiredArgsConstructor;
import org.apache.jena.ontology.ConversionException;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConvertorServiceImpl implements ConvertorService {

    private final ConvertorEngine convertorEngine;

    @Override
    public void parseArchiFromString(String content) throws FileParsingException {
        convertorEngine.parseArchiFromString(content);
    }

    @Override
    public void convertArchi() throws ConversionException {
        convertorEngine.convertArchi();
    }

    @Override
    public String exportArchiToJson() throws JSONException {
        return convertorEngine.exportToJson();
    }

    @Override
    public String exportArchiToTurtle() {
        return convertorEngine.exportToTurtle();
    }
}
