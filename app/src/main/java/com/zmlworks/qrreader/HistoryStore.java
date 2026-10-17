package com.zmlworks.qrreader;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads the history file once; every change is written in the background (write to .tmp, then rename). */
final class HistoryStore {
    private final File file, tmp;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    final History history;

    HistoryStore(Context c) {
        file = new File(c.getFilesDir(), "history.tsv");
        tmp = new File(c.getFilesDir(), "history.tsv.tmp");
        String s = null;
        try { if (file.isFile()) s = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); } catch (IOException ignored) { }
        history = History.parse(s);
        history.addListener(this::save);
    }

    private void save() {
        final String data = history.serialize();
        io.execute(() -> {
            try (FileOutputStream os = new FileOutputStream(tmp)) {
                os.write(data.getBytes(StandardCharsets.UTF_8));
                os.getFD().sync();
            } catch (IOException e) { return; }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(file);
        });
    }
}
