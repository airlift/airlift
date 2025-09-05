package io.airlift.api.internals;

import java.util.Locale;

public interface Strings
{
    static String capitalize(String s)
    {
        return s.isEmpty() ? "" : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    static String snakeCase(String input)
    {
        // copied from Jackson databind

        int length = input.length();
        StringBuilder result = new StringBuilder(length * 2);
        int resultLength = 0;
        boolean wasPrevTranslated = false;
        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);
            if (i > 0 || c != '_') { // skip first starting underscore
                if (Character.isUpperCase(c)) {
                    if (!wasPrevTranslated && resultLength > 0 && result.charAt(resultLength - 1) != '_') {
                        result.append('_');
                        resultLength++;
                    }
                    c = Character.toLowerCase(c);
                    wasPrevTranslated = true;
                }
                else {
                    wasPrevTranslated = false;
                }
                result.append(c);
                resultLength++;
            }
        }
        return resultLength > 0 ? result.toString() : input;
    }

    static String camelCase(String enumName)
    {
        // copied from Jackson databind

        if (enumName == null) {
            return null;
        }

        final String underscore = "_";
        StringBuilder out = null;
        int iterationCnt = 0;
        int lastSeparatorIdx = -1;

        do {
            lastSeparatorIdx = indexIn(enumName, lastSeparatorIdx + 1);
            if (lastSeparatorIdx != -1) {
                if (iterationCnt == 0) {
                    out = new StringBuilder(enumName.length() + 4 * underscore.length());
                    out.append(toLowerCase(enumName.substring(iterationCnt, lastSeparatorIdx)));
                }
                else {
                    out.append(normalizeWord(enumName.substring(iterationCnt, lastSeparatorIdx)));
                }
                iterationCnt = lastSeparatorIdx + underscore.length();
            }
        }
        while (lastSeparatorIdx != -1);

        if (iterationCnt == 0) {
            return toLowerCase(enumName);
        }
        out.append(normalizeWord(enumName.substring(iterationCnt)));
        return out.toString();
    }

    private static int indexIn(CharSequence sequence, int start)
    {
        int length = sequence.length();
        for (int i = start; i < length; i++) {
            if ('_' == sequence.charAt(i)) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    private static String normalizeWord(String word)
    {
        int length = word.length();
        if (length == 0) {
            return word;
        }
        return new StringBuilder(length)
                .append(charToUpperCaseIfLower(word.charAt(0)))
                .append(toLowerCase(word.substring(1)))
                .toString();
    }

    private static String toLowerCase(String string)
    {
        int length = string.length();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(charToLowerCaseIfUpper(string.charAt(i)));
        }
        return builder.toString();
    }

    private static char charToUpperCaseIfLower(char c)
    {
        return Character.isLowerCase(c) ? Character.toUpperCase(c) : c;
    }

    private static char charToLowerCaseIfUpper(char c)
    {
        return Character.isUpperCase(c) ? Character.toLowerCase(c) : c;
    }
}
