package app.revanced.integrations.patches.misc.client;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.utils.Logger;

public class DeviceHardwareSupport {
    public static final boolean DEVICE_HAS_HARDWARE_DECODING_VP9;
    public static final boolean DEVICE_HAS_HARDWARE_DECODING_AV1;

    static {
        boolean vp9found = false;
        boolean av1found = false;
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        final boolean deviceIsAndroidTenOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            final boolean isHardwareAccelerated = deviceIsAndroidTenOrLater
                    ? codecInfo.isHardwareAccelerated()
                    : !codecInfo.getName().startsWith("OMX.google"); // Software decoder.
            if (isHardwareAccelerated && !codecInfo.isEncoder()) {
                for (String type : codecInfo.getSupportedTypes()) {
                    if (type.equalsIgnoreCase("video/x-vnd.on2.vp9")) {
                        vp9found = true;
                    } else if (type.equalsIgnoreCase("video/av01")) {
                        av1found = true;
                    }
                }
            }
        }

        DEVICE_HAS_HARDWARE_DECODING_VP9 = vp9found;
        DEVICE_HAS_HARDWARE_DECODING_AV1 = av1found;

        Logger.printDebug(() -> DEVICE_HAS_HARDWARE_DECODING_AV1
                ? "Device supports AV1 hardware decoding\n"
                : "Device does not support AV1 hardware decoding\n"
                + (DEVICE_HAS_HARDWARE_DECODING_VP9
                ? "Device supports VP9 hardware decoding"
                : "Device does not support VP9 hardware decoding"));
    }

    public static boolean allowVP9() {
        return DEVICE_HAS_HARDWARE_DECODING_VP9 && !SettingsEnum.SPOOF_STREAMING_DATA_IOS_FORCE_AVC.getBoolean();
    }

    public static boolean allowAV1() {
        return allowVP9() && DEVICE_HAS_HARDWARE_DECODING_AV1;
    }
}
