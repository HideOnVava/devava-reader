package com.devavaxp.reader.data;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small text helpers with no JavaFX dependencies.
 */
public final class TextUtils {

    private static final Pattern CHUNKS = Pattern.compile("(\\d+)|(\\D+)");

    private TextUtils() {
    }

    /** "Volume 2" sorts before "Volume 10": digit runs are compared by numeric value. */
    public static int compareNatural(String a, String b) {
        Matcher ma = CHUNKS.matcher(a == null ? "" : a);
        Matcher mb = CHUNKS.matcher(b == null ? "" : b);
        while (true) {
            boolean ha = ma.find();
            boolean hb = mb.find();
            if (!ha || !hb) return Boolean.compare(ha, hb);
            String ta = ma.group();
            String tb = mb.group();
            int cmp;
            if (ma.group(1) != null && mb.group(1) != null) {
                cmp = compareNumbers(ta, tb);
            } else {
                cmp = ta.compareToIgnoreCase(tb);
            }
            if (cmp != 0) return cmp;
        }
    }

    private static int compareNumbers(String a, String b) {
        String na = a.replaceFirst("^0+(?=\\d)", "");
        String nb = b.replaceFirst("^0+(?=\\d)", "");
        if (na.length() != nb.length()) return Integer.compare(na.length(), nb.length());
        int cmp = na.compareTo(nb);
        return cmp != 0 ? cmp : Integer.compare(a.length(), b.length());
    }

    /** Default title of a volume: the file name without the .epub extension. */
    public static String titleFromFile(File f) {
        String name = f.getName().replaceAll("(?i)\\.epub$", "").trim();
        return name.isEmpty() ? f.getName() : name;
    }

    /** "1 volume" / "3 volumes". */
    public static String plural(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
