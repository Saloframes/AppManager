// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.SettingsHidden;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.misc.SystemProperties;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.adb.android.AdbMdns;

public class AdbUtils {
    private static final String PROP_ADB_TLS_PORT = "service.adb.tls.port";
    private static final String PROP_ADB_TLS_SERVER_ENABLE = "persist.adb.tls_server.enable";
    /**
     * Android 17 (API 37) local network permission. Not yet exposed as a {@link Manifest} constant in
     * the current compile SDK.
     */
    public static final String PERMISSION_ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK";
    private static final int ANDROID_17_SDK = 37;

    @WorkerThread
    @NonNull
    public static Pair<String, Integer> getLatestAdbDaemon(@NonNull Context context, long timeout, @NonNull TimeUnit unit)
            throws InterruptedException, IOException {
        if (!isAdbdRunning()) {
            throw new IOException("ADB daemon not running.");
        }
        int tlsPort = getAdbTlsPort();
        if (tlsPort > 0) {
            return new Pair<>(getLoopbackHostAddress(), tlsPort);
        }
        AtomicInteger atomicPort = new AtomicInteger(-1);
        AtomicReference<String> atomicHostAddress = new AtomicReference<>(null);
        CountDownLatch resolveHostAndPort = new CountDownLatch(1);

        try (AdbMdnsMulticastLock ignored = new AdbMdnsMulticastLock(context)) {
            LocalAdbMdns adbMdnsTcp = new LocalAdbMdns(context, AdbMdns.SERVICE_TYPE_ADB, (hostAddress, port) -> {
                if (hostAddress != null) {
                    atomicHostAddress.set(hostAddress.getHostAddress());
                    atomicPort.set(port);
                }
                resolveHostAndPort.countDown();
            });
            adbMdnsTcp.start();

            LocalAdbMdns adbMdnsTls = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                adbMdnsTls = new LocalAdbMdns(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT, (hostAddress, port) -> {
                    if (hostAddress != null) {
                        atomicHostAddress.set(hostAddress.getHostAddress());
                        atomicPort.set(port);
                    }
                    resolveHostAndPort.countDown();
                });
                adbMdnsTls.start();
            }

            try {
                if (!resolveHostAndPort.await(timeout, unit)) {
                    throw new InterruptedException("Timed out while trying to find a valid host address and port");
                }
            } finally {
                adbMdnsTcp.stop();
                if (adbMdnsTls != null) {
                    adbMdnsTls.stop();
                }
            }
        }

        String host = atomicHostAddress.get();
        int port = atomicPort.get();
        if (host == null || port == -1) {
            throw new IOException("Could not find any valid host address or port");
        }
        return new Pair<>(host, port);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public static boolean enableWirelessDebugging(@NonNull Context context) {
        if (isWirelessDebuggingEnabled(context) && isAdbdRunning()) {
            return true;
        }
        ContentResolver resolver = context.getContentResolver();
        if (!SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)) {
            // No permission
            return false;
        }
        try {
            if (Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 0) {
                ContentValues contentValues = new ContentValues(2);
                contentValues.put("name", Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
                contentValues.put("value", 1);
                resolver.insert(Uri.parse("content://settings/global"), contentValues);
            }
            if (!isWirelessDebuggingEnabled(context)) {
                ContentValues contentValues = new ContentValues(2);
                contentValues.put("name", SettingsHidden.Global.ADB_WIFI_ENABLED);
                contentValues.put("value", 1);
                resolver.insert(Uri.parse("content://settings/global"), contentValues);
            }
            for (int i = 0; i < 5; ++i) {
                if (isAdbdRunning() && (getAdbTlsPort() > 0 || isWirelessDebuggingEnabled(context))) {
                    return true;
                }
                SystemClock.sleep(500);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return false;
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public static boolean isWirelessDebuggingEnabled(@NonNull Context context) {
        ContentResolver resolver = context.getContentResolver();
        if (Settings.Global.getInt(resolver, SettingsHidden.Global.ADB_WIFI_ENABLED, 0) != 0) {
            return true;
        }
        return SystemProperties.getBoolean(PROP_ADB_TLS_SERVER_ENABLE, false);
    }

    public static boolean isAdbdRunning() {
        // Default is set to “running” to avoid other issues
        return "running".equals(SystemProperties.get("init.svc.adbd", "running"));
    }

    public static int getAdbPortOrDefault() {
        return SystemProperties.getInt("service.adb.tcp.port", ServerConfig.DEFAULT_ADB_PORT);
    }

    @IntRange(from = -1, to = 65535)
    public static int getAdbTlsPort() {
        return SystemProperties.getInt(PROP_ADB_TLS_PORT, -1);
    }

    public static boolean isAdbTlsPortInUse(@IntRange(from = 1, to = 65535) int port) {
        String host = getLoopbackHostAddress();
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(host, port), 1);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    @NonNull
    private static String getLoopbackHostAddress() {
        String ipAddress = Inet4Address.getLoopbackAddress().getHostAddress();
        if (ipAddress == null || "::1".equals(ipAddress)) {
            return "127.0.0.1";
        }
        return ipAddress;
    }

    public static boolean startAdb(int port) {
        return Runner.runCommand(new String[]{"setprop", "service.adb.tcp.port", String.valueOf(port)}).isSuccessful()
                && Runner.runCommand(new String[]{"setprop", "ctl.restart", "adbd"}).isSuccessful();
    }

    public static boolean stopAdb() {
        return Runner.runCommand(new String[]{"setprop", "service.adb.tcp.port", "-1"}).isSuccessful()
                && Runner.runCommand(new String[]{"setprop", "ctl.restart", "adbd"}).isSuccessful();
    }

    public static boolean needsLocalNetworkPermission() {
        return Build.VERSION.SDK_INT >= ANDROID_17_SDK;
    }

    public static boolean hasLocalNetworkPermission(@NonNull Context context) {
        if (!needsLocalNetworkPermission()) {
            return true;
        }
        return SelfPermissions.checkSelfPermission(PERMISSION_ACCESS_LOCAL_NETWORK);
    }
}
