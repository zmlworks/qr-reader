package com.zmlworks.qrreader;

import java.util.Locale;

/**
 * What a scanned text is (pure Java, unit-tested): URL, Wi-Fi, phone, e-mail, SMS, geo or plain text, plus the
 * fields the detail page needs. Parsing is lenient; anything not recognised is {@link Kind#TEXT}.
 */
public final class ContentType {
    public enum Kind { URL, WIFI, TEL, EMAIL, SMS, GEO, TEXT }

    public final Kind kind;
    /** URL to open (http/https; "www.x" gets https://), or the tel:/mailto:/sms:/geo: URI to hand to other apps */
    public final String uri;
    // Wi-Fi
    public final String ssid, password, security;
    public final boolean hidden;
    // tel / sms / e-mail
    public final String number, address, subject, body;

    private ContentType(Kind k, String uri, String ssid, String password, String security, boolean hidden,
                        String number, String address, String subject, String body) {
        this.kind = k; this.uri = uri; this.ssid = ssid; this.password = password; this.security = security;
        this.hidden = hidden; this.number = number; this.address = address; this.subject = subject; this.body = body;
    }

    private static ContentType simple(Kind k, String uri) { return new ContentType(k, uri, null, null, null, false, null, null, null, null); }

    /** Short label for lists ("URL", "Wi-Fi", ...). */
    public String label() {
        switch (kind) {
            case URL: return "URL";
            case WIFI: return "Wi-Fi";
            case TEL: return "Phone";
            case EMAIL: return "Email";
            case SMS: return "SMS";
            case GEO: return "Location";
            default: return "Text";
        }
    }

    public static ContentType parse(String raw) {
        if (raw == null) return simple(Kind.TEXT, null);
        String t = raw.trim();
        String low = t.toLowerCase(Locale.ROOT);
        if (low.startsWith("http://") || low.startsWith("https://")) {
            if (t.length() > low.indexOf("://") + 3 && !containsWhitespace(t)) return simple(Kind.URL, t);
            return simple(Kind.TEXT, null);
        }
        if (low.startsWith("www.") && !containsWhitespace(t) && t.indexOf('.', 4) > 4) return simple(Kind.URL, "https://" + t);
        if (low.startsWith("wifi:")) return wifi(t);
        if (low.startsWith("tel:")) {
            String n = t.substring(4).trim();
            return n.isEmpty() ? simple(Kind.TEXT, null) : new ContentType(Kind.TEL, "tel:" + n, null, null, null, false, n, null, null, null);
        }
        if (low.startsWith("mailto:")) return mailto(t);
        if (low.startsWith("matmsg:")) return matmsg(t);
        if (low.startsWith("smsto:") || low.startsWith("sms:") || low.startsWith("mmsto:")) return sms(t);
        if (low.startsWith("geo:")) {
            String rest = t.substring(4);
            String coords = rest.split("[?;]", 2)[0];
            String[] p = coords.split(",");
            if (p.length >= 2 && isNumber(p[0].trim()) && isNumber(p[1].trim())) return simple(Kind.GEO, t);
            return simple(Kind.TEXT, null);
        }
        return simple(Kind.TEXT, null);
    }

