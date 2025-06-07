package com.dia.converter.excel.reader.poi;

import com.dia.exceptions.ExcelReadingException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;

public class WorkbookProcessor {

    /**
     * Opens a workbook with optimized settings for reading
     * Uses streaming when possible to handle large files efficiently
     */
    public Workbook openWorkbook(InputStream inputStream) throws IOException {
        return new XSSFWorkbook(inputStream);
    }

    /**
     * Safely retrieves a sheet by name with clear error handling
     */
    public Sheet getSheet(Workbook workbook, String sheetName) throws ExcelReadingException {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new ExcelReadingException("Required sheet not found: " + sheetName);
        }
        return sheet;
    }

    /**
     * Checks if a sheet exists without throwing exceptions
     */
    public boolean hasSheet(Workbook workbook, String sheetName) {
        return workbook.getSheet(sheetName) != null;
    }
}
