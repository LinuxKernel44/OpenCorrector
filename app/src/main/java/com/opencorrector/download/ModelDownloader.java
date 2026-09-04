package com.opencorrector.download;

import android.content.Context;

import com.opencorrector.inference.LlamaModel;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Public entry point for downloading/managing model files. Models are stored under the app's
 * private internal storage (getFilesDir()/models), never in public/shared storage, and are
 * never bundled in the APK.
 */
public final class ModelDownloader {

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile DownloadTask activeTask;

    public ModelDownloader(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static File modelsDir(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "models");
    }

    public static File modelFile(Context context, LlamaModel model) {
        return new File(modelsDir(context), model.fileName);
    }

    public static boolean isDownloaded(Context context, LlamaModel model) {
        File file = modelFile(context, model);
        return file.exists() && file.length() == model.sizeBytes;
    }

    public void startDownload(LlamaModel model, DownloadProgressListener listener) {
        DownloadTask task = new DownloadTask(model, modelsDir(appContext), listener);
        activeTask = task;
        executor.execute(task);
    }

    public void cancelDownload() {
        DownloadTask task = activeTask;
        if (task != null) {
            task.cancel();
        }
    }

    public boolean deleteModel(LlamaModel model) {
        File file = modelFile(appContext, model);
        return !file.exists() || file.delete();
    }

    public void shutdown() {
        executor.shutdown();
    }
}
