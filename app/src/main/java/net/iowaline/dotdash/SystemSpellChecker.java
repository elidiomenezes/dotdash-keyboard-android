package net.iowaline.dotdash;

import android.content.Context;
import android.os.Bundle;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Adds corrections from the spell-checker selected in Android settings. */
final class SystemSpellChecker implements SpellCheckerSession.SpellCheckerSessionListener {
    interface Listener { void onResult(); }

    private final TextServicesManager manager;
    private final Listener listener;
    private SpellCheckerSession session;
    private String languageTag;
    private String requestedWord = "";
    private String resultWord = "";
    private List<String> corrections = new ArrayList<>();

    SystemSpellChecker(Context context, Listener listener) {
        manager = (TextServicesManager) context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
        this.listener = listener;
    }

    void setLanguage(String tag) {
        if (tag.equals(languageTag)) return;
        languageTag = tag;
        if (session != null) session.close();
        session = manager == null ? null : manager.newSpellCheckerSession(
                Bundle.EMPTY, Locale.forLanguageTag(tag), this, true);
        requestedWord = "";
        resultWord = "";
        corrections = new ArrayList<>();
    }

    List<String> suggest(String word, List<String> local, int limit) {
        String normalized = fold(word);
        if (session != null && word.length() >= 2 && !normalized.equals(requestedWord)) {
            requestedWord = normalized;
            session.getSuggestions(new TextInfo(word), Math.max(5, limit));
        }

        Set<String> merged = new LinkedHashSet<>();
        if (normalized.equals(resultWord)) {
            for (String correction : corrections) {
                if (!correction.equalsIgnoreCase(word)) merged.add(correction);
            }
        }
        for (String completion : local) {
            if (!completion.equalsIgnoreCase(word)) merged.add(completion);
        }
        List<String> output = new ArrayList<>(limit);
        for (String candidate : merged) {
            output.add(candidate);
            if (output.size() == limit) break;
        }
        return output;
    }

    @Override
    public void onGetSuggestions(SuggestionsInfo[] results) {
        if (results == null || results.length == 0) return;
        SuggestionsInfo info = results[0];
        List<String> next = new ArrayList<>();
        for (int i = 0; i < info.getSuggestionsCount(); i++) {
            String candidate = info.getSuggestionAt(i);
            if (candidate != null && !candidate.isEmpty()) next.add(candidate);
        }
        resultWord = requestedWord;
        corrections = next;
        listener.onResult();
    }

    @Override
    public void onGetSentenceSuggestions(android.view.textservice.SentenceSuggestionsInfo[] results) {
        // Word-level requests are used so this callback is intentionally unused.
    }

    void close() {
        if (session != null) session.close();
        session = null;
    }

    private static String fold(String value) {
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }
}
