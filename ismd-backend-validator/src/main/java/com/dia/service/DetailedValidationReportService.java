package com.dia.service;

import com.dia.validation.data.DetailedValidationReportDto;
import com.dia.validation.data.ISMDValidationReport;
import org.apache.jena.rdf.model.Model;

public interface DetailedValidationReportService {

    DetailedValidationReportDto generateDetailedReport(ISMDValidationReport report, Model ontologyModel);

    DetailedValidationReportDto generateCombinedDetailedReport(
            ISMDValidationReport localReport,
            ISMDValidationReport globalReport,
            Model ontologyModel
    );

    DetailedValidationReportDto generateCombinedDetailedReport(
            ISMDValidationReport localReport,
            ISMDValidationReport globalReport,
            Model ontologyModel,
            Model shaclRulesModel
    );

    String generateCSV(DetailedValidationReportDto report);
}
