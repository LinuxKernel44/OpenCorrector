package com.opencorrector.inference;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.opencorrector.R;
import com.opencorrector.prompt.CorrectionMode;

import java.io.File;

/**
 * Bound Service that owns the {@link LlamaEngine} so the model survives across multiple
 * ProcessTextActivity popups without being reloaded each time. Runs as a foreground service
 * only while actively loading a model or generating text (CPU-heavy work that must not be
 * killed by the system); it drops back to a background service the instant that work finishes.
 *
 * The model is unloaded automatically after {@link InferenceConfig#unloadDelayMillis} of
 * inactivity so ~1 GB of RAM isn't held forever (see resetAutoUnloadTimer()).
 */
public final class LlamaService extends Service {

    public static final String CHANNEL_ID = "opencorrector_inference";
    private static final int NOTIFICATION_ID = 1;

    public interface LoadCallback {
        void onLoaded();
        void onError(EngineException.Code code);
    }

    private final IBinder binder = new LocalBinder();
    private LlamaEngine engine;
    private Handler mainHandler;
    private Runnable pendingUnloadRunnable;
    private volatile long currentUnloadDelayMillis = 5 * 60_000L;

    public class LocalBinder extends android.os.Binder {
        public LlamaService getService() {
            return LlamaService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        engine = new LlamaEngine(this);
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        cancelAutoUnloadTimer();
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        resetAutoUnloadTimer();
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        cancelAutoUnloadTimer();
    }

    @Override
    public void onDestroy() {
        cancelAutoUnloadTimer();
        engine.shutdown();
        super.onDestroy();
    }

    public boolean isModelLoaded() {
        return engine.isLoaded();
    }

    public LlamaModel getLoadedModel() {
        return engine.getLoadedModel();
    }

    public boolean isGenerating() {
        return engine.isGenerating();
    }

    /** Loads (or confirms already loaded) the given model. Safe to call even if already loaded. */
    public void loadModel(LlamaModel model, File modelFile, InferenceConfig config, LoadCallback callback) {
        currentUnloadDelayMillis = config.unloadDelayMillis;
        cancelAutoUnloadTimer();
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.service_notification_title_loading)));

        new Thread(() -> {
            try {
                engine.loadBlocking(model, modelFile.getAbsolutePath(), config);
                mainHandler.post(() -> {
                    stopForegroundCompat();
                    resetAutoUnloadTimer();
                    callback.onLoaded();
                });
            } catch (EngineException e) {
                mainHandler.post(() -> {
                    stopForegroundCompat();
                    resetAutoUnloadTimer();
                    callback.onError(e.code);
                });
            }
        }, "opencorrector-model-load").start();
    }

    /** Generates a correction for the given text. Assumes the model is already loaded. */
    public void generate(String languageCode, CorrectionMode mode, String text, InferenceCallback callback) {
        cancelAutoUnloadTimer();
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.service_notification_title_generating)));

        engine.process(languageCode, mode, text, new InferenceCallback() {
            @Override
            public void onChunkingStarted(int totalChunks) {
                mainHandler.post(() -> callback.onChunkingStarted(totalChunks));
            }

            @Override
            public void onPartial(String textSoFar, int chunkIndex, int totalChunks) {
                mainHandler.post(() -> callback.onPartial(textSoFar, chunkIndex, totalChunks));
            }

            @Override
            public void onComplete(String finalText) {
                mainHandler.post(() -> {
                    stopForegroundCompat();
                    resetAutoUnloadTimer();
                    callback.onComplete(finalText);
                });
            }

            @Override
            public void onError(EngineException.Code code) {
                mainHandler.post(() -> {
                    stopForegroundCompat();
                    resetAutoUnloadTimer();
                    callback.onError(code);
                });
            }

            @Override
            public void onCancelled() {
                mainHandler.post(() -> {
                    stopForegroundCompat();
                    resetAutoUnloadTimer();
                    callback.onCancelled();
                });
            }
        });
    }

    public void cancelGeneration() {
        engine.cancel();
    }

    private void resetAutoUnloadTimer() {
        cancelAutoUnloadTimer();
        if (!engine.isLoaded()) {
            return;
        }
        pendingUnloadRunnable = () -> {
            if (!engine.isGenerating()) {
                engine.unloadBlocking();
            }
        };
        mainHandler.postDelayed(pendingUnloadRunnable, currentUnloadDelayMillis);
    }

    private void cancelAutoUnloadTimer() {
        if (pendingUnloadRunnable != null) {
            mainHandler.removeCallbacks(pendingUnloadRunnable);
            pendingUnloadRunnable = null;
        }
    }

    private void stopForegroundCompat() {
        if (!engine.isGenerating()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private android.app.Notification buildNotification(String title) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(getString(R.string.service_notification_text))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }
}
