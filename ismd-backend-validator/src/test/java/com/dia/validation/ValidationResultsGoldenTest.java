package com.dia.validation;

import com.dia.conversion.data.OntologyData;
import com.dia.conversion.reader.archi.ArchiReader;
import com.dia.conversion.reader.ea.EnterpriseArchitectReader;
import com.dia.conversion.reader.excel.ExcelReader;
import com.dia.conversion.transformer.OFNDataTransformer;
import com.dia.service.ValidationService;
import com.dia.validation.data.ISMDValidationReport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the local SHACL results (severity, rule, focus node, path, value) for every conformance
 * fixture, so a change in the converted model that changes what users are told shows up as a diff.
 * <p>
 * Regenerate after an intended change with {@code -Dvalidation.golden.update=true}.
 */
@SpringBootTest
@ActiveProfiles("test")
class ValidationResultsGoldenTest {

    private static final Path GOLDEN_DIR = Path.of("src/test/resources/com/dia/expected-outputs/validation");

    @Autowired
    private ExcelReader excelReader;
    @Autowired
    private ArchiReader archiReader;
    @Autowired
    private EnterpriseArchitectReader eaReader;
    @Autowired
    private OFNDataTransformer transformer;
    @Autowired
    private ValidationService validationService;

    static List<String> fixtures() {
        String dir = "com/dia/canonical/complete/";
        return List.of(dir + "testExcelProject_no-lkod.xlsx", dir + "testArchiInput_no-lkod.xml",
                dir + "testEAInput_no-lkod.xml", dir + "ciselniky-test-excel.xlsx",
                dir + "ciselniky-test-Archi.xml", dir + "ciselniky-test-ea.xml");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void validationResults_matchGolden(String fixture) throws Exception {
        ISMDValidationReport report = validationService.validate(transformer.transform(read(fixture)));
        String actual = report.results().stream()
                .map(r -> String.join(" | ", String.valueOf(r.severity()), r.ruleName(),
                        String.valueOf(r.focusNodeUri()), String.valueOf(r.resultPathUri()), String.valueOf(r.value())))
                .sorted()
                .collect(Collectors.joining("\n", "", "\n"));

        Path golden = GOLDEN_DIR.resolve(fixture.substring(fixture.lastIndexOf('/') + 1) + ".txt");
        if (Boolean.getBoolean("validation.golden.update")) {
            Files.createDirectories(GOLDEN_DIR);
            Files.writeString(golden, actual, StandardCharsets.UTF_8);
            return;
        }
        assertThat(actual).isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
    }

    private OntologyData read(String fixture) throws Exception {
        try (InputStream is = new ClassPathResource(fixture).getInputStream()) {
            if (fixture.endsWith(".xlsx")) {
                return excelReader.readOntologyFromExcel(is);
            }
            byte[] bytes = is.readAllBytes();
            return fixture.contains("EA") || fixture.contains("-ea")
                    ? eaReader.readXmiFromBytes(bytes)
                    : archiReader.readArchiFromString(new String(bytes, StandardCharsets.UTF_8));
        }
    }
}
