package com.opencorrector.inference;

/**
 * Thrown by {@link LlamaEngine} for any failure. The errorCode maps 1:1 to a string
 * resource in strings.xml (error_*) so the UI never has to interpret raw native messages.
 */
public final class EngineException extends Exception {

    public enum Code {
        MODEL_MISSING,
        OUT_OF_MEMORY,
        NATIVE_ENGINE_ERROR,
        CANCELLED,
        EMPTY_TEXT,
        TEXT_TOO_LONG
    }

    public final Code code;

    public EngineException(Code code, String debugMessage) {
        super(debugMessage);
        this.code = code;
    }

    public EngineException(Code code, String debugMessage, Throwable cause) {
        super(debugMessage, cause);
        this.code = code;
    }
}
