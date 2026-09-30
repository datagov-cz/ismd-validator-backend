package com.dia.conversion.reader.excel.poi;

import com.dia.conversion.data.ClassData;
import com.dia.conversion.reader.excel.mapper.ColumnMapping;
import com.dia.conversion.reader.excel.mapper.ColumnMappingRegistry;
import com.dia.conversion.reader.excel.mapper.PropertySetter;
import com.dia.exceptions.ExcelReadingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dia.constants.FormatConstants.Excel.SUBJEKTY_OBJEKTY_PRAVA;

/**
 * ClassSheetProcessor - Handles tabular sheets for Subjekty a objekty práva.
 * <p>
 * This processor handles sheets with header rows and data rows, mapping each
 * column to the appropriate property in the data object.
 */
@Slf4j
public class ClassSheetProcessor extends BaseSheetProcessor<List<ClassData>> {

    public ClassSheetProcessor(ColumnMappingRegistry mappingRegistry) {
        super(mappingRegistry);
    }

    @Override
    public List<ClassData> process(Sheet sheet) throws ExcelReadingException {
        log.info("Processing Class sheet: sheetName={}", sheet.getSheetName());
        List<ClassData> classes = new ArrayList<>();
        ColumnMapping<ClassData> mapping = mappingRegistry.getMapping(SUBJEKTY_OBJEKTY_PRAVA);

        Row headerRow = findHeaderRow(sheet);
        if (headerRow == null) {
            log.error("No header row found in Class sheet: sheetName={}", sheet.getSheetName());
            throw new ExcelReadingException("No header row found in Subjekty a objekty práva sheet");
        }
        log.debug("Header row found at row={}", headerRow.getRowNum());

        Map<String, Integer> columnMap = createColumnIndexMap(headerRow);

        int processedRows = 0;
        int validRows = 0;
        int emptyRows = 0;
        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) {
                emptyRows++;
                continue;
            }

            processedRows++;
            ClassData classData = processDataRow(row, columnMap, mapping);
            if (classData.hasValidData()) {
                classes.add(classData);
                validRows++;
                log.debug("Valid class data extracted from row={}: name={}", rowIndex, classData.getName());
            } else {
                log.warn("Row {} contains no valid class data", rowIndex);
            }
        }

        log.info("Class sheet processing completed: sheetName={}, totalRows={}, processedRows={}, validClasses={}, emptyRows={}",
                sheet.getSheetName(), sheet.getLastRowNum() - headerRow.getRowNum(), processedRows, validRows, emptyRows);
        return classes;
    }

    private Row findHeaderRow(Sheet sheet) {
        Set<String> expectedColumns = Set.of("Název", "Typ", "Popis", "Definice");
        log.debug("Searching for header row with expected columns: {}", expectedColumns);

        for (Row row : sheet) {
            if (isRowEmpty(row)) continue;

            Map<String, Integer> columnMap = createColumnIndexMap(row);
            if (columnMap.keySet().containsAll(expectedColumns)) {
                log.debug("Header row found at row={} with columns: {}", row.getRowNum(), columnMap.keySet());
                return row;
            } else {
                log.debug("Row {} does not contain all expected columns, found: {}", row.getRowNum(), columnMap.keySet());
            }
        }

        log.warn("No header row found with expected columns in sheet: {}", sheet.getSheetName());
        return null;
    }

    private ClassData processDataRow(Row row, Map<String, Integer> columnMap,
                                     ColumnMapping<ClassData> mapping) {
        ClassData classData = new ClassData();

        for (String columnName : mapping.getColumnNames()) {
            Integer columnIndex = findColumnIndex(columnName, columnMap);
            if (columnIndex != null) {
                Cell cell = row.getCell(columnIndex);
                String value = getCellValueAsString(cell);

                PropertySetter<ClassData> setter = mapping.getColumnMappingFlexible(columnName);
                if (setter != null && !value.isEmpty()) {
                    setter.setProperty(classData, value);
                }
            }
        }

        return classData;
    }

    private Integer findColumnIndex(String mappingKey, Map<String, Integer> columnMap) {
        Integer index = columnMap.get(mappingKey);
        if (index != null) {
            return index;
        }

        for (Map.Entry<String, Integer> entry : columnMap.entrySet()) {
            String excelColumnName = entry.getKey();
            if (mappingKey.contains(excelColumnName) || excelColumnName.contains(mappingKey)) {
                log.debug("Found partial column match: mappingKey='{}' matches excelColumn='{}'", mappingKey, excelColumnName);
                return entry.getValue();
            }
        }

        log.debug("No column mapping found for key: '{}'", mappingKey);
        return null;
    }
}
