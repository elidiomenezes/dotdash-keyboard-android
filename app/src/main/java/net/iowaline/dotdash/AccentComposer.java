package net.iowaline.dotdash;

import java.text.Normalizer;

final class AccentComposer {
    private static final String[] MARKS = {"", "\u0301", "\u0302", "\u0303", "\u0300", "\u0308", "\u0327"};
    private static final String[] LABELS = {"´", "´", "^", "~", "`", "¨", "¸"};
    private int selected = 0;
    private long lastTap;
    private static final long RAPID_TAP_MS = 1000;

    void tap(long now) {
        if (!pending()) {
            selected = 1;
        } else if (now - lastTap <= RAPID_TAP_MS) {
            selected = selected % (MARKS.length - 1) + 1;
        } else {
            selected = 0;
        }
        lastTap = now;
    }

    String label() { return pending() ? LABELS[selected] : "´"; }

    boolean pending() { return selected != 0; }

    String apply(String character) {
        if (!pending()) return character;
        if ("\u0327".equals(MARKS[selected]) && !"c".equalsIgnoreCase(character)) {
            selected = 0;
            return character;
        }
        String result = Normalizer.normalize(character + MARKS[selected], Normalizer.Form.NFC);
        selected = 0;
        return result;
    }

    void clear() { selected = 0; }
}
