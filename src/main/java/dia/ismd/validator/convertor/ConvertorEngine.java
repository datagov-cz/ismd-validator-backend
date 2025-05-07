package dia.ismd.validator.convertor;

import dia.ismd.common.exception.ConvertionException;
import dia.ismd.common.exception.FileParsingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class ConvertorEngine {

    private final ArchiConvertor archiConvertor;

    public void parseArchiFromString(String content) throws FileParsingException {
        archiConvertor.parseFromString(content);
    }

    public void convertArchi() throws ConvertionException {
        archiConvertor.convert();
    }

    public String exportToJson() throws JSONException {
        return archiConvertor.exportToJson();
    }

    public String exportToTurtle() {
        return archiConvertor.exportToTurtle();
    }
}
