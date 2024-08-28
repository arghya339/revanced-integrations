package app.revanced.integrations.patches.misc;

import static android.text.TextUtils.isEmpty;
import static app.revanced.integrations.patches.misc.requests.LiveStreamRendererRequester.getLiveStreamRenderer;
import static app.revanced.integrations.utils.ReVancedUtils.submitOnBackgroundThread;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import app.revanced.integrations.patches.misc.requests.PlayerRoutes.ClientType;
import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.shared.PlayerType;
import app.revanced.integrations.utils.LogHelper;

/**
 * @noinspection ALL
 */
public class SpoofClientPatch {
    private static final boolean SPOOF_CLIENT_ENABLED = SettingsEnum.SPOOF_CLIENT.getBoolean();

    /**
     * Clips or Shorts Parameters.
     */
    private static final String CLIP_PARAMETER_PREFIX = "kAIB";
    private static final String SHORTS_PARAMETER_PREFIX = "8AEB";

    /**
     * iOS client is used for Clips or Shorts.
     */
    private static volatile boolean isShortsOrClips;

    /**
     * Any unreachable ip address.  Used to intentionally fail requests.
     */
    private static final String UNREACHABLE_HOST_URI_STRING = "https://127.0.0.0";
    private static final Uri UNREACHABLE_HOST_URI = Uri.parse(UNREACHABLE_HOST_URI_STRING);

    /**
     * Last spoofed client type.
     */
    private static volatile ClientType lastSpoofedClientType;

    /**
     * Last video id loaded. Used to prevent reloading the same spec multiple times.
     */
    @NonNull
    private static volatile String lastPlayerResponseVideoId = "";

    @Nullable
    private static volatile Future<LiveStreamRenderer> rendererFuture;

    /**
     * Injection point.
     * Blocks /get_watch requests by returning a localhost URI.
     *
     * @param playerRequestUri The URI of the player request.
     * @return Localhost URI if the request is a /get_watch request, otherwise the original URI.
     */
    public static Uri blockGetWatchRequest(Uri playerRequestUri) {
        if (SPOOF_CLIENT_ENABLED) {
            try {
                String path = playerRequestUri.getPath();

                if (path != null && path.contains("get_watch")) {
                    // Logger.printDebug(() -> "Blocking: " + playerRequestUri + " by returning: " + UNREACHABLE_HOST_URI_STRING);

                    return UNREACHABLE_HOST_URI;
                }
            } catch (Exception ex) {
                // Logger.printException(() -> "blockGetWatchRequest failure", ex);
            }
        }

        return playerRequestUri;
    }

    /**
     * Injection point.
     *
     * Blocks /initplayback requests.
     */
    public static String blockInitPlaybackRequest(String originalUrlString) {
        if (SPOOF_CLIENT_ENABLED) {
            try {
                Uri originalUri = Uri.parse(originalUrlString);
                String path = originalUri.getPath();

                if (path != null && path.contains("initplayback")) {
                    String replacementUriString = (getSpoofClientType() != ClientType.ANDROID_TESTSUITE)
                            ? UNREACHABLE_HOST_URI_STRING
                            // TODO: Ideally, a local proxy could be setup and block
                            //  the request the same way as Burp Suite is capable of
                            //  because that way the request is never sent to YouTube unnecessarily.
                            //  Just using localhost unfortunately does not work.
                            : originalUri.buildUpon().clearQuery().build().toString();

                    // Logger.printDebug(() -> "Blocking: " + originalUrlString + " by returning: " + replacementUriString);

                    return replacementUriString;
                }
            } catch (Exception ex) {
                // Logger.printException(() -> "blockInitPlaybackRequest failure", ex);
            }
        }

        return originalUrlString;
    }

    private static ClientType getSpoofClientType() {
            if (isShortsOrClips) {
                lastSpoofedClientType = getClientTypeFromSettingsEnum(SettingsEnum.SPOOF_CLIENT_SHORTS);
                return lastSpoofedClientType;
            }
            LiveStreamRenderer renderer = getRenderer(false);
            if (renderer != null) {
                if (renderer.isLive) {
                    lastSpoofedClientType = getClientTypeFromSettingsEnum(SettingsEnum.SPOOF_CLIENT_LIVESTREAM);
                    return lastSpoofedClientType;
                }
                if (!renderer.playabilityOk) {
                    lastSpoofedClientType = getClientTypeFromSettingsEnum(SettingsEnum.SPOOF_CLIENT_FALLBACK);
                    return lastSpoofedClientType;
                }
            }
            lastSpoofedClientType = getClientTypeFromSettingsEnum(SettingsEnum.SPOOF_CLIENT_GENERAL);
            return lastSpoofedClientType;
    }

    /**
     * Injection point.
     */
    public static int getClientTypeId(int originalClientTypeId) {
        if (SPOOF_CLIENT_ENABLED) {
            return getSpoofClientType().id;
        }

        return originalClientTypeId;
    }

