package net.iowaline.dotdash;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline predictor with lazy, background-loaded language packs. */
final class PredictionEngine {
    interface Listener { void onSuggestionsChanged(); }

    private static final class PackSpec {
        final String tag;
        final String label;
        final String path;

        PackSpec(String tag, String label, String path) {
            this.tag = tag;
            this.label = label;
            this.path = path;
        }
    }

    private final AssetManager assets;
    private final Listener listener;
    private final Map<String, PackSpec> specs = new LinkedHashMap<>();
    private final Map<String, LanguagePack> loadedPacks = new LinkedHashMap<>();
    private final Map<String, NgramLanguageModel> languageModels = new LinkedHashMap<>();
    private final List<String> loadingTags = new ArrayList<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final SystemSpellChecker systemSpellChecker;
    private final PersonalLanguageModel personalModel;
    private volatile String currentTag = "pt-BR";
    private volatile boolean closed;

    PredictionEngine(Context context, Listener listener) {
        assets = context.getAssets();
        this.listener = listener;
        addPack("pt-BR", "PT", "language/pt-BR.dat");
        addPack("en-US", "EN", "language/en-US.dat");
        addModel("pt-BR", "language/pt-BR.ngram");
        addModel("en-US", "language/en-US.ngram");
        systemSpellChecker = new SystemSpellChecker(context, listener::onSuggestionsChanged);
        personalModel = new PersonalLanguageModel(context);
        systemSpellChecker.setLanguage(currentTag);
        loadPack(currentTag);
    }

    private void addPack(String tag, String label, String path) {
        specs.put(tag, new PackSpec(tag, label, path));
    }

    private void addModel(String tag, String path) {
        try {
            languageModels.put(tag, new NgramLanguageModel(assets, path));
        } catch (IOException ignored) {
            // Context prediction remains optional.
        }
    }

    private void loadPack(String tag) {
        final PackSpec spec = specs.get(tag);
        if (spec == null) return;
        synchronized (loadedPacks) {
            if (loadedPacks.containsKey(tag) || loadingTags.contains(tag) || closed) return;
            loadingTags.add(tag);
        }
        loader.execute(() -> {
            LanguagePack pack = null;
            try {
                pack = new LanguagePack(assets, spec.tag, spec.label, spec.path);
            } catch (IOException ignored) {
                // A broken optional pack must not make the keyboard unusable.
            }
            synchronized (loadedPacks) {
                loadingTags.remove(tag);
                if (!closed && pack != null) loadedPacks.put(tag, pack);
            }
            if (!closed && tag.equals(currentTag)) listener.onSuggestionsChanged();
        });
    }

    List<String> suggest(String textBeforeCursor) {
        NgramLanguageModel.Context context = NgramLanguageModel.contextOf(textBeforeCursor);
        LanguagePack pack;
        synchronized (loadedPacks) {
            pack = loadedPacks.get(currentTag);
        }
        List<String> local = pack == null || context.prefix.isEmpty()
                ? Collections.emptyList() : pack.suggest(context.prefix, 8);
        NgramLanguageModel model = languageModels.get(currentTag);
        List<String> predicted = model == null ? Collections.emptyList()
                : model.suggest(context.previousWord, context.prefix, 8);
        List<String> personal = personalModel.suggest(
                currentTag, context.previousWord, context.prefix, 8);
        return systemSpellChecker.suggest(context.prefix,
                NgramLanguageModel.merge(personal,
                        NgramLanguageModel.merge(predicted, local)), 3);
    }

    void learnCompletedWord(String textBeforeCursor) {
        NgramLanguageModel.Context context = NgramLanguageModel.contextOf(textBeforeCursor);
        if (!context.prefix.isEmpty()) {
            personalModel.learn(currentTag, context.previousWord, context.prefix);
        }
    }

    void learnAcceptedSuggestion(String textBeforeCursor, String suggestion) {
        NgramLanguageModel.Context context = NgramLanguageModel.contextOf(textBeforeCursor);
        personalModel.learn(currentTag, context.previousWord, suggestion);
    }

    String currentLabel() {
        PackSpec spec = specs.get(currentTag);
        return spec == null ? "--" : spec.label;
    }

    void next() {
        List<String> tags = Arrays.asList(specs.keySet().toArray(new String[0]));
        if (tags.isEmpty()) return;
        int index = tags.indexOf(currentTag);
        currentTag = tags.get((index + 1) % tags.size());
        systemSpellChecker.setLanguage(currentTag);
        loadPack(currentTag);
    }

    void close() {
        closed = true;
        loader.shutdownNow();
        systemSpellChecker.close();
        personalModel.close();
        synchronized (loadedPacks) {
            loadedPacks.clear();
            loadingTags.clear();
        }
    }
}
