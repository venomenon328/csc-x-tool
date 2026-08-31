package de.venomenon.cscxtool.publishedballot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NumericCharacterReferences {

    private static final Pattern REFERENCE = Pattern.compile("&#(?:(?:x|X)([0-9A-Fa-f]+)|(\\d+));");

    private NumericCharacterReferences() { }

    static String decode(String source) {
        if (source == null || source.indexOf("&#") < 0) return source;

        Matcher matcher = REFERENCE.matcher(source);
        StringBuilder decoded = new StringBuilder(source.length());
        int cursor = 0;
        while (matcher.find()) {
            decoded.append(source, cursor, matcher.start());
            Integer codePoint = codePoint(matcher);
            if (codePoint != null && isUnicodeScalarValue(codePoint)) decoded.appendCodePoint(codePoint);
            else decoded.append(matcher.group());
            cursor = matcher.end();
        }
        decoded.append(source, cursor, source.length());
        return decoded.toString();
    }

    private static Integer codePoint(Matcher matcher) {
        String hexadecimal = matcher.group(1);
        String digits = hexadecimal == null ? matcher.group(2) : hexadecimal;
        try {
            return Integer.parseInt(digits, hexadecimal == null ? 10 : 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isUnicodeScalarValue(int codePoint) {
        return Character.isValidCodePoint(codePoint)
                && codePoint != 0
                && (codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE);
    }
}
