package com.dia.service;

import com.dia.conversion.data.TransformationResult;
import com.dia.validation.data.ISMDValidationReport;
import com.dia.enums.ValidationTiming;
import org.apache.jena.rdf.model.Model;
import org.springframework.web.multipart.MultipartFile;

public interface ValidationService {

    ISMDValidationReport validate(TransformationResult result);

    ISMDValidationReport validate(TransformationResult result, ValidationTiming timing);

    ISMDValidationReport validateModel(Model model);

    ISMDValidationReport validateRdf(String rdfContent, String format);

    ISMDValidationReport validateTtl(String ttlContent);

    ISMDValidationReport validateTtlFile(MultipartFile file);
}
