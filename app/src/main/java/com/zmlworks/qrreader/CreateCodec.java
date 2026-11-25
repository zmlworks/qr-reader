package com.zmlworks.qrreader;

/**
 * Create-page formats and the text that is encoded / saved (pure Java, unit-tested).
 * QR is left to {@link QrGen}; Code 128 and EAN-13 / JAN use {@link Recreate}.
 */
final class CreateCodec {
    private CreateCodec() {}

    static final String QR = "QR", CODE_128 = "Code 128", EAN13 = "EAN-13";

    /** History {@link History.Entry#format} (null = QR). */
    static String historyFormat(String fmt) {
        return fmt == null || QR.equals(fmt) ? null : fmt;
    }

    /**
     * Text to encode and to store. Empty input is refused. EAN-13 / JAN: spaces and hyphens are ignored;
     * 12 digits get a check digit, 13 digits are checked.
     */
    static String payload(String fmt, String text) throws Recreate.InvalidContent {
        if (text == null || text.isEmpty()) throw new Recreate.InvalidContent("empty", null);
        if (EAN13.equals(fmt)) return ean13(text);
        if (CODE_128.equals(fmt)) {
            Recreate.encode(CODE_128, text);
            return text;
        }
        return text;
    }

    /** 13 digits with a wrong check digit (after stripping spaces and hyphens). */
    static boolean ean13BadCheck(String text) {
        String d = digits(text);
        return d.length() == 13 && allDigits(d) && check(d.substring(0, 12)) != d.charAt(12) - '0';
    }

    static String ean13(String text) throws Recreate.InvalidContent {
        String d = digits(text);
        if (d.length() == 12 && allDigits(d)) return d + check(d);
        if (d.length() == 13 && allDigits(d)) {
            int ck = check(d.substring(0, 12));
            if (ck != d.charAt(12) - '0') throw new Recreate.InvalidContent("check", null);
            return d;
        }
        throw new Recreate.InvalidContent("digits", null);
    }

    /** EAN-13 check digit of the first 12 digits (0–9). */
    static int check(String d12) {
        int s = 0;
        for (int i = 0; i < 12; i++) s += (d12.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return (10 - (s % 10)) % 10;
    }

    static String digits(String text) {
        if (text == null) return "";
        StringBuilder b = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ' && c != '-') b.append(c);
        }
        return b.toString();
    }

    private static boolean allDigits(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        return true;
    }
}
