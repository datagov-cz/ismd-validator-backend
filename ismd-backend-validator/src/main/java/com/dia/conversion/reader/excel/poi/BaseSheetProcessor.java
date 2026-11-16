package com.dia.conversion.reader.excel.poi;

import com.dia.conversion.reader.excel.mapper.ColumnMappingRegistry;
import com.dia.exception.ExcelReadingException;
import java.util.LinkedHashMap;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public abstract class BaseSheetProcessor<T> {
    protected final ColumnMappingRegistry mappingRegistry;

    protected BaseSheetProcessor(ColumnMappingRegistry mappingRegistry) {
        this.mappingRegistry = mappingRegistry;
    }

    public abstract T process(Sheet sheet) throws ExcelReadingException;

    protected String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        try {
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
                    log.debug("Blank or unknown cell type at row={}, column={}",
                            cell.getRowIndex(), cell.getColumnIndex());
                    return "";
            }
        } catch (Exception e) {
            log.warn("Failed to extract cell value at row={}, column={}: {}",
                    cell.getRowIndex(), cell.getColumnIndex(), e.getMessage());
            return "";
        }
    }

    protected boolean isRowEmpty(Row row) {
        if (row == null) return true;

        for (Cell cell : row) {
            if (cell != null && !getCellValueAsString(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    protected Map<String, Integer> createColumnIndexMap(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();

        if (headerRow != null) {
            log.debug("Creating column index map from header row at row={}", headerRow.getRowNum());
            for (Cell cell : headerRow) {
                String columnName = getCellValueAsString(cell);
                if (!columnName.isEmpty()) {
                    columnMap.put(columnName, cell.getColumnIndex());
                    log.debug("Mapped column '{}' to index {}", columnName, cell.getColumnIndex());
                }
            }
            log.debug("Column mapping created: {} columns mapped", columnMap.size());
        } else {
            log.warn("Header row is null, returning empty column map");
        }

        return columnMap;
    }

    /**
     * Header columns that no mapping reads (matched the way the processors match: exactly, or one
     * name containing the other). Their values are not converted.
     */
    protected Map<String, Integer> unmappedColumns(Map<String, Integer> columnMap, Set<String> mappingKeys) {
        Map<String, Integer> unmapped = new LinkedHashMap<>();
        columnMap.forEach((column, index) -> {
            boolean mapped = mappingKeys.stream()
                    .anyMatch(key -> key.equals(column) || key.contains(column) || column.contains(key));
            if (!mapped) {
                unmapped.put(column, index);
            }
        });
        return unmapped;
    }

    /** Counts, per unmapped column, the rows that have a value in it. */
    protected void countUnmappedValues(Row row, Map<String, Integer> unmapped, Map<String, Integer> counts) {
        unmapped.forEach((column, index) -> {
            if (!getCellValueAsString(row.getCell(index)).isEmpty()) {
                counts.merge(column, 1, Integer::sum);
            }
        });
    }

    /** Warns about unmapped columns that hold data: those values are not converted. */
    protected void warnUnmappedValues(Sheet sheet, Map<String, Integer> counts) {
        counts.forEach((column, rows) -> log.warn(
                "Sheet '{}': column '{}' is not a known template column; its values in {} row(s) are ignored",
                sheet.getSheetName(), column, rows));
    }
}