    /**
     * Injection point.
     */
    public static String getClientVersion(String originalClientVersion) {
        if (SPOOF_CLIENT_ENABLED) {
            return getSpoofClientType().version;
        }

        return originalClientVersion;
    }

    /**
     * Injection point.
     */
    public static String getClientModel(String originalClientModel) {
        if (SPOOF_CLIENT_ENABLED) {
            return getSpoofClientType().model;
        }

        return originalClientModel;
    }

    /**
     * Injection point.
     */
    public static String getOsVersion(String originalOsVersion) {
        if (SPOOF_CLIENT_ENABLED) {
            return getSpoofClientType().osVersion;
        }

        return originalOsVersion;
    }

    /**
     * Injection point.
     */
    public static String getUserAgent(String originalUserAgent) {
        if (SPOOF_CLIENT_ENABLED) {
            ClientType clientType = getSpoofClientType();
            if (clientType == ClientType.IOS) {
                //Logger.printDebug(() -> "Replaced: '" + originalUserAgent + "' with: '"
                //        + clientType.userAgent + "'");
                return clientType.userAgent;
            }
        }

        return originalUserAgent;
    }

    /**
     * Injection point.
     */
    public static boolean isClientSpoofingEnabled() {
        return SPOOF_CLIENT_ENABLED;
    }

    /**
     * Injection point.
     */
    public static boolean enablePlayerGesture(boolean original) {
        return SPOOF_CLIENT_ENABLED || original;
    }

    /**
     * Injection point.
     * When spoofing the client to iOS, the playback speed menu is missing from the player response.
     * Return true to force create the playback speed menu.
     */
    public static boolean forceCreatePlaybackSpeedMenu(boolean original) {
        if (SPOOF_CLIENT_ENABLED && (lastSpoofedClientType == ClientType.IOS
                || lastSpoofedClientType == ClientType.ANDROID_UNPLUGGED
                || lastSpoofedClientType == ClientType.ANDROID_TESTSUITE)) {
            return true;
        }

        return original;
    }

    /**
     * Injection point.
     * This fix only works if {@link BackgroundPlaybackPatch} is included.
     */
    public static boolean forceEnableBackgroundPlayback() {
        if (SPOOF_CLIENT_ENABLED && getSpoofClientType() == ClientType.IOS) {
            return !PlayerType.getCurrent().isNoneHiddenOrSlidingMinimized();
        }

        return false;
    }

    private static final Uri VIDEO_STATS_PLAYBACK_URI = Uri.parse("https://www.youtube.com/api/stats/playback?ns=yt&ver=2&final=1");

    private static final String PARAM_DOC_ID = "docid";
    private static final String PARAM_LEN = "len";
    private static final String PARAM_CPN = "cpn";

    private static final String PARAM_EVENT_ID = "ei";
    private static final String PARAM_VM = "vm";
    private static final String PARAM_OF = "of";

    private static String mDocId;
    private static String mLen;
    private static String mCpn;
    private static String mEventId;
    private static String mVisitorMonitoringData;
    private static String mOfParam;

    /**
     * Injection point.
     */
    public static void setCpn(String cpn, boolean isLive) {
        if (SPOOF_CLIENT_ENABLED && !Objects.equals(mCpn, cpn)) {
            mCpn = cpn;
        }
    }

    /**
     * Injection point.
     *
     * Parse parameters from the Tracking URL.
     * See <a href="https://github.com/yuliskov/MediaServiceCore/blob/d83b56d98f75ba24eef0bf31073c72c3db2f9cb0/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/videoinfo/models/VideoInfo.java#L327">yuliskov/MediaServiceCore</a>.
     */
    public static void setTrackingUriParameter(Uri trackingUri) {
        try {
            if (SPOOF_CLIENT_ENABLED && SettingsEnum.SPOOF_CLIENT_IOS_HISTORY_FIX.getBoolean()) {
                String path = trackingUri.getPath();

                if (path == null || (!path.contains("playback") && !path.contains("watchtime"))) {
                    return;
                }

                mDocId = getQueryParameter(trackingUri, PARAM_DOC_ID);
                mLen = getQueryParameter(trackingUri, PARAM_LEN);
                mEventId = getQueryParameter(trackingUri, PARAM_EVENT_ID);
                mVisitorMonitoringData = getQueryParameter(trackingUri, PARAM_VM);
                mOfParam = getQueryParameter(trackingUri, PARAM_OF);
            }
        } catch (Exception ex) {
            LogHelper.printException(SpoofClientPatch.class ,"setTrackingUriParameter failure", ex);
        }
    }

