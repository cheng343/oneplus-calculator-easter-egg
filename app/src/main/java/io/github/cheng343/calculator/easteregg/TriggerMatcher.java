package io.github.cheng343.calculator.easteregg;

final class TriggerMatcher {
    private TriggerMatcher() {}

    static boolean matches(CharSequence text) {
        if (text == null) return false;
        StringBuilder normalized = new StringBuilder(2);
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (Character.isWhitespace(value) || Character.isSpaceChar(value)
                    || value == '\u200e' || value == '\u200f' || value == '\u061c'
                    || (value >= '\u2066' && value <= '\u2069')) continue;
            if (value == '\uff11') value = '1';
            if (value == '\uff0b' || value == '\ufe62') value = '+';
            normalized.append(value);
            if (normalized.length() > 2) return false;
        }
        return "1+".contentEquals(normalized);
    }
}
