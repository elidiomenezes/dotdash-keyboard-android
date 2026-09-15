package net.iowaline.dotdash;

import java.text.Normalizer;

final class AccentComposer {
    private static final String[] MARKS = {"", "\u0301", "\u0302", "\u0303", "\u0300", "\u0308"};
    private static final String[] LABELS = {"´", "´", "^", "~", "`", "¨"};
    private int selected = 0;

    String nextLabel() {
        selected = selected % (MARKS.length - 1) + 1;
        return LABELS[selected];
    }

    boolean pending() { return selected != 0; }

    String apply(String character) {
        if (!pending()) return character;
        String result = Normalizer.normalize(character + MARKS[selected], Normalizer.Form.NFC);
        selected = 0;
        return result;
    }

    void clear() { selected = 0; }
}
