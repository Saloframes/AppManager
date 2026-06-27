// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.content.Context;
import android.net.wifi.WifiManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Holds a Wi-Fi multicast lock while mDNS discovery is active. Some devices (notably Pixels) drop
 * multicast packets unless this lock is held.
 */
/* package */ final class AdbMdnsMulticastLock implements AutoCloseable {
    @Nullable
    private final WifiManager.MulticastLock mLock;

    /* package */ AdbMdnsMulticastLock(@NonNull Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            mLock = wifiManager.createMulticastLock("AppManagerAdbMdns");
            mLock.setReferenceCounted(false);
            mLock.acquire();
        } else {
            mLock = null;
        }
    }

    @Override
    public void close() {
        if (mLock != null && mLock.isHeld()) {
            mLock.release();
        }
    }
}
