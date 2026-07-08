package com.dia.validation.sparql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeIriTest {

    @Test
    void acceptsPlainHttpAndHttpsIris() {
        assertThat(SafeIri.isSafeHttpIri("https://slovník.gov.cz/a3791---registr-vysokých-škol")).isTrue();
        assertThat(SafeIri.isSafeHttpIri("http://example.org/x")).isTrue();
    }

    @Test
    void rejectsNullBlankRelativeAndNonHttp() {
        assertThat(SafeIri.isSafeHttpIri(null)).isFalse();
        assertThat(SafeIri.isSafeHttpIri("  ")).isFalse();
        assertThat(SafeIri.isSafeHttpIri("not a uri")).isFalse();
        assertThat(SafeIri.isSafeHttpIri("/relative/path")).isFalse();
        assertThat(SafeIri.isSafeHttpIri("ftp://example.org/x")).isFalse();
    }

    @Test
    void rejectsInjectionBreakoutCharacters() {
        assertThat(SafeIri.isSafeHttpIri("https://x/a> } INSERT { <y> <z> <w> } WHERE { <a")).isFalse();
        assertThat(SafeIri.isSafeHttpIri("https://x/a\"b")).isFalse();
        assertThat(SafeIri.isSafeHttpIri("https://x/a b")).isFalse(); // space
    }
}
