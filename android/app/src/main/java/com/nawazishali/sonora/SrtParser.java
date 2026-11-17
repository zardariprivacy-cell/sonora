package com.nawazishali.sonora;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal SubRip (.srt) parser: cue list with start/end ms and plain text. */
public class SrtParser {
    public static class Cue { public long start, end; public String text; }

    private static final Pattern TIME = Pattern.compile(
            "(\\d+):(\\d+):(\\d+)[,.](\\d+)\\s*-->\\s*(\\d+):(\\d+):(\\d+)[,.](\\d+)");

    public static List<Cue> parse(InputStream in) throws Exception {
        List<Cue> cues = new ArrayList<>();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        Cue cur = null;
        StringBuilder text = new StringBuilder();
        while ((line = r.readLine()) != null) {
            line = line.trim();
            Matcher m = TIME.matcher(line);
            if (m.find()) {
                if (cur != null) { cur.text = text.toString().trim(); cues.add(cur); }
                cur = new Cue();
                cur.start = toMs(m, 1); cur.end = toMs(m, 5);
                text.setLength(0);
            } else if (line.isEmpty()) {
                if (cur != null && text.length() > 0) { cur.text = text.toString().trim(); cues.add(cur); cur = null; text.setLength(0); }
            } else if (cur != null && !line.matches("\\d+")) {
                if (text.length() > 0) text.append('\n');
                text.append(line.replaceAll("<[^>]+>", ""));
            }
        }
        if (cur != null) { cur.text = text.toString().trim(); cues.add(cur); }
        r.close();
        return cues;
    }

    private static long toMs(Matcher m, int g) {
        long h = Long.parseLong(m.group(g)), min = Long.parseLong(m.group(g + 1));
        long s = Long.parseLong(m.group(g + 2)), ms = Long.parseLong(m.group(g + 3));
        return ((h * 60 + min) * 60 + s) * 1000 + ms;
    }

    /** Binary-search the cue visible at position ms (or null). */
    public static Cue at(List<Cue> cues, long ms) {
        if (cues == null || cues.isEmpty()) return null;
        int lo = 0, hi = cues.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (cues.get(mid).start <= ms) { found = mid; lo = mid + 1; } else hi = mid - 1;
        }
        if (found < 0) return null;
        Cue c = cues.get(found);
        return ms <= c.end ? c : null;
    }
}
