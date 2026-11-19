package com.kyroxova.lootgamesolver.client.storage;

/** Recognizes the ServerUtilities confirmation for the temporary return warp. */
public final class StorageWarpFeedback {

    public static final String RETURN_WARP = "returnLooting";

    private StorageWarpFeedback() {}

    public static boolean isReturnWarpConfirmation(String translationKey, String text, String expectedText) {
        if (text == null || !text.toLowerCase(java.util.Locale.ROOT)
            .contains(RETURN_WARP.toLowerCase(java.util.Locale.ROOT))) return false;
        if ("serverutilities.lang.warps.set".equals(translationKey)) return true;
        return expectedText != null && expectedText.equalsIgnoreCase(text);
    }
}
