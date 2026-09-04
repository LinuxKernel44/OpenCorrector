package com.opencorrector.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.opencorrector.inference.InferenceConfig;
import com.opencorrector.inference.LlamaModel;

/**
 * Small typed wrapper around the default SharedPreferences used by the preference screen
 * (res/xml/root_preferences.xml) and read by the rest of the app.
 */
public final class AppPreferences {

    public static final String KEY_MODEL_VARIANT = "pref_model_variant";
    public static final String KEY_CPU_THREADS = "pref_cpu_threads";
    public static final String KEY_UNLOAD_DELAY_MINUTES = "pref_unload_delay_minutes";
    public static final String KEY_GPU_ENABLED = "pref_gpu_enabled";

    private static final int DEFAULT_THREADS = 4;
    private static final int DEFAULT_UNLOAD_DELAY_MINUTES = 5;

    private final SharedPreferences prefs;

    public AppPreferences(Context context) {
        this.prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public LlamaModel getSelectedModel() {
        return LlamaModel.fromId(prefs.getString(KEY_MODEL_VARIANT, LlamaModel.QUALITY.id));
    }

    public int getThreadCount() {
        return parseIntSafe(prefs.getString(KEY_CPU_THREADS, String.valueOf(DEFAULT_THREADS)), DEFAULT_THREADS);
    }

    public int getUnloadDelayMinutes() {
        return parseIntSafe(prefs.getString(KEY_UNLOAD_DELAY_MINUTES, String.valueOf(DEFAULT_UNLOAD_DELAY_MINUTES)), DEFAULT_UNLOAD_DELAY_MINUTES);
    }

    public boolean isGpuEnabled() {
        return prefs.getBoolean(KEY_GPU_ENABLED, false);
    }

    public InferenceConfig buildInferenceConfig() {
        return new InferenceConfig(getThreadCount(), isGpuEnabled(), getUnloadDelayMinutes() * 60_000L);
    }

    private static int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