    /**
     * Injection point.
     * This only works on YouTube 18.38.45 or earlier.
     *
     * Build a Tracking URL.
     * This does not include the last watched time.
     * See <a href="https://github.com/yuliskov/MediaServiceCore/blob/d83b56d98f75ba24eef0bf31073c72c3db2f9cb0/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/track/TrackingService.java#L72">yuliskov/MediaServiceCore</a>.
     */
    public static Uri overrideTrackingUrl(Uri trackingUrl) {
        try {
            if (SPOOF_CLIENT_ENABLED &&
                    getSpoofClientType() == ClientType.IOS &&
                    SettingsEnum.SPOOF_CLIENT_IOS_HISTORY_FIX.getBoolean() &&
                    trackingUrl.toString().contains("youtube.com/csi") &&
                    !isAnyEmpty(mDocId, mLen, mCpn, mEventId, mVisitorMonitoringData, mOfParam)

            ) {
                final Uri videoStatsPlaybackUri = VIDEO_STATS_PLAYBACK_URI
                        .buildUpon()
                        .appendQueryParameter(PARAM_DOC_ID, mDocId)
                        .appendQueryParameter(PARAM_LEN, mLen)
                        .appendQueryParameter(PARAM_CPN, mCpn)
                        .appendQueryParameter(PARAM_EVENT_ID, mEventId)
                        .appendQueryParameter(PARAM_VM, mVisitorMonitoringData)
                        .appendQueryParameter(PARAM_OF, mOfParam)
                        .build();
                return videoStatsPlaybackUri;
            }
        } catch (Exception ex) {
        }

        return trackingUrl;
    }

    private static String getQueryParameter(Uri uri, String key) {
        List<String> queryParams = uri.getQueryParameters(key);
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        } else {
            return queryParams.get(0);
        }
    }

    /**
     * Injection point.
     */
    public static String appendSpoofedClient(String videoFormat) {
        try {
            if (SPOOF_CLIENT_ENABLED && SettingsEnum.SPOOF_CLIENT_STATS_FOR_NERDS.getBoolean()
                    && !isEmpty(videoFormat)) {
                // Force LTR layout, to match the same LTR video time/length layout YouTube uses for all languages
                return "\u202D" + videoFormat + String.format("\u2009(%s)", lastSpoofedClientType.friendlyName); // u202D = left to right override
            }
        } catch (Exception ex) {
            // Logger.printException(() -> "appendSpoofedClient failure", ex);
        }

        return videoFormat;
    }

    /**
     * Injection point.
     */
    public static String setPlayerResponseVideoId(@NonNull String videoId, @Nullable String parameters) {
        if (SPOOF_CLIENT_ENABLED) {
            isShortsOrClips = parameters != null &&
                    (parameters.startsWith(CLIP_PARAMETER_PREFIX) || parameters.startsWith(SHORTS_PARAMETER_PREFIX));

            if (!isShortsOrClips) {
                fetchLiveStreamRenderer(videoId, getClientTypeFromSettingsEnum(SettingsEnum.SPOOF_CLIENT_GENERAL));
            }
        }

        return parameters; // Return the original value since we are observing and not modifying.
    }

    private static void fetchLiveStreamRenderer(@NonNull String videoId, @NonNull ClientType clientType) {
        if (!videoId.equals(lastPlayerResponseVideoId)) {
            rendererFuture = submitOnBackgroundThread(() -> getLiveStreamRenderer(videoId, clientType));
            lastPlayerResponseVideoId = videoId;
        }
        // Block until the renderer fetch completes.
        // This is desired because if this returns without finishing the fetch
        // then video will start playback but the storyboard is not ready yet.
        getRenderer(true);
    }

    @Nullable
    private static LiveStreamRenderer getRenderer(boolean waitForCompletion) {
        Future<LiveStreamRenderer> future = rendererFuture;
        if (future != null) {
            try {
                if (waitForCompletion || future.isDone()) {
                    return future.get(20000, TimeUnit.MILLISECONDS); // Any arbitrarily large timeout.
                } // else, return null.
            } catch (TimeoutException ex) {
                // Logger.printDebug(() -> "Could not get renderer (get timed out)");
            } catch (ExecutionException | InterruptedException ex) {
                // Should never happen.
                // Logger.printException(() -> "Could not get renderer", ex);
            }
        }
        return null;
    }

    private static ClientType getClientTypeFromSettingsEnum(@NonNull SettingsEnum setting) {
        try {
            return ClientType.valueOf(setting.getString());
        } catch (IllegalArgumentException e) {
            // if the ClientType does not exist, reset the setting
            setting.saveValue(setting.getDefaultValue());
            return ClientType.valueOf((String) setting.getDefaultValue());
        }
    }

    private static boolean isAnyEmpty(final CharSequence... css) {
        if (css != null && css.length != 0) {
            for (final CharSequence cs : css) {
                if (isEmpty(cs)) {
                    return true;
                }
            }
        }
        return false;
    }
}