    private static boolean containsWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isWhitespace(s.charAt(i))) return true;
        return false;
    }

    private static boolean isNumber(String s) {
        try { Double.parseDouble(s); return !s.isEmpty(); } catch (NumberFormatException e) { return false; }
    }

    /** WIFI:T:WPA;S:name;P:pass;H:true;; with backslash escapes for \ ; , : " */
    private static ContentType wifi(String t) {
        String ssid = null, pass = null, sec = null;
        boolean hidden = false;
        String body = t.substring(5);
        int i = 0;
        while (i < body.length()) {
            int colon = body.indexOf(':', i);
            if (colon < 0) break;
            String key = body.substring(i, colon).trim().toUpperCase(Locale.ROOT);
            StringBuilder v = new StringBuilder();
            int j = colon + 1;
            for (; j < body.length(); j++) {
                char c = body.charAt(j);
                if (c == '\\' && j + 1 < body.length()) { v.append(body.charAt(++j)); continue; }
                if (c == ';') break;
                v.append(c);
            }
            String val = v.toString();
            switch (key) {
                case "S": ssid = val; break;
                case "P": pass = val; break;
                case "T": sec = val; break;
                case "H": hidden = "true".equalsIgnoreCase(val); break;
                default: break;
            }
            i = j + 1;
            while (i < body.length() && body.charAt(i) == ';') i++;
        }
        if (ssid == null || ssid.isEmpty()) return simple(Kind.TEXT, null);
        if (sec != null && (sec.isEmpty() || sec.equalsIgnoreCase("nopass"))) sec = null;
        if (pass != null && pass.isEmpty()) pass = null;
        return new ContentType(Kind.WIFI, null, ssid, pass, sec, hidden, null, null, null, null);
    }

    private static ContentType mailto(String t) {
        String rest = t.substring(7);
        String addr = rest, subject = null, body = null;
        int q = rest.indexOf('?');
        if (q >= 0) {
            addr = rest.substring(0, q);
            for (String kv : rest.substring(q + 1).split("&")) {
                int e = kv.indexOf('=');
                if (e < 0) continue;
                String k = kv.substring(0, e).toLowerCase(Locale.ROOT), v = pctDecode(kv.substring(e + 1));
                if (k.equals("subject")) subject = v; else if (k.equals("body")) body = v;
            }
        }
        addr = pctDecode(addr).trim();
        if (addr.isEmpty() && subject == null && body == null) return simple(Kind.TEXT, null);
        return new ContentType(Kind.EMAIL, t, null, null, null, false, null, addr, subject, body);
    }

    /** MATMSG:TO:a@b;SUB:s;BODY:b;; (DoCoMo style) */
    private static ContentType matmsg(String t) {
        String to = field(t, "TO:"), sub = field(t, "SUB:"), body = field(t, "BODY:");
        if (to == null || to.isEmpty()) return simple(Kind.TEXT, null);
        StringBuilder u = new StringBuilder("mailto:").append(to);
        String sep = "?";
        if (sub != null) { u.append(sep).append("subject=").append(pctEncode(sub)); sep = "&"; }
        if (body != null) u.append(sep).append("body=").append(pctEncode(body));
        return new ContentType(Kind.EMAIL, u.toString(), null, null, null, false, null, to, sub, body);
    }

    private static String field(String t, String key) {
        int i = t.toUpperCase(Locale.ROOT).indexOf(key);
        if (i < 0) return null;
        int s = i + key.length();
        int e = t.indexOf(';', s);
        return (e < 0 ? t.substring(s) : t.substring(s, e)).trim();
    }

    /** smsto:NUMBER:BODY, sms:NUMBER?body=BODY */
    private static ContentType sms(String t) {
        int c = t.indexOf(':');
        String rest = t.substring(c + 1);
        String num, body = null;
        int q = rest.indexOf('?');
        if (q >= 0) {
            num = rest.substring(0, q);
            for (String kv : rest.substring(q + 1).split("&"))
                if (kv.toLowerCase(Locale.ROOT).startsWith("body=")) body = pctDecode(kv.substring(5));
        } else {
            int c2 = rest.indexOf(':');
            if (c2 >= 0) { num = rest.substring(0, c2); body = rest.substring(c2 + 1); }
            else num = rest;
        }
        num = num.trim();
        if (num.isEmpty()) return simple(Kind.TEXT, null);
        return new ContentType(Kind.SMS, "smsto:" + num, null, null, null, false, num, null, null, body);
    }

    static String pctDecode(String s) {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length() && hex(s.charAt(i + 1)) >= 0 && hex(s.charAt(i + 2)) >= 0) {
                b.write(hex(s.charAt(i + 1)) * 16 + hex(s.charAt(i + 2)));
                i += 2;
            } else {
                byte[] u = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                b.write(u, 0, u.length);
            }
        }
        return new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    static String pctEncode(String s) {
        StringBuilder o = new StringBuilder();
        for (byte b : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') o.append((char) c);
            else o.append('%').append("0123456789ABCDEF".charAt(c >> 4)).append("0123456789ABCDEF".charAt(c & 15));
        }
        return o.toString();
    }
}
