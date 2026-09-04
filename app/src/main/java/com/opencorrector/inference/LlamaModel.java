package com.opencorrector.inference;

/**
 * Describes one downloadable GGUF model variant. Values (URL, size, sha256) are taken from
 * the official Qwen GGUF repositories on Hugging Face and correspond to the exact file
 * checked in by ChecksumVerifier — do not change one without the other.
 */
public enum LlamaModel {

    QUALITY(
            "quality",
            "Qwen2.5-1.5B-Instruct Q4_K_M",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
            1_117_320_736L
    ),
    FAST(
            "fast",
            "Qwen2.5-0.5B-Instruct Q4_K_M",
            "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            491_400_032L
    );

    public final String id;
    public final String displayName;
    public final String fileName;
    public final String downloadUrl;
    public final String sha256;
    public final long sizeBytes;

    LlamaModel(String id, String displayName, String fileName, String downloadUrl, String sha256, long sizeBytes) {
        this.id = id;
        this.displayName = displayName;
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
    }

    public static LlamaModel fromId(String id) {
        for (LlamaModel model : values()) {
            if (model.id.equals(id)) {
                return model;
            }
        }
        return QUALITY;
    }
}
