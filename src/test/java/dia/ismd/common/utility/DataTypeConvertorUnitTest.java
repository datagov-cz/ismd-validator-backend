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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.*;

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

    // TODO verify
    /*@Test
    void testAddTypedProperty_withValidValue_shouldAddProperty() {
        when(model.createTypedLiteral("123", XSDDatatype.XSDinteger)).thenReturn(literal);
        when(literal.getDatatypeURI()).thenReturn("http://www.w3.org/2001/XMLSchema#integer");

        DataTypeConvertor.addTypedProperty(subject, property, "123", null, model);

        verify(subject).addProperty(property, literal);
    }*/
}
