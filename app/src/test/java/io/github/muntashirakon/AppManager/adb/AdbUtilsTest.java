// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.UPSIDE_DOWN_CAKE})
public class AdbUtilsTest {
    @Test
    public void testNeedsLocalNetworkPermission_belowAndroid17() {
        assertFalse(AdbUtils.needsLocalNetworkPermission());
    }

    @Test
    public void testIsAdbTlsPortInUse_forUnusedPort() {
        assertFalse(AdbUtils.isAdbTlsPortInUse(29_999));
    }
}
