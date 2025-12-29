package com.dia.conversion.reader.excel.poi;

import com.dia.conversion.data.RelationshipData;
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

import static com.dia.constants.FormatConstants.Excel.VZTAHY;

/**
 * RelationshipSheetProcessor - Handles the Vztahy sheet
 * <p>
 * Relationships have a unique format with domain and range columns, requiring
 * special handling for the relationship mapping logic.
 */
@Slf4j
public class RelationshipSheetProcessor extends BaseSheetProcessor<List<RelationshipData>> {

    public RelationshipSheetProcessor(ColumnMappingRegistry mappingRegistry) {
        super(mappingRegistry);
    }

    @Override
    public List<RelationshipData> process(Sheet sheet) throws ExcelReadingException {
        log.info("Processing Relationship sheet: sheetName={}", sheet.getSheetName());
        List<RelationshipData> relationships = new ArrayList<>();
        ColumnMapping<RelationshipData> mapping = mappingRegistry.getMapping(VZTAHY);

        Row headerRow = findHeaderRow(sheet);
        if (headerRow == null) {
            log.error("No header row found in Relationship sheet: sheetName={}", sheet.getSheetName());
            throw new ExcelReadingException("No header row found in Vztahy sheet.");
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
            RelationshipData relationshipData = processDataRow(row, columnMap, mapping);
            relationships.add(relationshipData);
            log.debug("Relationship data extracted from row={}: domain={}, name={}, range={}",
                    rowIndex, relationshipData.getDomain(), relationshipData.getName(), relationshipData.getRange());
        }

        log.info("Relationship sheet processing completed: sheetName={}, totalRows={}, processedRows={}, validRelationships={}, emptyRows={}",
                sheet.getSheetName(), sheet.getLastRowNum() - headerRow.getRowNum(), processedRows, relationships.size(), emptyRows);
        return relationships;
    }

    private Row findHeaderRow(Sheet sheet) {
        Set<String> expectedColumns = Set.of("Název", "Subjekt nebo objekt práva");
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

    private RelationshipData processDataRow(Row row, Map<String, Integer> columnMap,
                                            ColumnMapping<RelationshipData> mapping) {
        RelationshipData relationshipData = new RelationshipData();

        relationshipData.setDomain(getCellValueAsString(row.getCell(0)));
        relationshipData.setName(getCellValueAsString(row.getCell(1)));
        relationshipData.setRange(getCellValueAsString(row.getCell(2)));

        for (String columnName : mapping.getColumnNames()) {
            Integer columnIndex = findColumnIndex(columnName, columnMap);
            if (columnIndex != null && columnIndex > 2) {
                Cell cell = row.getCell(columnIndex);
                String value = getCellValueAsString(cell);

                PropertySetter<RelationshipData> setter = mapping.getColumnMappingFlexible(columnName);
                if (setter != null && !value.isEmpty()) {
                    setter.setProperty(relationshipData, value);
                }
            }
        }

        return relationshipData;
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
