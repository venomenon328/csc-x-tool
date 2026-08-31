package de.venomenon.cscxtool.publishedballot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NumericCharacterReferencesTest {

    @Test
    void decodesDecimalAndHexadecimalUnicodeReferences() {
        assertThat(NumericCharacterReferences.decode("1 &#1201;&#1087;&#1072;&#1081; / &#x4B1;&#x43F;&#x430;&#x439;"))
                .isEqualTo("1 ұпай / ұпай");
    }

    @Test
    void leavesAlreadyDecodedUnicodeUntouched() {
        assertThat(NumericCharacterReferences.decode("25 ұпай")).isEqualTo("25 ұпай");
    }

    @Test
    void leavesInvalidUnicodeReferencesLiteral() {
        assertThat(NumericCharacterReferences.decode("&#0; &#55296; &#x110000; &#999999999999999999999;"))
                .isEqualTo("&#0; &#55296; &#x110000; &#999999999999999999999;");
    }

    @Test
    void doesNotInterpretNamedEntitiesOrMarkup() {
        assertThat(NumericCharacterReferences.decode("&amp; <strong>ұпай</strong>"))
                .isEqualTo("&amp; <strong>ұпай</strong>");
    }
}
