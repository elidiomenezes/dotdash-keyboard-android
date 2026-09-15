package net.iowaline.dotdash;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small MVP predictor. Language packs can later be loaded from signed files. */
final class PredictionEngine {
    private final Map<String, LanguagePack> packs = new LinkedHashMap<>();
    private String currentTag = "pt-BR";

    PredictionEngine() {
        add(new LanguagePack("pt-BR", "PT",
                "ainda", "agora", "amanhã", "antes", "aqui", "assim", "até", "bem",
                "boa", "bom", "casa", "certeza", "coisa", "como", "com", "depois",
                "dia", "então", "está", "estão", "fazer", "feito", "gente", "hoje",
                "isso", "mais", "melhor", "mesmo", "muito", "não", "obrigado", "onde",
                "para", "pode", "porque", "por", "preciso", "quando", "que", "sim",
                "também", "tenho", "tudo", "você", "vamos"));
        add(new LanguagePack("en-US", "EN",
                "about", "after", "again", "also", "because", "before", "better", "can",
                "could", "day", "good", "have", "hello", "here", "how", "just", "know",
                "like", "more", "much", "need", "now", "only", "please", "right", "should",
                "something", "still", "than", "thank", "that", "then", "there", "they",
                "think", "this", "today", "very", "want", "well", "what", "when", "where",
                "which", "with", "would", "yes", "you"));
    }

    private void add(LanguagePack pack) { packs.put(pack.tag, pack); }

    List<String> suggest(String prefix) {
        LanguagePack pack = packs.get(currentTag);
        return pack == null ? Collections.emptyList() : pack.suggest(prefix, 3);
    }

    LanguagePack current() { return packs.get(currentTag); }

    LanguagePack next() {
        List<String> tags = Arrays.asList(packs.keySet().toArray(new String[0]));
        int index = tags.indexOf(currentTag);
        currentTag = tags.get((index + 1) % tags.size());
        return current();
    }
}
