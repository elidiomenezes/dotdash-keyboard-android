package net.iowaline.dotdash;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline predictor backed by independently replaceable language assets. */
final class PredictionEngine {
    interface Listener { void onSuggestionsChanged(); }

    private final Map<String, LanguagePack> packs = new LinkedHashMap<>();
    private final SystemSpellChecker systemSpellChecker;
    private String currentTag = "pt-BR";

    PredictionEngine(Context context, Listener listener) {
        AssetManager assets = context.getAssets();
        addPack(assets, "pt-BR", "PT", "language/pt-BR.dat");
        addPack(assets, "en-US", "EN", "language/en-US.dat");
        systemSpellChecker = new SystemSpellChecker(context, listener::onSuggestionsChanged);
        systemSpellChecker.setLanguage(currentTag);
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
        List<String> local = pack == null ? Collections.emptyList() : pack.suggest(prefix, 6);
        return systemSpellChecker.suggest(prefix, local, 3);
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
        systemSpellChecker.setLanguage(currentTag);
        return current();
    }

    void close() { systemSpellChecker.close(); }
}
