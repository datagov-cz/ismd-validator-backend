package com.dia.converter.reader.excel.poi;

import com.dia.converter.data.ClassData;
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

import static com.dia.constants.ExcelOntologyConstants.SUBJEKTY_OBJEKTY_PRAVA;

/**
 * ClassSheetProcessor - Handles tabular sheets for Subjekty a objekty práva.
 * <p>
 * This processor handles sheets with header rows and data rows, mapping each
 * column to the appropriate property in the data object.
 */
public class ClassSheetProcessor extends BaseSheetProcessor<List<ClassData>> {

    public ClassSheetProcessor(ColumnMappingRegistry mappingRegistry) {
        super(mappingRegistry);
    }

    @Override
    public List<ClassData> process(Sheet sheet) throws ExcelReadingException {
        List<ClassData> classes = new ArrayList<>();
        ColumnMapping<ClassData> mapping = mappingRegistry.getMapping(SUBJEKTY_OBJEKTY_PRAVA);

        Row headerRow = findHeaderRow(sheet);
        if (headerRow == null) {
            throw new ExcelReadingException("No header row found in Subjekty a objekty práva sheet");
        }

        Map<String, Integer> columnMap = createColumnIndexMap(headerRow);

        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) continue;

            ClassData classData = processDataRow(row, columnMap, mapping);
            if (classData.hasValidData()) {
                classes.add(classData);
            }
        }

        return classes;
    }

    private Row findHeaderRow(Sheet sheet) {
        Set<String> expectedColumns = Set.of("Název", "Typ", "Popis", "Definice");

        for (Row row : sheet) {
            if (isRowEmpty(row)) continue;

            Map<String, Integer> columnMap = createColumnIndexMap(row);
            if (columnMap.keySet().containsAll(expectedColumns)) {
                return row;
            }
        }

        return null;
    }

    private ClassData processDataRow(Row row, Map<String, Integer> columnMap,
                                     ColumnMapping<ClassData> mapping) {
        ClassData classData = new ClassData();

        for (String columnName : mapping.getColumnNames()) {
            Integer columnIndex = columnMap.get(columnName);
            if (columnIndex != null) {
                Cell cell = row.getCell(columnIndex);
                String value = getCellValueAsString(cell);

                PropertySetter<ClassData> setter = mapping.getColumnMapping(columnName);
                if (setter != null && !value.isEmpty()) {
                    setter.setProperty(classData, value);
                }
            }
        }

        return classData;
    }
}
