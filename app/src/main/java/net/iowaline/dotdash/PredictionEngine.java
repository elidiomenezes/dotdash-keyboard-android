package net.iowaline.dotdash;

import android.content.res.AssetManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline predictor backed by independently replaceable language assets. */
final class PredictionEngine {
    private final Map<String, LanguagePack> packs = new LinkedHashMap<>();
    private String currentTag = "pt-BR";

    PredictionEngine(AssetManager assets) {
        addPack(assets, "pt-BR", "PT", "language/pt-BR.txt.gz");
        addPack(assets, "en-US", "EN", "language/en-US.txt.gz");
    }

    private void addPack(AssetManager assets, String tag, String label, String path) {
        try {
            packs.put(tag, new LanguagePack(assets, tag, label, path));
        } catch (IOException ignored) {
            // A broken optional pack must not make the input method unusable.
        }
    }

    List<String> suggest(String prefix) {
        LanguagePack pack = packs.get(currentTag);
        return pack == null ? Collections.emptyList() : pack.suggest(prefix, 3);
    }

    LanguagePack current() { return packs.get(currentTag); }

    String currentLabel() {
        LanguagePack pack = current();
        return pack == null ? "--" : pack.shortName;
    }

    LanguagePack next() {
        List<String> tags = Arrays.asList(packs.keySet().toArray(new String[0]));
        if (tags.isEmpty()) return null;
        int index = tags.indexOf(currentTag);
        currentTag = tags.get((index + 1) % tags.size());
        return current();
    }
}
