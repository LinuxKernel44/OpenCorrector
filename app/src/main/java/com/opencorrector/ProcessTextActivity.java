package com.opencorrector;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.opencorrector.databinding.ActivityProcessTextBinding;
import com.opencorrector.download.ModelDownloader;
import com.opencorrector.inference.EngineException;
import com.opencorrector.inference.InferenceCallback;
import com.opencorrector.inference.LlamaModel;
import com.opencorrector.inference.LlamaService;
import com.opencorrector.prompt.CorrectionMode;
import com.opencorrector.settings.AppPreferences;
import com.opencorrector.text.LanguageDetector;
import com.opencorrector.text.TextProcessor;

import java.io.File;

/**
 * Entry point for android.intent.action.PROCESS_TEXT. Shown as a floating dialog (see
 * Theme.OpenCorrector.Popup). Never logs the selected text itself.
 */
public final class ProcessTextActivity extends AppCompatActivity implements SuggestionsAdapter.Listener {

    private ActivityProcessTextBinding binding;
    private AppPreferences appPreferences;
    private SuggestionsAdapter adapter;

    private String originalText;
    private String detectedLanguage;
    private boolean readOnly;

    private LlamaService boundService;
    private boolean serviceBound = false;
    private boolean userCancelled = false;
    private int currentModeIndex = 0;

    private final CorrectionMode[] modes = CorrectionMode.values();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            boundService = ((LlamaService.LocalBinder) service).getService();
            serviceBound = true;
            startProcessing();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            boundService = null;
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProcessTextBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        appPreferences = new AppPreferences(this);

        CharSequence extraText = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        readOnly = getIntent().getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        originalText = extraText != null ? extraText.toString() : "";

        if (originalText.trim().isEmpty()) {
            Toast.makeText(this, R.string.error_empty_text, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        detectedLanguage = LanguageDetector.detect(originalText);
        binding.textOriginal.setText(originalText);

        if (TextProcessor.isLikelyLong(originalText)) {
            int chunks = TextProcessor.estimateChunkCount(originalText);
            binding.textLongTextWarning.setVisibility(View.VISIBLE);
            binding.textLongTextWarning.setText(getString(
                    R.string.process_text_long_text_warning,
                    TextProcessor.estimateTokenCount(originalText),
                    chunks));
        }

        adapter = new SuggestionsAdapter(this, readOnly);
        binding.recyclerSuggestions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerSuggestions.setAdapter(adapter);

        binding.buttonCancel.setOnClickListener(v -> {
            userCancelled = true;
            if (serviceBound && boundService != null) {
                boundService.cancelGeneration();
            }
            finish();
        });

        LlamaModel selectedModel = appPreferences.getSelectedModel();
        if (!ModelDownloader.isDownloaded(this, selectedModel)) {
            showGlobalError(getString(R.string.error_model_missing));
            return;
        }

        bindService(new Intent(this, LlamaService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        super.onDestroy();
    }

    private void startProcessing() {
        if (userCancelled) {
            return;
        }
        LlamaModel selectedModel = appPreferences.getSelectedModel();
        File modelFile = ModelDownloader.modelFile(this, selectedModel);

        binding.textGlobalStatus.setText(R.string.process_text_loading_model);
        binding.layoutGlobalStatus.setVisibility(View.VISIBLE);

        boundService.loadModel(selectedModel, modelFile, appPreferences.buildInferenceConfig(), new LlamaService.LoadCallback() {
            @Override
            public void onLoaded() {
                if (userCancelled) {
                    return;
                }
                binding.layoutGlobalStatus.setVisibility(View.GONE);
                currentModeIndex = 0;
                processNextMode();
            }

            @Override
            public void onError(EngineException.Code code) {
                showGlobalError(getString(errorMessageFor(code)));
            }
        });
    }

    private void processNextMode() {
        if (userCancelled || currentModeIndex >= modes.length) {
            return;
        }
        int position = currentModeIndex;
        adapter.updateItem(position, SuggestionsAdapter.State.GENERATING, "", null);

        boundService.generate(detectedLanguage, modes[position], originalText, new InferenceCallback() {
            @Override
            public void onChunkingStarted(int totalChunks) {
                // The long-text banner (shown at onCreate) already covers this case.
            }

            @Override
            public void onPartial(String textSoFar, int chunkIndex, int totalChunks) {
                adapter.updateItem(position, SuggestionsAdapter.State.GENERATING, textSoFar, null);
            }

            @Override
            public void onComplete(String finalText) {
                adapter.updateItem(position, SuggestionsAdapter.State.DONE, finalText, null);
                currentModeIndex++;
                processNextMode();
            }

            @Override
            public void onError(EngineException.Code code) {
                adapter.updateItem(position, SuggestionsAdapter.State.ERROR, "", getString(errorMessageFor(code)));
                currentModeIndex++;
                processNextMode();
            }

            @Override
            public void onCancelled() {
                adapter.updateItem(position, SuggestionsAdapter.State.CANCELLED, "", getString(R.string.error_inference_cancelled));
            }
        });
    }

    @Override
    public void onReplaceClicked(int position, String text) {
        if (readOnly) {
            Toast.makeText(this, R.string.error_replace_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        result.putExtra(Intent.EXTRA_PROCESS_TEXT, text);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onCopyClicked(int position, String text) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text));
            Toast.makeText(this, R.string.process_text_copy, Toast.LENGTH_SHORT).show();
        }
    }

    private void showGlobalError(String message) {
        binding.layoutGlobalStatus.setVisibility(View.VISIBLE);
        binding.progressGlobal.setVisibility(View.GONE);
        binding.textGlobalStatus.setText(message);
    }

    private static int errorMessageFor(EngineException.Code code) {
        switch (code) {
            case MODEL_MISSING:
                return R.string.error_model_missing;
            case OUT_OF_MEMORY:
                return R.string.error_out_of_memory;
            case EMPTY_TEXT:
                return R.string.error_empty_text;
            case TEXT_TOO_LONG:
                return R.string.error_text_too_long;
            case CANCELLED:
                return R.string.error_inference_cancelled;
            case NATIVE_ENGINE_ERROR:
            default:
                return R.string.error_native_engine;
        }
    }
}
