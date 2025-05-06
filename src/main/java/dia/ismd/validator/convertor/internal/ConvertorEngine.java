package dia.ismd.validator.convertor.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConvertorEngine {

    private ArchiConvertor archiConvertor;

    private JSONExporter jsonExporter;
    private TurtleExporter turtleExporter;

    public void parseArchiFromString(String content) {
        archiConvertor.parseFromString(content);
    }

    public void convertArchi() {
        archiConvertor.convert();
    }

    public String exportToJson() throws JSONException {
        return jsonExporter.exportToJson();
    }

    public String exportToTurtle() {
        return turtleExporter.exportToTurtle();
    }
}
