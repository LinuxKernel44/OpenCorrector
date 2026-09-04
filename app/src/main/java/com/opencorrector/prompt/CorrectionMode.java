package com.opencorrector.prompt;

import com.opencorrector.R;

/**
 * The three transformation modes offered by the popup, in display order.
 */
public enum CorrectionMode {

    CORRECTION(R.string.process_text_mode_correction, "correction"),
    FORMAL(R.string.process_text_mode_formal, "formal"),
    CONCISE(R.string.process_text_mode_concise, "concise");

    public final int labelRes;
    public final String resourceSuffix;

    CorrectionMode(int labelRes, String resourceSuffix) {
        this.labelRes = labelRes;
        this.resourceSuffix = resourceSuffix;
    }
}
