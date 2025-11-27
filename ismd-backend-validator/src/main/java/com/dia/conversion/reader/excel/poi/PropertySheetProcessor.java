package com.dia.conversion.reader.excel.poi;

import com.dia.conversion.data.PropertyData;
import com.dia.conversion.reader.excel.mapper.ColumnMapping;
import com.dia.conversion.reader.excel.mapper.ColumnMappingRegistry;
import com.dia.conversion.reader.excel.mapper.PropertySetter;
import com.dia.exceptions.ExcelReadingException;
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
public class PropertySheetProcessor extends BaseSheetProcessor<List<PropertyData>> {

    public PropertySheetProcessor(ColumnMappingRegistry mappingRegistry) {
        super(mappingRegistry);
    }

    @Override
    public List<PropertyData> process(Sheet sheet) throws ExcelReadingException {
        List<PropertyData> properties = new ArrayList<>();
        ColumnMapping<PropertyData> mapping = mappingRegistry.getMapping(VLASTNOSTI);

        Row headerRow = findHeaderRow(sheet);
        if (headerRow == null) {
            throw new ExcelReadingException("No header row found in Vlastnosti sheet.");
        }

        Map<String, Integer> columnMap = createColumnIndexMap(headerRow);

        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) continue;

            PropertyData propertyData = processDataRow(row, columnMap, mapping);
            properties.add(propertyData);
        }

        return properties;
    }

    private Row findHeaderRow(Sheet sheet) {
        Set<String> expectedColumns = Set.of("Název", "Subjekt nebo objekt práva", "Popis");

        for (Row row : sheet) {
            if (isRowEmpty(row)) continue;

            Map<String, Integer> columnMap = createColumnIndexMap(row);
            if (columnMap.keySet().containsAll(expectedColumns)) {
                return row;
            }
        }

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
                return entry.getValue();
            }
        }

        return null;
    }
}
