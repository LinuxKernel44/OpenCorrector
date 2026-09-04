package com.opencorrector;

import android.app.Application;

/**
 * No global state lives here on purpose: LlamaService owns the model/engine lifecycle, and
 * every other component reads its configuration from AppPreferences on demand.
 */
public final class OpenCorrectorApplication extends Application {
}
