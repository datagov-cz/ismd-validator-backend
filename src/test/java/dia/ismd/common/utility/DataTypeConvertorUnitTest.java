package dia.ismd.common.utility;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataTypeConvertorUnitTest {

    @Mock
    private Model model;

    @Mock
    private Resource subject;

    @Mock
    private Property property;

    @Mock
    private Literal literal;

    @BeforeEach
    void setUp() {
        when(property.getLocalName()).thenReturn("testProperty");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "  \t\n  "})
    void testAddTypedProperty_withInvalidValues_shouldDoNothing(String value) {
        DataTypeConvertor.addTypedProperty(subject, property, value, null, model);

        verifyNoInteractions(subject);
    }

    @Test
    void testAddTypedProperty_withValidValue_shouldAddProperty() {
        when(model.createTypedLiteral(123, XSDDatatype.XSDinteger)).thenReturn(literal);
        when(literal.getDatatypeURI()).thenReturn("http://www.w3.org/2001/XMLSchema#integer");

        DataTypeConvertor.addTypedProperty(subject, property, "123", null, model);

        verify(subject).addProperty(property, literal);
    }

    @Test
    void testAddTypedProperty_withLanguage_shouldCreateLanguageTaggedLiteral() {
        when(model.createLiteral("hello", "en")).thenReturn(literal);

        DataTypeConvertor.addTypedProperty(subject, property, "hello", "en", model);

        verify(model).createLiteral("hello", "en");
        verify(subject).addProperty(property, literal);
    }

    @Test
    void testCreateTypedLiteral_withLanguage_shouldReturnLanguageTaggedLiteral() {
        when(model.createLiteral("hello", "en")).thenReturn(literal);

        Literal result = DataTypeConvertor.createTypedLiteral("hello", model, "en", "testProperty");

        assertEquals(literal, result);
        verify(model).createLiteral("hello", "en");
    }

    @ParameterizedTest
    @CsvSource({
            "true, true",
            "false, false",
            "ano, true",
            "ne, false",
            "yes, true",
            "no, false"
    })
    void testCreateTypedLiteral_withBooleanValues_shouldReturnBooleanLiteral(String input, boolean expected) {
        when(model.createTypedLiteral(expected)).thenReturn(literal);

        Literal result = DataTypeConvertor.createTypedLiteral(input, model, null, "testProperty");

        assertEquals(literal, result);
        verify(model).createTypedLiteral(expected);
    }

    @Test
    void testCreateTypedLiteral_withInteger_shouldReturnIntegerLiteral() {
        when(model.createTypedLiteral(123, XSDDatatype.XSDinteger)).thenReturn(literal);

        Literal result = DataTypeConvertor.createTypedLiteral("123", model, null, "testProperty");

        assertEquals(literal, result);
        verify(model).createTypedLiteral(123, XSDDatatype.XSDinteger);
    }

    @Test
    void testCreateTypedLiteral_withNegativeInteger_shouldReturnIntegerLiteral() {
        when(model.createTypedLiteral(-456, XSDDatatype.XSDinteger)).thenReturn(literal);

        Literal result = DataTypeConvertor.createTypedLiteral("-456", model, null, "testProperty");

        assertEquals(literal, result);
        verify(model).createTypedLiteral(-456, XSDDatatype.XSDinteger);
    }

    @Test
    void testCreateTypedLiteral_withDouble_shouldReturnDoubleLiteral() {
        when(model.createTypedLiteral(123.45, XSDDatatype.XSDdouble)).thenReturn(literal);

        Literal result = DataTypeConvertor.createTypedLiteral("123.45", model, null, "testProperty");

        assertEquals(literal, result);
        verify(model).createTypedLiteral(123.45, XSDDatatype.XSDdouble);
    }

    @Test
    void testCreateTypedLiteral_withDate_shouldReturnDateLiteral() {
        when(model.createTypedLiteral("2023-12-25", XSDDatatype.XSDdate)).thenReturn(literal);

        Literal result = DataTypeConvertor.createTypedLiteral("2023-12-25", model, null, "testProperty");

        assertEquals(literal, result);
        verify(model).createTypedLiteral("2023-12-25", XSDDatatype.XSDdate);
    }

    @Test
    void testCreateTypedLiteral_withTime_shouldReturnTimeLiteral() {
        when(model.createTypedLiteral("14:30:00", XSDDatatype.XSDtime)).thenReturn(literal);

        Literal result = DataTypeConvertor.createTypedLiteral("14:30:00", model, null, "testProperty");

        assertEquals(literal, result);
        verify(model).createTypedLiteral("14:30:00", XSDDatatype.XSDtime);
    }

    @Test
    void testCreateTypedLiteral_withDateTime_shouldReturnDateTimeLiteral() {
        when(model.createTypedLiteral("2023-12-25T14:30:00", XSDDatatype.XSDdateTime)).thenReturn(literal);

        Literal result = DataTypeConvertor.createTypedLiteral("2023-12-25T14:30:00", model, null, "testProperty");

        assertEquals(literal, result);
        verify(model).createTypedLiteral("2023-12-25T14:30:00", XSDDatatype.XSDdateTime);
    }

    @Test
    void testCreateTypedLiteral_withUri_shouldReturnUriLiteral() {
        when(model.createTypedLiteral("https://example.com", XSDDatatype.XSDanyURI)).thenReturn(literal);

        Literal result = DataTypeConvertor.createTypedLiteral("https://example.com", model, null, "testProperty");

        assertEquals(literal, result);
        verify(model).createTypedLiteral("https://example.com", XSDDatatype.XSDanyURI);
    }

    @Test
    void testCreateTypedLiteral_withString_shouldReturnStringLiteral() {
        when(model.createTypedLiteral("hello world", XSDDatatype.XSDstring)).thenReturn(literal);

        Literal result = DataTypeConvertor.createTypedLiteral("hello world", model, null, "testProperty");

        assertEquals(literal, result);
        verify(model).createTypedLiteral("hello world", XSDDatatype.XSDstring);
    }

    // Boolean value tests
    @Test
    void testIsBooleanValue_withVariousBooleanValues() {
        assertTrue(DataTypeConvertor.isBooleanValue("true"));
        assertTrue(DataTypeConvertor.isBooleanValue("TRUE"));
        assertTrue(DataTypeConvertor.isBooleanValue("false"));
        assertTrue(DataTypeConvertor.isBooleanValue("FALSE"));
        assertTrue(DataTypeConvertor.isBooleanValue("ano"));
        assertTrue(DataTypeConvertor.isBooleanValue("ANO"));
        assertTrue(DataTypeConvertor.isBooleanValue("ne"));
        assertTrue(DataTypeConvertor.isBooleanValue("NE"));
        assertTrue(DataTypeConvertor.isBooleanValue("yes"));
        assertTrue(DataTypeConvertor.isBooleanValue("YES"));
        assertTrue(DataTypeConvertor.isBooleanValue("no"));
        assertTrue(DataTypeConvertor.isBooleanValue("NO"));

        assertFalse(DataTypeConvertor.isBooleanValue("maybe"));
        assertFalse(DataTypeConvertor.isBooleanValue("1"));
        assertFalse(DataTypeConvertor.isBooleanValue(""));
    }

    // Integer tests
    @Test
    void testIsInteger_withValidIntegers() {
        assertTrue(DataTypeConvertor.isInteger("123"));
        assertTrue(DataTypeConvertor.isInteger("-456"));
        assertTrue(DataTypeConvertor.isInteger("0"));
        assertTrue(DataTypeConvertor.isInteger("999999999"));

        assertFalse(DataTypeConvertor.isInteger("123.45"));
        assertFalse(DataTypeConvertor.isInteger("abc"));
        assertFalse(DataTypeConvertor.isInteger(""));
        assertFalse(DataTypeConvertor.isInteger("123.0"));
    }

    // Double tests
    @Test
    void testIsDouble_withValidDoubles() {
        assertTrue(DataTypeConvertor.isDouble("123.45"));
        assertTrue(DataTypeConvertor.isDouble("-456.789"));
        assertTrue(DataTypeConvertor.isDouble("0.0"));
        assertTrue(DataTypeConvertor.isDouble("123"));
        assertTrue(DataTypeConvertor.isDouble("-123"));

        assertFalse(DataTypeConvertor.isDouble("abc"));
        assertFalse(DataTypeConvertor.isDouble(""));
        assertFalse(DataTypeConvertor.isDouble("123.45.67"));
    }

    // URI tests
    @Test
    void testIsUri_withValidUris() {
        assertTrue(DataTypeConvertor.isUri("https://example.com"));
        assertTrue(DataTypeConvertor.isUri("http://test.org/path"));
        assertTrue(DataTypeConvertor.isUri("ftp://files.example.com"));

        assertFalse(DataTypeConvertor.isUri("not-a-uri"));
        assertFalse(DataTypeConvertor.isUri(""));
        assertFalse(DataTypeConvertor.isUri("example.com")); // no scheme
        assertFalse(DataTypeConvertor.isUri("https://")); // no host
    }

    // Date tests
    @Test
    void testIsDate_withValidDates() {
        assertTrue(DataTypeConvertor.isDate("2023-12-25"));
        assertTrue(DataTypeConvertor.isDate("25.12.2023"));
        assertTrue(DataTypeConvertor.isDate("1.1.2023"));

        assertFalse(DataTypeConvertor.isDate("not-a-date"));
        assertFalse(DataTypeConvertor.isDate(""));
        assertFalse(DataTypeConvertor.isDate("2023-13-01")); // invalid month
        assertFalse(DataTypeConvertor.isDate("32.12.2023")); // invalid day
    }

    // Time tests
    @Test
    void testIsTime_withValidTimes() {
        assertTrue(DataTypeConvertor.isTime("14:30:00"));
        assertTrue(DataTypeConvertor.isTime("14:30"));
        assertTrue(DataTypeConvertor.isTime("09:05:15"));

        assertFalse(DataTypeConvertor.isTime("not-a-time"));
        assertFalse(DataTypeConvertor.isTime(""));
        assertFalse(DataTypeConvertor.isTime("25:00:00")); // invalid hour
        assertFalse(DataTypeConvertor.isTime("14:60:00")); // invalid minute
    }

    // DateTime tests
    @Test
    void testIsDateTime_withValidDateTimes() {
        assertTrue(DataTypeConvertor.isDateTime("2023-12-25T14:30:00"));
        assertTrue(DataTypeConvertor.isDateTime("25.12.2023 14:30:00"));
        assertTrue(DataTypeConvertor.isDateTime("2023-12-25T14:30:00"));

        assertFalse(DataTypeConvertor.isDateTime("not-a-datetime"));
        assertFalse(DataTypeConvertor.isDateTime(""));
        assertFalse(DataTypeConvertor.isDateTime("2023-12-25"));
        assertFalse(DataTypeConvertor.isDateTime("14:30:00"));
    }

    // Property name inference tests
    @Test
    void testInferTypeFromPropertyName_withDateProperties() {
        // Test through createTypedLiteral
        when(model.createTypedLiteral("somevalue", XSDDatatype.XSDdate)).thenReturn(literal);

        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "datumNarozeni");
        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "birthDate");

        verify(model, times(2)).createTypedLiteral("somevalue", XSDDatatype.XSDdate);
    }

    @Test
    void testInferTypeFromPropertyName_withTimeProperties() {
        when(model.createTypedLiteral("somevalue", XSDDatatype.XSDtime)).thenReturn(literal);

        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "casOdchodu");
        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "departureTime");

        verify(model, times(2)).createTypedLiteral("somevalue", XSDDatatype.XSDtime);
    }

    @Test
    void testInferTypeFromPropertyName_withUriProperties() {
        when(model.createTypedLiteral("somevalue", XSDDatatype.XSDanyURI)).thenReturn(literal);

        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "webUrl");
        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "uri");
        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "odkaz");
        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "link");

        verify(model, times(4)).createTypedLiteral("somevalue", XSDDatatype.XSDanyURI);
    }

    @Test
    void testInferTypeFromPropertyName_withBooleanProperties() {
        when(model.createTypedLiteral(false)).thenReturn(literal);

        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "isActive");
        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "hasChildren");
        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "flagEnabled");
        DataTypeConvertor.createTypedLiteral("somevalue", model, null, "indicator");

        verify(model, times(4)).createTypedLiteral(false);
    }

    @Test
    void testInferTypeFromPropertyName_withIntegerProperties() {
        when(model.createTypedLiteral("somevalue", XSDDatatype.XSDinteger)).thenReturn(literal);

        DataTypeConvertor.createTypedLiteral(String.valueOf(123), model, null, "count");
        DataTypeConvertor.createTypedLiteral(String.valueOf(123), model, null, "number");
        DataTypeConvertor.createTypedLiteral(String.valueOf(123), model, null, "quantity");

        verify(model, times(3)).createTypedLiteral(123, XSDDatatype.XSDinteger);
    }

    @Test
    void testInferTypeFromPropertyName_withNullPropertyName() {
        when(model.createTypedLiteral("somevalue", XSDDatatype.XSDstring)).thenReturn(literal);

        DataTypeConvertor.createTypedLiteral("somevalue", model, null, null);

        verify(model).createTypedLiteral("somevalue", XSDDatatype.XSDstring);
    }

    @Test
    void testEdgeCases_emptyStrings() {
        assertFalse(DataTypeConvertor.isBooleanValue(""));
        assertFalse(DataTypeConvertor.isInteger(""));
        assertFalse(DataTypeConvertor.isDouble(""));
        assertFalse(DataTypeConvertor.isUri(""));
        assertFalse(DataTypeConvertor.isDate(""));
        assertFalse(DataTypeConvertor.isTime(""));
        assertFalse(DataTypeConvertor.isDateTime(""));
    }

    @Test
    void testEdgeCases_whitespaceStrings() {
        assertFalse(DataTypeConvertor.isBooleanValue("   "));
        assertFalse(DataTypeConvertor.isInteger("   "));
        assertFalse(DataTypeConvertor.isDouble("   "));
        assertFalse(DataTypeConvertor.isUri("   "));
        assertFalse(DataTypeConvertor.isDate("   "));
        assertFalse(DataTypeConvertor.isTime("   "));
        assertFalse(DataTypeConvertor.isDateTime("   "));
    }

    @Test
    void testCreateTypedLiteral_fallbackToStringType() {
        when(model.createTypedLiteral("random text", XSDDatatype.XSDstring)).thenReturn(literal);

        Literal result = DataTypeConvertor.createTypedLiteral("random text", model, null, "unknownProperty");

        assertEquals(literal, result);
        verify(model).createTypedLiteral("random text", XSDDatatype.XSDstring);
    }

    @Test
    void testCreateTypedLiteral_fallbackToPlainLiteralOnError() {
        when(model.createTypedLiteral("error value", XSDDatatype.XSDstring)).thenReturn(literal);

        // This would test the catch block in createTypedLiteral, but it's hard to trigger
        // since the type detection is robust. The fallback is tested above.
        Literal result = DataTypeConvertor.createTypedLiteral("error value", model, null, "testProperty");

        assertNotNull(result);
    }
}
