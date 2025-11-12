package com.dia.workflow;

import com.dia.conversion.data.*;
import com.dia.conversion.reader.archi.ArchiReader;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.exceptions.ExcelReadingException;
import com.dia.exceptions.FileParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.dia.constants.FormatConstants.Converter.LOG_REQUEST_ID;

/**
 * Debug test that extracts OntologyData from Excel, Archi, and EA readers
 * and outputs them to separate text files for comparison and debugging.
 */
@ExtendWith(MockitoExtension.class)
class OntologyDataDebugExportTest {

    @InjectMocks
    private ArchiReader archiReader;

    @InjectMocks
    private EnterpriseArchitectReader eaReader;

    private ExcelReader excelReader;

    private static final String TEST_RESOURCES_PATH = "com/dia/canonical/complete/";
    private static final String OUTPUT_DIR = "target/debug-output/";

    @BeforeEach
    void setUp() {
        MDC.put(LOG_REQUEST_ID, "debug-test-123");
        excelReader = new ExcelReader();

        // Create output directory
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory", e);
        }
    }

    @Test
    void exportOntologyDataFromAllReaders() throws IOException, FileParsingException, ExcelReadingException {
        System.out.println("=".repeat(80));
        System.out.println("Starting OntologyData Debug Export Test");
        System.out.println("=".repeat(80));

        // Process Excel
        System.out.println("\n[1/3] Processing Excel file...");
        OntologyData excelData = processExcel();
        exportToFile(excelData, "excel-ontology-data.md", "Excel");

        // Process Archi
        System.out.println("\n[2/3] Processing Archi file...");
        OntologyData archiData = processArchi();
        exportToFile(archiData, "archi-ontology-data.md", "Archi");

        // Process EA
        System.out.println("\n[3/3] Processing Enterprise Architect file...");
        OntologyData eaData = processEA();
        exportToFile(eaData, "ea-ontology-data.md", "Enterprise Architect");

        System.out.println("\n" + "=".repeat(80));
        System.out.println("Export complete! Files saved to: " + OUTPUT_DIR);
        System.out.println("=".repeat(80));
        System.out.println("\nGenerated files:");
        System.out.println("  - " + OUTPUT_DIR + "excel-ontology-data.md");
        System.out.println("  - " + OUTPUT_DIR + "archi-ontology-data.md");
        System.out.println("  - " + OUTPUT_DIR + "ea-ontology-data.md");
    }

    private OntologyData processExcel() throws IOException, ExcelReadingException {
        ClassPathResource resource = new ClassPathResource(TEST_RESOURCES_PATH + "testExcelProject.xlsx");
        try (InputStream inputStream = resource.getInputStream()) {
            return excelReader.readOntologyFromExcel(inputStream);
        }
    }

    private OntologyData processArchi() throws IOException, FileParsingException {
        ClassPathResource resource = new ClassPathResource(TEST_RESOURCES_PATH + "testArchiInput.xml");
        String xmlContent = Files.readString(Paths.get(resource.getURI()));
        return archiReader.readArchiFromString(xmlContent);
    }

    private OntologyData processEA() throws IOException, FileParsingException {
        ClassPathResource resource = new ClassPathResource(TEST_RESOURCES_PATH + "testEAInput.xml");
        byte[] xmlBytes = Files.readAllBytes(Paths.get(resource.getURI()));
        return eaReader.readXmiFromBytes(xmlBytes);
    }

    private void exportToFile(OntologyData data, String filename, String sourceName) throws IOException {
        Path outputPath = Paths.get(OUTPUT_DIR + filename);
        StringBuilder content = new StringBuilder();

        // Header
        content.append("# OntologyData Export - ").append(sourceName).append("\n\n");
        content.append("Generated: ").append(java.time.LocalDateTime.now()).append("\n\n");
        content.append("---\n\n");

        // Vocabulary Metadata
        content.append("## Vocabulary Metadata\n\n");
        VocabularyMetadata metadata = data.getVocabularyMetadata();
        if (metadata != null) {
            appendField(content, "Name", metadata.getName());
            appendField(content, "Namespace", metadata.getNamespace());
            appendField(content, "Description", metadata.getDescription());
            appendField(content, "Date of Creation", metadata.getDateOfCreation());
            appendField(content, "Date of Modification", metadata.getDateOfModification());
        } else {
            content.append("*No vocabulary metadata found*\n");
        }
        content.append("\n---\n\n");

        // Classes
        content.append("## Classes (").append(data.getClasses().size()).append(")\n\n");
        if (data.getClasses().isEmpty()) {
            content.append("*No classes found*\n\n");
        } else {
            int classIndex = 1;
            for (ClassData classData : data.getClasses()) {
                content.append("### ").append(classIndex++).append(". ").append(classData.getName()).append("\n\n");
                appendField(content, "Type", classData.getType());
                appendField(content, "Definition", classData.getDefinition());
                appendField(content, "Description", classData.getDescription());
                appendField(content, "Source", classData.getSource());
                appendField(content, "Identifier", classData.getIdentifier());
                appendField(content, "Alternative Name", classData.getAlternativeName());
                appendField(content, "Is Public", classData.getIsPublic());
                appendField(content, "Privacy Provision", classData.getPrivacyProvision());
                appendField(content, "Shared in PPDF", classData.getSharedInPPDF());
                content.append("\n");
            }
        }
        content.append("---\n\n");

        // Properties
        content.append("## Properties (").append(data.getProperties().size()).append(")\n\n");
        if (data.getProperties().isEmpty()) {
            content.append("*No properties found*\n\n");
        } else {
            int propIndex = 1;
            for (PropertyData property : data.getProperties()) {
                content.append("### ").append(propIndex++).append(". ").append(property.getName()).append("\n\n");
                appendField(content, "Domain", property.getDomain());
                appendField(content, "Data Type", property.getDataType());
                appendField(content, "Description", property.getDescription());
                appendField(content, "Definition", property.getDefinition());
                appendField(content, "Source", property.getSource());
                appendField(content, "Identifier", property.getIdentifier());
                appendField(content, "Alternative Name", property.getAlternativeName());
                appendField(content, "Is Public", property.getIsPublic());
                appendField(content, "Privacy Provision", property.getPrivacyProvision());
                appendField(content, "Shared in PPDF", property.getSharedInPPDF());
                content.append("\n");
            }
        }
        content.append("---\n\n");

        // Relationships
        content.append("## Relationships (").append(data.getRelationships().size()).append(")\n\n");
        if (data.getRelationships().isEmpty()) {
            content.append("*No relationships found*\n\n");
        } else {
            int relIndex = 1;
            for (RelationshipData relationship : data.getRelationships()) {
                content.append("### ").append(relIndex++).append(". ").append(relationship.getName()).append("\n\n");
                appendField(content, "Domain", relationship.getDomain());
                appendField(content, "Range", relationship.getRange());
                appendField(content, "Relationship Type", relationship.getRelationshipType());
                appendField(content, "Description", relationship.getDescription());
                appendField(content, "Definition", relationship.getDefinition());
                appendField(content, "Source", relationship.getSource());
                appendField(content, "Identifier", relationship.getIdentifier());
                appendField(content, "Alternative Name", relationship.getAlternativeName());
                appendField(content, "Is Public", relationship.getIsPublic());
                appendField(content, "Privacy Provision", relationship.getPrivacyProvision());
                appendField(content, "Shared in PPDF", relationship.getSharedInPPDF());
                content.append("\n");
            }
        }
        content.append("---\n\n");

        // Hierarchies
        content.append("## Hierarchies (").append(data.getHierarchies().size()).append(")\n\n");
        if (data.getHierarchies().isEmpty()) {
            content.append("*No hierarchies found*\n\n");
        } else {
            int hierIndex = 1;
            for (HierarchyData hierarchy : data.getHierarchies()) {
                String title = hierarchy.getSubClass() + " → " + hierarchy.getSuperClass();
                content.append("### ").append(hierIndex++).append(". ").append(title).append("\n\n");
                appendField(content, "Sub-Class", hierarchy.getSubClass());
                appendField(content, "Super-Class", hierarchy.getSuperClass());
                appendField(content, "Relationship Name", hierarchy.getRelationshipName());
                content.append("\n");
            }
        }
        content.append("---\n\n");

        // Summary Statistics
        content.append("## Summary Statistics\n\n");
        content.append("| Category | Count |\n");
        content.append("|----------|-------|\n");
        content.append("| Classes | ").append(data.getClasses().size()).append(" |\n");
        content.append("| Properties | ").append(data.getProperties().size()).append(" |\n");
        content.append("| Relationships | ").append(data.getRelationships().size()).append(" |\n");
        content.append("| Hierarchies | ").append(data.getHierarchies().size()).append(" |\n");

        // Write to file
        Files.writeString(outputPath, content.toString(), StandardCharsets.UTF_8);

        System.out.println("  ✓ Exported to: " + outputPath);
        System.out.println("    - Classes: " + data.getClasses().size());
        System.out.println("    - Properties: " + data.getProperties().size());
        System.out.println("    - Relationships: " + data.getRelationships().size());
        System.out.println("    - Hierarchies: " + data.getHierarchies().size());
    }

    private void appendField(StringBuilder sb, String fieldName, String value) {
        if (value != null && !value.trim().isEmpty()) {
            sb.append("**").append(fieldName).append(":** ");
            // Escape markdown special characters in the value
            String escapedValue = value.replace("|", "\\|").replace("\n", " ");
            sb.append(escapedValue).append("  \n");
        }
    }
}
