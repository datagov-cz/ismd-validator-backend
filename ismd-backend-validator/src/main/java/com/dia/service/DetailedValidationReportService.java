package com.dia.service;

import com.dia.validation.data.DetailedValidationReportDto;
import com.dia.validation.data.ISMDValidationReport;
import org.apache.jena.rdf.model.Model;
import org.springframework.web.multipart.MultipartFile;

public interface DetailedValidationReportService {

    DetailedValidationReportDto generateDetailedReport(ISMDValidationReport report, Model ontologyModel);

    DetailedValidationReportDto generateDetailedReportFromTtlFile(ISMDValidationReport report, MultipartFile ttlFile);

    String generateCSV(DetailedValidationReportDto report);
}
