package app.revanced.integrations.patches.layout;

import static app.revanced.integrations.utils.StringRef.str;

import android.graphics.Color;
import android.widget.Toast;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.utils.ReVancedUtils;

public class SeekbarLayoutPatch {

    public static final int ORIGINAL_SEEKBAR_COLOR = 0xFFFF0000;

    public static boolean enableSeekbarTapping() {
        return SettingsEnum.ENABLE_SEEKBAR_TAPPING.getBoolean();
    }

    public static boolean hideTimeAndSeekbar() {
        return SettingsEnum.HIDE_TIME_AND_SEEKBAR.getBoolean();
    }

    /**
     * Overrides all drawable color that use the YouTube seekbar color.
     * Used only for the video thumbnails seekbar.
     * <p>
     * If {@link Settings#HIDE_SEEKBAR_THUMBNAIL} is enabled, this returns a fully transparent color.
     */
    public static int getColor(int colorValue) {
        if (colorValue == ORIGINAL_SEEKBAR_COLOR) {
            if (SettingsEnum.HIDE_SEEKBAR_THUMBNAIL.getBoolean()) {
                return 0x00000000;
            }
            return overrideSeekbarColor(ORIGINAL_SEEKBAR_COLOR);
        }
        return colorValue;
    }

    public static int overrideSeekbarColor(int colorValue) {
        if (SettingsEnum.ENABLE_CUSTOM_SEEKBAR_COLOR.getBoolean()) {
            try {
                colorValue = Color.parseColor(SettingsEnum.ENABLE_CUSTOM_SEEKBAR_COLOR_VALUE.getString());
            } catch (Exception ignored) {
                Toast.makeText(ReVancedUtils.getContext(), str("color_invalid"), Toast.LENGTH_SHORT).show();
            }
        }
        return colorValue;
    }
}
