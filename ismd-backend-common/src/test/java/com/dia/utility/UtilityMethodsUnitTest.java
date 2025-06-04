package com.dia.utility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link UtilityMethods}.
 * @see UtilityMethods
 */
class UtilityMethodsUnitTest {

    @Test
    void testIsValidFirstChar() {
        // Valid first characters: letters, digits, underscore, colon
        assertTrue(UtilityMethods.isValidFirstChar('a'));
        assertTrue(UtilityMethods.isValidFirstChar('Z'));
        assertTrue(UtilityMethods.isValidFirstChar('_'));
        assertTrue(UtilityMethods.isValidFirstChar('7'));
        assertTrue(UtilityMethods.isValidFirstChar(':'));

        // Invalid first characters
        assertFalse(UtilityMethods.isValidFirstChar('-'));
        assertFalse(UtilityMethods.isValidFirstChar('.'));
        assertFalse(UtilityMethods.isValidFirstChar(' '));
        assertFalse(UtilityMethods.isValidFirstChar('!'));
    }

    @Test
    void testIsValidSubsequentChar() {
        // Valid subsequent characters
        assertTrue(UtilityMethods.isValidSubsequentChar('a'));
        assertTrue(UtilityMethods.isValidSubsequentChar('Z'));
        assertTrue(UtilityMethods.isValidSubsequentChar('_'));
        assertTrue(UtilityMethods.isValidSubsequentChar('5'));
        assertTrue(UtilityMethods.isValidSubsequentChar('-'));
        assertTrue(UtilityMethods.isValidSubsequentChar('.'));
        assertTrue(UtilityMethods.isValidSubsequentChar(':'));
        assertTrue(UtilityMethods.isValidSubsequentChar('·'));

        // Test Unicode combining characters (U+0300 to U+036F)
        assertTrue(UtilityMethods.isValidSubsequentChar('̀'));  // Combining grave accent
        assertTrue(UtilityMethods.isValidSubsequentChar('ͯ'));  // Combining Latin small letter x

        // Test Unicode joining characters (U+203F to U+2040)
        assertTrue(UtilityMethods.isValidSubsequentChar('‿'));  // Undertie
        assertTrue(UtilityMethods.isValidSubsequentChar('⁀'));  // Character tie

        // Invalid subsequent characters
        assertFalse(UtilityMethods.isValidSubsequentChar(' '));
        assertFalse(UtilityMethods.isValidSubsequentChar('!'));
        assertFalse(UtilityMethods.isValidSubsequentChar('@'));
    }

    @Test
    void testIsLetterOrUnderscore() {
        // Valid: letters and underscore
        assertTrue(UtilityMethods.isLetterOrUnderscore('a'));
        assertTrue(UtilityMethods.isLetterOrUnderscore('Z'));
        assertTrue(UtilityMethods.isLetterOrUnderscore('_'));
        assertTrue(UtilityMethods.isLetterOrUnderscore('ñ'));
        assertTrue(UtilityMethods.isLetterOrUnderscore('é'));

        // Invalid: not letters or underscore
        assertFalse(UtilityMethods.isLetterOrUnderscore('5'));
        assertFalse(UtilityMethods.isLetterOrUnderscore('-'));
        assertFalse(UtilityMethods.isLetterOrUnderscore(' '));
    }

    @Test
    void testIsValidUrl() {
        // Valid URLs
        assertTrue(UtilityMethods.isValidUrl("https://example.com"));
        assertTrue(UtilityMethods.isValidUrl("http://localhost:8080"));
        assertTrue(UtilityMethods.isValidUrl("ftp://server.edu/path/file.txt"));

        // Invalid URLs
        assertFalse(UtilityMethods.isValidUrl("not a url"));
        assertFalse(UtilityMethods.isValidUrl("http:/missing-slash"));
        assertFalse(UtilityMethods.isValidUrl("://no-protocol.com"));
        assertFalse(UtilityMethods.isValidUrl(null));
    }

    @Test
    void testEnsureNamespaceEndsWithDelimiter() {
        // Already has delimiter
        assertEquals("http://example.com/", UtilityMethods.ensureNamespaceEndsWithDelimiter("http://example.com/"));
        assertEquals("http://example.com#", UtilityMethods.ensureNamespaceEndsWithDelimiter("http://example.com#"));

        // Needs delimiter
        assertEquals("http://example.com/", UtilityMethods.ensureNamespaceEndsWithDelimiter("http://example.com"));

        // Edge cases
        assertEquals("/", UtilityMethods.ensureNamespaceEndsWithDelimiter(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "id-12345678abcdef",
            "id-12345678",
            "12345678-1234-5678-9abc-123456789012"
    })
    void testLooksLikeIdWithValidIds(String id) {
        assertTrue(UtilityMethods.looksLikeId(id));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-an-id",
            "ID-12345678",  // Case-sensitive check
            "id12345678",   // Missing hyphen
            "id-1234567",   // Not enough hexadecimal digits
            "12345678-1234-5678-9abc-12345678901"  // UUID too short
    })
    void testLooksLikeIdWithInvalidIds(String id) {
        assertFalse(UtilityMethods.looksLikeId(id));
    }

