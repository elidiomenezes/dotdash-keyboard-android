package net.iowaline.dotdash;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded, on-device unigram and bigram learning. */
final class PersonalLanguageModel extends SQLiteOpenHelper {
    private static final String DB_NAME = "personal_language.db";
    private static final int DB_VERSION = 1;
    private static final int MAX_WORDS_PER_LANGUAGE = 5000;
    private static final int MAX_BIGRAMS_PER_LANGUAGE = 10000;

    private static final class Entry {
        final String word;
        int count;
        long lastUsed;
        Entry(String word, int count, long lastUsed) {
            this.word = word;
            this.count = count;
            this.lastUsed = lastUsed;
        }
    }

    private final Map<String, Map<String, Entry>> words = new HashMap<>();
    private final Map<String, Map<String, Entry>> bigrams = new HashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private int writesUntilCleanup = 100;

    PersonalLanguageModel(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        worker.execute(this::loadCache);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE words (language TEXT NOT NULL, normalized TEXT NOT NULL,"
                + " word TEXT NOT NULL, count INTEGER NOT NULL, last_used INTEGER NOT NULL,"
                + " PRIMARY KEY(language, normalized))");
        db.execSQL("CREATE TABLE bigrams (language TEXT NOT NULL, previous TEXT NOT NULL,"
                + " normalized TEXT NOT NULL, word TEXT NOT NULL, count INTEGER NOT NULL,"
                + " last_used INTEGER NOT NULL, PRIMARY KEY(language, previous, normalized))");
        db.execSQL("CREATE INDEX bigram_lookup ON bigrams(language, previous)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS bigrams");
        db.execSQL("DROP TABLE IF EXISTS words");
        onCreate(db);
    }

    void learn(String language, String previous, String word) {
        String normalized = fold(word);
        String previousNormalized = fold(previous);
        if (normalized.length() < 2 || normalized.length() > 48) return;
        long now = System.currentTimeMillis();
        synchronized (words) {
            Map<String, Entry> languageWords =
                    words.computeIfAbsent(language, ignored -> new HashMap<>());
            Entry entry = languageWords.get(normalized);
            if (entry != null) {
                entry.count++;
                entry.lastUsed = now;
            } else if (languageWords.size() < MAX_WORDS_PER_LANGUAGE) {
                languageWords.put(normalized, new Entry(word, 1, now));
            }
            if (!previousNormalized.isEmpty()) {
                String key = previousNormalized + '\0' + normalized;
                Map<String, Entry> languageBigrams =
                        bigrams.computeIfAbsent(language, ignored -> new HashMap<>());
                Entry pair = languageBigrams.get(key);
                if (pair != null) {
                    pair.count++;
                    pair.lastUsed = now;
                } else if (languageBigrams.size() < MAX_BIGRAMS_PER_LANGUAGE) {
                    languageBigrams.put(key, new Entry(word, 1, now));
                }
            }
        }
        worker.execute(() -> persist(language, previousNormalized, normalized, word, now));
    }

    List<String> suggest(String language, String previous, String prefix, int limit) {
        String previousNormalized = fold(previous);
        String foldedPrefix = fold(prefix);
        List<Entry> candidates = new ArrayList<>();
        synchronized (words) {
            Map<String, Entry> pairs = bigrams.get(language);
            if (pairs != null && !previousNormalized.isEmpty()) {
                String start = previousNormalized + '\0';
                for (Map.Entry<String, Entry> item : pairs.entrySet()) {
                    if (item.getKey().startsWith(start)
                            && fold(item.getValue().word).startsWith(foldedPrefix)) {
                        candidates.add(item.getValue());
                    }
                }
            }
            Map<String, Entry> languageWords = words.get(language);
            if (languageWords != null) {
                for (Map.Entry<String, Entry> item : languageWords.entrySet()) {
                    if (item.getKey().startsWith(foldedPrefix)) candidates.add(item.getValue());
                }
            }
        }
        candidates.sort(Comparator
                .comparingInt((Entry entry) -> entry.count).reversed()
                .thenComparing(Comparator.comparingLong(
                        (Entry entry) -> entry.lastUsed).reversed()));
        Set<String> unique = new LinkedHashSet<>();
        for (Entry entry : candidates) {
            if (!entry.word.equalsIgnoreCase(prefix)) unique.add(entry.word);
            if (unique.size() == limit) break;
        }
        return new ArrayList<>(unique);
    }

    private void loadCache() {
        if (closed) return;
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query("words",
                new String[]{"language", "normalized", "word", "count", "last_used"},
                null, null, null, null, null)) {
            synchronized (words) {
                while (cursor.moveToNext()) {
                    words.computeIfAbsent(cursor.getString(0), ignored -> new HashMap<>())
                            .put(cursor.getString(1),
                                    new Entry(cursor.getString(2), cursor.getInt(3), cursor.getLong(4)));
                }
            }
        }
        try (Cursor cursor = db.query("bigrams",
                new String[]{"language", "previous", "normalized", "word", "count", "last_used"},
                null, null, null, null, null)) {
            synchronized (words) {
                while (cursor.moveToNext()) {
                    String key = cursor.getString(1) + '\0' + cursor.getString(2);
                    bigrams.computeIfAbsent(cursor.getString(0), ignored -> new HashMap<>())
                            .put(key, new Entry(cursor.getString(3), cursor.getInt(4), cursor.getLong(5)));
                }
            }
        }
    }

    private void persist(String language, String previous, String normalized,
                         String word, long now) {
        if (closed) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            upsert(db, "words", language, null, normalized, word, now);
            if (!previous.isEmpty()) upsert(db, "bigrams", language, previous, normalized, word, now);
            if (--writesUntilCleanup == 0) {
                trim(db, "words", language, MAX_WORDS_PER_LANGUAGE);
                trim(db, "bigrams", language, MAX_BIGRAMS_PER_LANGUAGE);
                writesUntilCleanup = 100;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void upsert(SQLiteDatabase db, String table, String language,
                               String previous, String normalized, String word, long now) {
        ContentValues update = new ContentValues();
        update.put("word", word);
        update.put("last_used", now);
        String where = "language=? AND normalized=?";
        List<String> args = new ArrayList<>();
        args.add(language);
        args.add(normalized);
        if (previous != null) {
            where += " AND previous=?";
            args.add(previous);
        }
        db.execSQL("UPDATE " + table + " SET count=count+1, word=?, last_used=? WHERE " + where,
                concat(new Object[]{word, now}, args));
        try (Cursor cursor = db.rawQuery("SELECT changes()", null)) {
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) return;
        }
        ContentValues insert = new ContentValues(update);
        insert.put("language", language);
        insert.put("normalized", normalized);
        insert.put("count", 1);
        if (previous != null) insert.put("previous", previous);
        db.insertWithOnConflict(table, null, insert, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static Object[] concat(Object[] prefix, List<String> suffix) {
        Object[] result = new Object[prefix.length + suffix.size()];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        for (int i = 0; i < suffix.size(); i++) result[prefix.length + i] = suffix.get(i);
        return result;
    }

    private static void trim(SQLiteDatabase db, String table, String language, int limit) {
        db.execSQL("DELETE FROM " + table + " WHERE rowid IN (SELECT rowid FROM " + table
                + " WHERE language=? ORDER BY count DESC, last_used DESC LIMIT -1 OFFSET ?)",
                new Object[]{language, limit});
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        worker.execute(this::closeDatabase);
        worker.shutdown();
    }

    private void closeDatabase() {
        super.close();
    }

    private static String fold(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }
}
