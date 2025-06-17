package com.dia.converter.reader.excel.poi;

import com.dia.converter.data.RelationshipData;
import com.dia.converter.reader.excel.mapper.ColumnMapping;
import com.dia.converter.reader.excel.mapper.ColumnMappingRegistry;
import com.dia.converter.reader.excel.mapper.PropertySetter;
import com.dia.exceptions.ExcelReadingException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dia.constants.ExcelOntologyConstants.VZTAHY;

/**
 * RelationshipSheetProcessor - Handles the Vztahy sheet
 * <p>
 * Relationships have a unique format with domain and range columns, requiring
 * special handling for the relationship mapping logic.
 */
public class RelationshipSheetProcessor extends BaseSheetProcessor<List<RelationshipData>> {

    public RelationshipSheetProcessor(ColumnMappingRegistry mappingRegistry) {
        super(mappingRegistry);
    }

    @Override
    public List<RelationshipData> process(Sheet sheet) throws ExcelReadingException {
        List<RelationshipData> relationships = new ArrayList<>();
        ColumnMapping<RelationshipData> mapping = mappingRegistry.getMapping(VZTAHY);

        Row headerRow = findHeaderRow(sheet);
        if (headerRow == null) {
            throw new ExcelReadingException("No header row found in Vztahy sheet.");
        }

        Map<String, Integer> columnMap = createColumnIndexMap(headerRow);

        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) continue;

            RelationshipData relationshipData = processDataRow(row, columnMap, mapping);
            if (relationshipData.hasValidData()) {
                relationships.add(relationshipData);
            }
        }

        return relationships;
    }

    private Row findHeaderRow(Sheet sheet) {
        Set<String> expectedColumns = Set.of("Název", "Subjekt nebo objekt práva");

        for (Row row : sheet) {
            if (isRowEmpty(row)) continue;

            Map<String, Integer> columnMap = createColumnIndexMap(row);
            if (columnMap.keySet().containsAll(expectedColumns)) {
                return row;
            }
        }

        return null;
    }

    private RelationshipData processDataRow(Row row, Map<String, Integer> columnMap,
                                            ColumnMapping<RelationshipData> mapping) {
        RelationshipData relationshipData = new RelationshipData();

        relationshipData.setDomain(getCellValueAsString(row.getCell(0)));
        relationshipData.setName(getCellValueAsString(row.getCell(1)));
        relationshipData.setRange(getCellValueAsString(row.getCell(2)));

        for (String columnName : mapping.getColumnNames()) {
            Integer columnIndex = columnMap.get(columnName);
            if (columnIndex != null && columnIndex > 2) {
                Cell cell = row.getCell(columnIndex);
                String value = getCellValueAsString(cell);

                PropertySetter<RelationshipData> setter = mapping.getColumnMapping(columnName);
                if (setter != null && !value.isEmpty()) {
                    setter.setProperty(relationshipData, value);
                }
            }
        }

        return relationshipData;
    }
}
