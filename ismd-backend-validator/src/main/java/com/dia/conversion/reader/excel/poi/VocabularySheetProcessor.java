package com.dia.conversion.reader.excel.poi;

import com.dia.conversion.data.VocabularyMetadata;
import com.dia.conversion.reader.excel.mapper.ColumnMapping;
import com.dia.conversion.reader.excel.mapper.ColumnMappingRegistry;
import com.dia.conversion.reader.excel.mapper.PropertySetter;
import com.dia.exceptions.ExcelReadingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import static com.dia.constants.FormatConstants.Excel.SLOVNIK;

/**
 * VocabularySheetProcessor - Handles key-value pair format sheets
 * <p>
 * The Slovník sheet has a different format than other sheets - it contains
 * key-value pairs rather than tabular data. This processor handles that specific format.
 */
@Slf4j
public class VocabularySheetProcessor extends BaseSheetProcessor<VocabularyMetadata> {

    public VocabularySheetProcessor(ColumnMappingRegistry mappingRegistry) {
        super(mappingRegistry);
    }

    @Override
    public VocabularyMetadata process(Sheet sheet) throws ExcelReadingException {
        log.info("Processing Vocabulary metadata sheet: sheetName={}", sheet.getSheetName());
        VocabularyMetadata metadata = new VocabularyMetadata();

        ColumnMapping<VocabularyMetadata> mapping = mappingRegistry.getMapping(SLOVNIK);

        if (mapping == null) {
            log.error("Mapping for vocabulary sheet Slovník not found");
            throw new ExcelReadingException(
                    "Mapping for vocabulary sheet Slovník not found. "
            );
        }

        log.debug("Successfully found mapping for Slovník sheet");

        int processedPairs = 0;
        int emptyRows = 0;
        int unmappedKeys = 0;

        for (Row row : sheet) {
            if (isRowEmpty(row)) {
                emptyRows++;
                continue;
            }

            Cell keyCell = row.getCell(0);
            Cell valueCell = row.getCell(1);

            if (keyCell != null && valueCell != null) {
                String key = getCellValueAsString(keyCell);
                String value = getCellValueAsString(valueCell);

                log.debug("Processing key-value pair: '{}' = '{}'", key, value);

                PropertySetter<VocabularyMetadata> setter = mapping.getKeyValueMapping(key);
                if (setter != null && !value.isEmpty()) {
                    setter.setProperty(metadata, value);
                    processedPairs++;
                    log.debug("Successfully set property for key: {}", key);
                } else if (setter == null && !key.isEmpty()) {
                    unmappedKeys++;
                    log.debug("No setter found for key: '{}'", key);
                }
            }
        }

        log.info("Vocabulary sheet processing completed: sheetName={}, processedPairs={}, unmappedKeys={}, emptyRows={}",
                sheet.getSheetName(), processedPairs, unmappedKeys, emptyRows);
        return metadata;
    }
}

