package com.dia.conversion.reader.excel.poi;

import com.dia.conversion.data.PropertyData;
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

import static com.dia.constants.FormatConstants.Excel.VLASTNOSTI;

/**
 * PropertySheetProcessor - Handles the Vlastnosti sheet.
 * <p>
 * This follows the same pattern as ClassSheetProcessor but handles the specific
 * structure and requirements of the Vlastnosti sheet.
 */
@Slf4j
public class PropertySheetProcessor extends BaseSheetProcessor<List<PropertyData>> {

    public PropertySheetProcessor(ColumnMappingRegistry mappingRegistry) {
        super(mappingRegistry);
    }

    @Override
    public List<PropertyData> process(Sheet sheet) throws ExcelReadingException {
        log.info("Processing Property sheet: sheetName={}", sheet.getSheetName());
        List<PropertyData> properties = new ArrayList<>();
        ColumnMapping<PropertyData> mapping = mappingRegistry.getMapping(VLASTNOSTI);

        Row headerRow = findHeaderRow(sheet);
        if (headerRow == null) {
            log.error("No header row found in Property sheet: sheetName={}", sheet.getSheetName());
            throw new ExcelReadingException("No header row found in Vlastnosti sheet.");
        }
        log.debug("Header row found at row={}", headerRow.getRowNum());

        Map<String, Integer> columnMap = createColumnIndexMap(headerRow);

        int processedRows = 0;
        int emptyRows = 0;
        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) {
                emptyRows++;
                continue;
            }

            processedRows++;
            PropertyData propertyData = processDataRow(row, columnMap, mapping);
            properties.add(propertyData);
            log.debug("Property data extracted from row={}: name={}", rowIndex, propertyData.getName());
        }

        log.info("Property sheet processing completed: sheetName={}, totalRows={}, processedRows={}, validProperties={}, emptyRows={}",
                sheet.getSheetName(), sheet.getLastRowNum() - headerRow.getRowNum(), processedRows, properties.size(), emptyRows);
        return properties;
    }

    private Row findHeaderRow(Sheet sheet) {
        Set<String> expectedColumns = Set.of("Název", "Subjekt nebo objekt práva", "Popis");
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

    private PropertyData processDataRow(Row row, Map<String, Integer> columnMap,
                                        ColumnMapping<PropertyData> mapping) {
        PropertyData propertyData = new PropertyData();

        for (String columnName : mapping.getColumnNames()) {
            Integer columnIndex = findColumnIndex(columnName, columnMap);
            if (columnIndex != null) {
                Cell cell = row.getCell(columnIndex);
                String value = getCellValueAsString(cell);

                PropertySetter<PropertyData> setter = mapping.getColumnMappingFlexible(columnName);
                if (setter != null && !value.isEmpty()) {
                    setter.setProperty(propertyData, value);
                }
            }
        }

        return propertyData;
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
