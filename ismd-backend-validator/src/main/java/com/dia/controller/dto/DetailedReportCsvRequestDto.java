package com.dia.controller.dto;

import com.dia.validation.data.DetailedValidationReportDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** JSON body for the CSV report download: { "detailedReport": { ... } }. */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class DetailedReportCsvRequestDto {
    private DetailedValidationReportDto detailedReport;
}
