package net.iowaline.dotdash;

import android.content.res.AssetManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Replaceable next-word model. Rows contain a previous word and ranked candidates. */
final class NgramLanguageModel {
    static final class Context {
        final String previousWord;
        final String prefix;
        Context(String previousWord, String prefix) {
            this.previousWord = previousWord;
            this.prefix = prefix;
        }
    }

    private final Map<String, List<String>> nextWords = new LinkedHashMap<>();

    NgramLanguageModel(AssetManager assets, String path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                assets.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] columns = line.split("\\t");
                if (columns.length < 2) continue;
                List<String> words = new ArrayList<>();
                Collections.addAll(words, columns[1].split(" "));
                nextWords.put(fold(columns[0]), words);
            }
        }
    }

    List<String> suggest(String previousWord, String prefix, int limit) {
        List<String> row = nextWords.get(fold(previousWord));
        if (row == null) row = nextWords.get("*");
        if (row == null) return Collections.emptyList();
        String foldedPrefix = fold(prefix);
        List<String> result = new ArrayList<>();
        for (String word : row) {
            if ((foldedPrefix.isEmpty() || fold(word).startsWith(foldedPrefix))
                    && !word.equalsIgnoreCase(prefix)) {
                result.add(word);
                if (result.size() == limit) break;
            }
        }
        return result;
    }

    static Context contextOf(String text) {
        boolean afterSeparator = text.isEmpty()
                || !Character.isLetter(text.charAt(text.length() - 1));
        List<String> words = new ArrayList<>();
        int end = text.length();
        while (end > 0) {
            while (end > 0 && !Character.isLetter(text.charAt(end - 1))) end--;
            int start = end;
            while (start > 0 && Character.isLetter(text.charAt(start - 1))) start--;
            if (start < end) words.add(0, text.substring(start, end));
            end = start;
            if (words.size() == 2) break;
        }
        String prefix = afterSeparator || words.isEmpty() ? "" : words.get(words.size() - 1);
        int previousIndex = afterSeparator ? words.size() - 1 : words.size() - 2;
        String previous = previousIndex >= 0 ? words.get(previousIndex) : "*";
        return new Context(previous, prefix);
    }

    static List<String> merge(List<String> first, List<String> second) {
        Set<String> unique = new LinkedHashSet<>(first);
        unique.addAll(second);
        return new ArrayList<>(unique);
    }

    private static String fold(String value) {
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }
}
