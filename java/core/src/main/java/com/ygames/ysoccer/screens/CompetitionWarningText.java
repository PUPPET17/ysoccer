package com.ygames.ysoccer.screens;

/** Splits the two-line competition-loss warning without assuming a translation contains spaces. */
final class CompetitionWarningText {
    private CompetitionWarningText() {
    }

    /**
     * Keeps word boundaries when available and otherwise splits at a Unicode code-point boundary.
     * Both warning screens use this so Chinese and other unspaced translations cannot crash navigation.
     * @return exactly two non-null label strings, preserving all non-whitespace characters
     */
    static String[] split(String message) {
        int count = message.codePointCount(0, message.length());
        if (count < 2) return new String[]{message, ""};
        int middle = message.offsetByCodePoints(0, count / 2);
        int cut = message.indexOf(' ', middle);
        if (cut < 0) cut = message.lastIndexOf(' ', middle);
        if (cut > 0 && cut < message.length() - 1) {
            return new String[]{message.substring(0, cut).trim(), message.substring(cut + 1).trim()};
        }
        return new String[]{message.substring(0, middle), message.substring(middle)};
    }
}
