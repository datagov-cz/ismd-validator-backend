package dia.ismd.validator.convertor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConvertorEngine {

    private final ArchiConvertor archiConvertor;

    public void parseArchiFromString(String content) {
        try {
            archiConvertor.parseFromString(content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void convertArchi() {
        archiConvertor.convert();
    }

    public String exportToJson() throws JSONException {
        return archiConvertor.exportToJson();
    }

    public String exportToTurtle() {
        return archiConvertor.exportToTurtle();
    }
}