    @Test
    void testLooksLikeIdWithNullOrEmpty() {
        assertFalse(UtilityMethods.looksLikeId(null));
        assertFalse(UtilityMethods.looksLikeId(""));
    }

    @Test
    void testSanitizeForIRI() {
        // Basic sanitization
        assertEquals("hello-world", UtilityMethods.sanitizeForIRI("Hello World"));
        assertEquals("test-123", UtilityMethods.sanitizeForIRI("Test 123"));

        // Special characters
        assertEquals("a-b-c", UtilityMethods.sanitizeForIRI("a@b#c"));

        // Leading/trailing special characters
        assertEquals("hello", UtilityMethods.sanitizeForIRI("!@#hello!@#"));

        // Multiple consecutive special characters
        assertEquals("hello-world", UtilityMethods.sanitizeForIRI("hello!!!world"));

        // First character invalid
        assertEquals("123", UtilityMethods.sanitizeForIRI("@123"));

        // Edge cases
        assertEquals("unnamed", UtilityMethods.sanitizeForIRI(null));
        assertEquals("unnamed", UtilityMethods.sanitizeForIRI(""));
        assertEquals("unnamed", UtilityMethods.sanitizeForIRI("!@#$%^"));
    }

    @Test
    void testFilterMap() {
        // Create a map with various types of values
        Map<String, Object> originalMap = new LinkedHashMap<>();
        originalMap.put("key1", "value1");
        originalMap.put("key2", null);
        originalMap.put("key3", "");
        originalMap.put("key4", new HashMap<>());

        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("nestedKey1", "nestedValue1");
        nestedMap.put("nestedKey2", null);
        originalMap.put("key5", nestedMap);

        List<String> list = Arrays.asList("item1", "", null);
        originalMap.put("key6", list);

        // Filter the map
        Map<String, Object> filteredMap = UtilityMethods.filterMap(originalMap);

        // Verify results
        assertTrue(filteredMap.containsKey("key1"));
        assertEquals("value1", filteredMap.get("key1"));

        assertFalse(filteredMap.containsKey("key2"));  // Null value removed
        assertFalse(filteredMap.containsKey("key3"));  // Empty string removed
        assertFalse(filteredMap.containsKey("key4"));  // Empty map removed

        assertTrue(filteredMap.containsKey("key5"));
        Map<?, ?> filteredNestedMap = (Map<?, ?>) filteredMap.get("key5");
        assertTrue(filteredNestedMap.containsKey("nestedKey1"));
        assertEquals("nestedValue1", filteredNestedMap.get("nestedKey1"));
        assertFalse(filteredNestedMap.containsKey("nestedKey2"));  // Null nested value removed

        assertTrue(filteredMap.containsKey("key6"));
        List<?> filteredList = (List<?>) filteredMap.get("key6");
        assertEquals(1, filteredList.size());
        assertEquals("item1", filteredList.get(0));
    }

    @Test
    void testFilterList() {
        // Create a list with various types of values
        List<Object> originalList = Arrays.asList(
                "item1",
                null,
                "",
                new HashMap<>(),
                Arrays.asList("nestedItem1", null),
                new HashMap<String, Object>() {{
                    put("mapKey1", "mapValue1");
                    put("mapKey2", null);
                }}
        );

        // Filter the list
        List<Object> filteredList = UtilityMethods.filterList(originalList);

        // Verify results
        assertEquals(3, filteredList.size());

        assertEquals("item1", filteredList.get(0));

        List<?> nestedList = (List<?>) filteredList.get(1);
        assertEquals(1, nestedList.size());
        assertEquals("nestedItem1", nestedList.get(0));

        Map<?, ?> map = (Map<?, ?>) filteredList.get(2);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("mapKey1"));
        assertEquals("mapValue1", map.get("mapKey1"));
    }

    @Test
    void testFilterValue() {
        // Test primitive values
        assertEquals("string", UtilityMethods.filterValue("string"));
        assertEquals(123, UtilityMethods.filterValue(123));
        assertEquals(true, UtilityMethods.filterValue(true));

        // Test null and empty values
        assertNull(UtilityMethods.filterValue(null));
        assertNull(UtilityMethods.filterValue(""));

        // Test maps
        Map<String, Object> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> filteredMap = (Map<String, Object>) UtilityMethods.filterValue(map);
        assertEquals(1, filteredMap.size());
        assertEquals("value1", filteredMap.get("key1"));

        // Test empty map
        assertNull(UtilityMethods.filterValue(new HashMap<>()));

        // Test lists
        List<String> list = Arrays.asList("item1", null);

        @SuppressWarnings("unchecked")
        List<Object> filteredList = (List<Object>) UtilityMethods.filterValue(list);
        assertEquals(1, filteredList.size());
        assertEquals("item1", filteredList.get(0));

        // Test empty list
        assertNull(UtilityMethods.filterValue(Arrays.asList(null, "")));
    }
}
