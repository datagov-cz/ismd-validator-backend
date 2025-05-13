package dia.ismd.validator.convertor;

import dia.ismd.common.exceptions.ConversionException;
import dia.ismd.common.exceptions.FileParsingException;
import dia.ismd.common.exceptions.JsonExportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class ConvertorEngine {

    private final ArchiConvertor archiConvertor;

    public void parseArchiFromString(String content) throws FileParsingException {
        archiConvertor.parseFromString(content);
    }

    public void convertArchi() throws ConversionException {
        archiConvertor.convert();
    }

    public String exportToJson() throws JsonExportException {
        return archiConvertor.exportToJson();
    }

    public String exportToTurtle() {
        return archiConvertor.exportToTurtle();
    }
}
