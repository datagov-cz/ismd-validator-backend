package com.dia.converter.excel.reader.poi;

import com.dia.converter.excel.data.VocabularyMetadata;
import com.dia.converter.excel.mapper.ColumnMapping;
import com.dia.converter.excel.mapper.ColumnMappingRegistry;
import com.dia.converter.excel.mapper.PropertySetter;
import com.dia.exceptions.ExcelReadingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import static com.dia.constants.ExcelOntologyConstants.SLOVNIK;

/**
 * VocabularySheetProcessor - Handles key-value pair format sheets
 * <p>
 * The vocabulary sheet has a different format than other sheets - it contains
 * key-value pairs rather than tabular data. This processor handles that specific format.
 */
@Slf4j
public class VocabularySheetProcessor extends BaseSheetProcessor<VocabularyMetadata> {

    public VocabularySheetProcessor(ColumnMappingRegistry mappingRegistry) {
        super(mappingRegistry);
    }

    @Override
    public VocabularyMetadata process(Sheet sheet) throws ExcelReadingException {
        VocabularyMetadata metadata = new VocabularyMetadata();

        ColumnMapping<VocabularyMetadata> mapping = mappingRegistry.getMapping(SLOVNIK);

        if (mapping == null) {
            log.error("Mapping for vocabulary sheet Slovník not found. ");
            throw new ExcelReadingException(
                    "Mapping for vocabulary sheet Slovník not found. " +
                            "This suggests an initialization problem in the mapping registry. " +
                            "Please verify that setupVocabularyMappings() is being called properly."
            );
        }

        log.debug("Successfully found mapping for Slovník sheet.");

        for (Row row : sheet) {
            if (isRowEmpty(row)) continue;

            Cell keyCell = row.getCell(0);
            Cell valueCell = row.getCell(1);

            if (keyCell != null && valueCell != null) {
                String key = getCellValueAsString(keyCell);
                String value = getCellValueAsString(valueCell);

                log.debug("Processing key-value pair: '{}' = '{}'", key, value);

                PropertySetter<VocabularyMetadata> setter = mapping.getKeyValueMapping(key);
                if (setter != null && !value.isEmpty()) {
                    setter.setProperty(metadata, value);
                    log.debug("Successfully set property for key: {}", key);
                } else if (setter == null) {
                    log.debug("No setter found for key: '{}'", key);
                    log.debug("Available keys in mapping: {}", mapping.getKeyNames());
                }
            }
        }

        return metadata;
    }
}

