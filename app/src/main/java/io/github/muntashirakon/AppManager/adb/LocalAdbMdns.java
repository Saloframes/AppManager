// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Objects;

import io.github.muntashirakon.adb.android.AdbMdns;

/**
 * On-device mDNS discovery for the local ADB daemon.
 * <p>
 * The stock {@link AdbMdns} only reports a service when the resolved host address exactly matches
 * one of the device's interface addresses. Android 17's ADB Wi-Fi 2.0 stack can resolve services
 * to hostnames or differently formatted addresses, which breaks that check. For local wireless
 * debugging we only need the port, so we accept ADB services whose TLS port is already bound on
 * loopback.
 */
@RequiresApi(android.os.Build.VERSION_CODES.JELLY_BEAN)
/* package */ class LocalAdbMdns {
    private static final String ADB_SERVICE_PREFIX = "adb-";

    public interface OnPortDiscoveredListener {
        void onPortChanged(@Nullable InetAddress hostAddress, int port);
    }

    @NonNull
    private final Context mContext;
    @NonNull
    private final String mServiceType;
    @NonNull
    private final OnPortDiscoveredListener mListener;
    @NonNull
    private final NsdManager mNsdManager;
    @NonNull
    private final NsdManager.DiscoveryListener mDiscoveryListener;

    private boolean mRegistered;
    private boolean mRunning;
    @Nullable
    private String mServiceName;

    /* package */ LocalAdbMdns(@NonNull Context context, @AdbMdns.ServiceType @NonNull String serviceType,
                               @NonNull OnPortDiscoveredListener listener) {
        mContext = context.getApplicationContext();
        mServiceType = String.format("_%s._tcp", Objects.requireNonNull(serviceType));
        mListener = Objects.requireNonNull(listener);
        mNsdManager = Objects.requireNonNull((NsdManager) mContext.getSystemService(Context.NSD_SERVICE));
        mDiscoveryListener = new DiscoveryListener();
    }

    /* package */ void start() {
        if (mRunning) {
            return;
        }
        mRunning = true;
        if (!mRegistered) {
            mNsdManager.discoverServices(mServiceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        }
    }

    /* package */ void stop() {
        if (!mRunning) {
            return;
        }
        mRunning = false;
        if (mRegistered) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
    }

    private void onDiscoveryStart() {
        mRegistered = true;
    }

    private void onDiscoverStop() {
        mRegistered = false;
    }

    private void onServiceFound(@NonNull NsdServiceInfo serviceInfo) {
        mNsdManager.resolveService(serviceInfo, new ResolveListener());
    }

    private void onServiceLost(@NonNull NsdServiceInfo serviceInfo) {
        if (mServiceName != null && mServiceName.equals(serviceInfo.getServiceName())) {
            mListener.onPortChanged(serviceInfo.getHost(), -1);
        }
    }

    private void onServiceResolved(@NonNull NsdServiceInfo serviceInfo) {
        if (!mRunning) {
            return;
        }
        InetAddress host = serviceInfo.getHost();
        int port = serviceInfo.getPort();
        if (host == null || port <= 0) {
            return;
        }
        String serviceName = serviceInfo.getServiceName();
        if (serviceName == null || !serviceName.startsWith(ADB_SERVICE_PREFIX)) {
            return;
        }
        if (AdbUtils.isAdbTlsPortInUse(port) || isLocalHostAddress(host)) {
            mServiceName = serviceName;
            mListener.onPortChanged(host, port);
        }
    }

    private boolean isLocalHostAddress(@NonNull InetAddress host) {
        String hostAddress = host.getHostAddress();
        if (hostAddress == null) {
            return false;
        }
        if (host.isLoopbackAddress() || "127.0.0.1".equals(hostAddress) || "::1".equals(hostAddress)) {
            return true;
        }
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                    if (Objects.equals(inetAddress.getHostAddress(), hostAddress)) {
                        return true;
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return false;
    }

    private final class DiscoveryListener implements NsdManager.DiscoveryListener {
        @Override
        public void onDiscoveryStarted(String serviceType) {
            LocalAdbMdns.this.onDiscoveryStart();
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            LocalAdbMdns.this.onDiscoverStop();
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            LocalAdbMdns.this.onServiceFound(serviceInfo);
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            LocalAdbMdns.this.onServiceLost(serviceInfo);
        }
    }

    private final class ResolveListener implements NsdManager.ResolveListener {
        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            LocalAdbMdns.this.onServiceResolved(serviceInfo);
        }
    }
}
