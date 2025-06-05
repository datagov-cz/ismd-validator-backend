package com.dia.converter.excel.reader.poi;

import com.dia.converter.excel.mapper.ColumnMappingRegistry;
import com.dia.exceptions.ExcelReadingException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for sheet processors - implements common row processing logic
 * <p>
 * This abstract class provides the foundation for processing different types of sheets
 * while allowing each sheet type to implement its own specific parsing logic.
 */
public abstract class BaseSheetProcessor<T> {
    protected final ColumnMappingRegistry mappingRegistry;

    protected BaseSheetProcessor(ColumnMappingRegistry mappingRegistry) {
        this.mappingRegistry = mappingRegistry;
    }

    /**
     * Template method for processing sheets - subclasses implement specific logic
     */
    public abstract T process(Sheet sheet) throws ExcelReadingException;

    /**
     * Safely extracts cell value as string, handling null cells and different data types
     */
    protected String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numValue = cell.getNumericCellValue();
                    if (numValue == Math.floor(numValue)) {
                        return String.valueOf((long) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * Checks if a row is effectively empty (all cells are null or blank)
     */
    protected boolean isRowEmpty(Row row) {
        if (row == null) return true;

        for (Cell cell : row) {
            if (cell != null && !getCellValueAsString(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a map of column names to their indices for efficient lookup
     */
    protected Map<String, Integer> createColumnIndexMap(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();

        if (headerRow != null) {
            for (Cell cell : headerRow) {
                String columnName = getCellValueAsString(cell);
                if (!columnName.isEmpty()) {
                    columnMap.put(columnName, cell.getColumnIndex());
                }
            }
        }

        return columnMap;
    }
}
