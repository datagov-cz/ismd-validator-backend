package com.dia.converter.excel.reader.poi;

import com.dia.exceptions.ExcelReadingException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;

public class WorkbookProcessor {

    public Workbook openWorkbook(InputStream inputStream) throws IOException {
        return new XSSFWorkbook(inputStream);
    }

    public Sheet getSheet(Workbook workbook, String sheetName) throws ExcelReadingException {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new ExcelReadingException("Required sheet not found: " + sheetName);
        }
        return sheet;
    }

    public boolean hasSheet(Workbook workbook, String sheetName) {
        return workbook.getSheet(sheetName) != null;
    }
}
