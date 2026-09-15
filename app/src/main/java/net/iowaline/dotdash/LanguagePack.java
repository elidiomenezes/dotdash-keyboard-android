package net.iowaline.dotdash;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** A replaceable, offline language data package. */
final class LanguagePack {
    final String tag;
    final String shortName;
    private final List<String> words;

    LanguagePack(String tag, String shortName, String... words) {
        this.tag = tag;
        this.shortName = shortName;
        this.words = Arrays.asList(words);
    }

    List<String> suggest(String rawPrefix, int limit) {
        String prefix = fold(rawPrefix);
        List<String> matches = new ArrayList<>();
        if (prefix.isEmpty()) return matches;
        for (String word : words) {
            if (!word.equalsIgnoreCase(rawPrefix) && fold(word).startsWith(prefix)) {
                matches.add(word);
            }
        }
        matches.sort(Comparator.comparingInt(String::length).thenComparing(String::compareTo));
        return matches.subList(0, Math.min(limit, matches.size()));
    }

    private String fold(String value) {
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }
}
