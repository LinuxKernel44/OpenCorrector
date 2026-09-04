package com.opencorrector;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.IBinder;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.opencorrector.databinding.ActivityMainBinding;
import com.opencorrector.download.DownloadProgressListener;
import com.opencorrector.download.ModelDownloader;
import com.opencorrector.inference.LlamaModel;
import com.opencorrector.inference.LlamaService;
import com.opencorrector.settings.AppPreferences;

import java.io.File;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AppPreferences appPreferences;
    private ModelDownloader modelDownloader;

    private LlamaService boundService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            boundService = ((LlamaService.LocalBinder) service).getService();
            serviceBound = true;
            refreshStatus();
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
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        appPreferences = new AppPreferences(this);
        modelDownloader = new ModelDownloader(this);

        binding.buttonDownload.setOnClickListener(v -> onDownloadClicked());
        binding.buttonDeleteModel.setOnClickListener(v -> onDeleteClicked());
        binding.buttonSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, LlamaService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        refreshStatus();
    }

    @Override
    protected void onStop() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        super.onStop();
    }

    private void refreshStatus() {
        LlamaModel selected = appPreferences.getSelectedModel();
        binding.textCurrentModel.setText(getString(R.string.main_current_model, selected.displayName));

        boolean loaded = serviceBound && boundService != null
                && boundService.isModelLoaded() && boundService.getLoadedModel() == selected;
        boolean downloaded = ModelDownloader.isDownloaded(this, selected);

        if (loaded) {
            binding.textModelStatus.setText(R.string.main_status_loaded);
        } else if (downloaded) {
            binding.textModelStatus.setText(R.string.main_status_ready);
        } else {
            binding.textModelStatus.setText(R.string.main_status_not_downloaded);
        }

        binding.buttonDownload.setText(downloaded
                ? R.string.main_cancel_download_button
                : R.string.main_download_button);
        binding.buttonDeleteModel.setVisibility(downloaded ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void onDownloadClicked() {
        LlamaModel selected = appPreferences.getSelectedModel();
        if (ModelDownloader.isDownloaded(this, selected)) {
            return;
        }
        if (!isNetworkAvailable()) {
            showError(getString(R.string.error_network_unavailable));
            return;
        }

        binding.buttonDownload.setEnabled(false);
        binding.progressDownload.setVisibility(android.view.View.VISIBLE);
        binding.textDownloadProgress.setVisibility(android.view.View.VISIBLE);

        modelDownloader.startDownload(selected, new DownloadProgressListener() {
            @Override
            public void onProgress(long bytesDownloaded, long totalBytes) {
                runOnUiThread(() -> {
                    int percent = totalBytes > 0 ? (int) (bytesDownloaded * 100 / totalBytes) : 0;
                    binding.progressDownload.setProgress(percent);
                    binding.textDownloadProgress.setText(getString(
                            R.string.download_progress,
                            formatBytes(bytesDownloaded),
                            formatBytes(totalBytes),
                            percent));
                });
            }

            @Override
            public void onCompleted(File modelFile) {
                runOnUiThread(() -> {
                    binding.buttonDownload.setEnabled(true);
                    binding.progressDownload.setVisibility(android.view.View.GONE);
                    binding.textDownloadProgress.setVisibility(android.view.View.GONE);
                    refreshStatus();
                });
            }

            @Override
            public void onError(String errorMessageResKey, String debugDetail) {
                runOnUiThread(() -> {
                    binding.buttonDownload.setEnabled(true);
                    binding.progressDownload.setVisibility(android.view.View.GONE);
                    binding.textDownloadProgress.setVisibility(android.view.View.GONE);
                    showError(getString(R.string.error_download_failed, debugDetail));
                    refreshStatus();
                });
            }

            @Override
            public void onCancelled() {
                runOnUiThread(() -> {
                    binding.buttonDownload.setEnabled(true);
                    binding.progressDownload.setVisibility(android.view.View.GONE);
                    binding.textDownloadProgress.setVisibility(android.view.View.GONE);
                    refreshStatus();
                });
            }
        });
    }

    private void onDeleteClicked() {
        LlamaModel selected = appPreferences.getSelectedModel();
        new AlertDialog.Builder(this)
                .setMessage(R.string.main_delete_model_button)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    modelDownloader.deleteModel(selected);
                    refreshStatus();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static String formatBytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(Locale.getDefault(), "%.0f MB", mb);
    }
}
