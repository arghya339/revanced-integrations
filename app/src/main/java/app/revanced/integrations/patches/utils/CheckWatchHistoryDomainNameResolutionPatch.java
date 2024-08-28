package app.revanced.integrations.patches.utils;

import static app.revanced.integrations.utils.StringRef.str;

import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.Html;

import java.net.InetAddress;
import java.net.UnknownHostException;

import app.revanced.integrations.settings.SettingsEnum;
import app.revanced.integrations.utils.ReVancedUtils;

@SuppressWarnings("unused")
public class CheckWatchHistoryDomainNameResolutionPatch {

    private static final String HISTORY_TRACKING_ENDPOINT = "s.youtube.com";

    private static final String SINKHOLE_IPV4 = "0.0.0.0";
    private static final String SINKHOLE_IPV6 = "::";

    /** @noinspection SameParameterValue */
    private static boolean domainResolvesToValidIP(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            String hostAddress = address.getHostAddress();

            if (!address.isLoopbackAddress() && !SINKHOLE_IPV4.equals(hostAddress) && !SINKHOLE_IPV6.equals(hostAddress)) {
                return true; // Domain is not blocked.
            }
        } catch (UnknownHostException e) {
            // Logger.printDebug(() -> host + " failed to resolve");
        }

        return false;
    }

    /**
     * Injection point.
     * Checks if s.youtube.com is blacklisted and playback history will fail to work.
     */
    public static void checkDnsResolver(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();

        if (info == null || !info.isConnected() || SettingsEnum.IGNORE_CHECK_WATCH_HISTORY_DOMAIN_NAME.getBoolean()) return;

        ReVancedUtils.runOnBackgroundThread(() -> {
            try {
                if (domainResolvesToValidIP(HISTORY_TRACKING_ENDPOINT)) {
                    return;
                }

                ReVancedUtils.runOnMainThread(() -> new AlertDialog.Builder(context)
                        .setTitle(str("dialog_title_warning"))
                        .setMessage(Html.fromHtml(str("revanced_check_watch_history_domain_name_dialog_message")))
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                        .setNegativeButton(str("revanced_check_watch_history_domain_name_dialog_ignore"), (dialog, which) -> {
                            SettingsEnum.IGNORE_CHECK_WATCH_HISTORY_DOMAIN_NAME.saveValue(true);
                            dialog.dismiss();
                        })
                        .setCancelable(false)
                        .show());
            } catch (Exception ex) {
                //Logger.printException(() -> "checkDnsResolver failure", ex);
            }
        });
    }
}