/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.stats;

import static com.hedera.services.stats.ServicesStatsManager.SPEEDOMETER_FORMAT;
import static com.hedera.services.stats.ServicesStatsManager.STAT_CATEGORY;

import com.google.common.annotations.VisibleForTesting;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.Platform;

public class MiscSpeedometers {
    private SpeedometerMetric syncVerifications;
    private SpeedometerMetric platformTxnRejections;

    public MiscSpeedometers(final double halfLife) {
        syncVerifications =
                new SpeedometerMetric(
                        STAT_CATEGORY,
                        Names.SYNC_VERIFICATIONS,
                        Descriptions.SYNC_VERIFICATIONS,
                        SPEEDOMETER_FORMAT,
                        halfLife);
        platformTxnRejections =
                new SpeedometerMetric(
                        STAT_CATEGORY,
                        Names.PLATFORM_TXN_REJECTIONS,
                        Descriptions.PLATFORM_TXN_REJECTIONS,
                        SPEEDOMETER_FORMAT,
                        halfLife);
    }

    public void registerWith(final Platform platform) {
        platform.addAppMetrics(syncVerifications, platformTxnRejections);
    }

    public void cycleSyncVerifications() {
        syncVerifications.update(1);
    }

    public void cyclePlatformTxnRejections() {
        platformTxnRejections.update(1);
    }

    public static final class Names {
        static final String SYNC_VERIFICATIONS = "sigVerifySync/sec";
        static final String PLATFORM_TXN_REJECTIONS = "platformTxnNotCreated/sec";

        private Names() {
            throw new UnsupportedOperationException("Utility Class");
        }
    }

    public static final class Descriptions {
        static final String SYNC_VERIFICATIONS =
                "number of transactions received per second that must be verified synchronously in"
                        + " handleTransaction";
        static final String PLATFORM_TXN_REJECTIONS =
                "number of platform transactions not created per second";

        private Descriptions() {
            throw new UnsupportedOperationException("Utility Class");
        }
    }

    @VisibleForTesting
    void setSyncVerifications(final SpeedometerMetric syncVerifications) {
        this.syncVerifications = syncVerifications;
    }

    @VisibleForTesting
    void setPlatformTxnRejections(final SpeedometerMetric platformTxnRejections) {
        this.platformTxnRejections = platformTxnRejections;
    }

    @VisibleForTesting
    SpeedometerMetric getSyncVerifications() {
        return syncVerifications;
    }

    @VisibleForTesting
    SpeedometerMetric getPlatformTxnRejections() {
        return platformTxnRejections;
    }
}
