package net.iowaline.dotdash;

import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** A frequency-ranked, offline language data package. */
final class LanguagePack {
    private static final Pattern WORD_PATTERN = Pattern.compile("\\p{L}+(?:[-’']\\p{L}+)*");
    final String tag;
    final String shortName;
    private final Map<String, List<String>> prefixIndex = new HashMap<>();

    LanguagePack(AssetManager assets, String tag, String shortName, String assetPath)
            throws IOException {
        this.tag = tag;
        this.shortName = shortName;
        load(assets, assetPath);
    }

    private void load(AssetManager assets, String assetPath) throws IOException {
        Set<String> seen = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(assets.open(assetPath)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.lastIndexOf(' ');
                String word = separator > 0 ? line.substring(0, separator) : line;
                if (word.length() < 2 || !WORD_PATTERN.matcher(word).matches()) continue;
                String folded = fold(word);
                if (!seen.add(folded + '\0' + word)) continue;
                addToIndex(folded.substring(0, 1), word);
                if (folded.length() >= 2) addToIndex(folded.substring(0, 2), word);
            }
        }
    }

    private void addToIndex(String key, String word) {
        prefixIndex.computeIfAbsent(key, ignored -> new ArrayList<>()).add(word);
    }

    List<String> suggest(String rawPrefix, int limit) {
        String prefix = fold(rawPrefix);
        if (prefix.isEmpty()) return Collections.emptyList();
        String bucketKey = prefix.substring(0, Math.min(2, prefix.length()));
        List<String> bucket = prefixIndex.get(bucketKey);
        if (bucket == null) return Collections.emptyList();
        List<String> matches = new ArrayList<>(limit);
        for (String word : bucket) {
            if (!word.equalsIgnoreCase(rawPrefix) && fold(word).startsWith(prefix)) {
                matches.add(word);
                if (matches.size() == limit) break;
            }
        }
        return matches;
    }

    private String fold(String value) {
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }
}
