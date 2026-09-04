package com.opencorrector.download;

import android.util.Log;

import com.opencorrector.inference.LlamaModel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Downloads one GGUF model file with resume support (HTTP Range) and SHA-256 verification.
 * Writes to "<file>.part" first and only renames to the final name after the checksum matches,
 * so a partially downloaded or corrupted file is never mistaken for a usable model.
 */
final class DownloadTask implements Runnable {

    private static final String TAG = "DownloadTask";

    private final LlamaModel model;
    private final File targetDir;
    private final DownloadProgressListener listener;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    DownloadTask(LlamaModel model, File targetDir, DownloadProgressListener listener) {
        this.model = model;
        this.targetDir = targetDir;
        this.listener = listener;
    }

    void cancel() {
        cancelled.set(true);
    }

    @Override
    public void run() {
        File finalFile = new File(targetDir, model.fileName);
        File partFile = new File(targetDir, model.fileName + ".part");

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            listener.onError("error_download_failed", "cannot create model directory");
            return;
        }

        long existingBytes = partFile.exists() ? partFile.length() : 0L;
        if (existingBytes >= model.sizeBytes) {
            // Stale/complete .part from a previous run; restart cleanly.
            existingBytes = 0L;
            //noinspection ResultOfMethodCallIgnored
            partFile.delete();
        }

        Request.Builder requestBuilder = new Request.Builder().url(model.downloadUrl);
        if (existingBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=" + existingBytes + "-");
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                listener.onError("error_download_failed", "HTTP " + response.code());
                return;
            }

            boolean resumed = response.code() == 206;
            long startOffset = resumed ? existingBytes : 0L;
            if (!resumed && existingBytes > 0) {
                // Server ignored the Range request; restart from scratch to stay correct.
                startOffset = 0L;
            }

            long totalBytes = model.sizeBytes;
            long downloaded = startOffset;

            try (InputStream input = response.body().byteStream();
                 RandomAccessFile output = new RandomAccessFile(partFile, "rw")) {
                output.seek(startOffset);
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (cancelled.get()) {
                        listener.onCancelled();
                        return;
                    }
                    output.write(buffer, 0, read);
                    downloaded += read;
                    listener.onProgress(downloaded, totalBytes);
                }
            }

            if (cancelled.get()) {
                listener.onCancelled();
                return;
            }

            if (!ChecksumVerifier.verify(partFile, model.sha256)) {
                //noinspection ResultOfMethodCallIgnored
                partFile.delete();
                listener.onError("error_checksum_mismatch", "sha256 mismatch for " + model.fileName);
                return;
            }

            if (!partFile.renameTo(finalFile)) {
                listener.onError("error_download_failed", "rename to final file failed");
                return;
            }

            listener.onCompleted(finalFile);
        } catch (IOException e) {
            if (cancelled.get()) {
                listener.onCancelled();
            } else {
                Log.e(TAG, "Download failed: " + e.getClass().getSimpleName());
                listener.onError("error_download_failed", e.getClass().getSimpleName());
            }
        }
    }
}
