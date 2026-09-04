package com.opencorrector.download;

import java.io.File;

/** Callbacks are delivered on a background thread; the caller must post to the UI thread itself. */
public interface DownloadProgressListener {
    void onProgress(long bytesDownloaded, long totalBytes);
    void onCompleted(File modelFile);
    void onError(String errorMessageResKey, String debugDetail);
    void onCancelled();
}
