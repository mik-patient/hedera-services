/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.pricing;

import static com.hedera.services.pricing.FeeSchedules.FEE_SCHEDULE_MULTIPLIER;
import static com.hedera.services.pricing.FeeSchedules.USD_TO_TINYCENTS;
import static java.math.MathContext.DECIMAL128;
import static java.math.RoundingMode.HALF_EVEN;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;

class FeeSchedulesTestHelper {
    protected static final double DEFAULT_ALLOWED_DEVIATION = 0.00000001;

    protected static FeeSchedules subject = new FeeSchedules();
    protected static AssetsLoader assetsLoader = new AssetsLoader();
    protected static BaseOperationUsage baseOperationUsage = new BaseOperationUsage();

    protected static Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalTotalPricesInUsd =
            null;

    @BeforeAll
    static void setup() throws IOException {
        canonicalTotalPricesInUsd = assetsLoader.loadCanonicalPrices();
    }

    protected void testCanonicalPriceFor(final HederaFunctionality function, final SubType subType)
            throws IOException {
        testCanonicalPriceFor(function, subType, DEFAULT_ALLOWED_DEVIATION);
    }

    protected void testCanonicalPriceFor(
            final HederaFunctionality function,
            final SubType subType,
            final double allowedDeviation)
            throws IOException {
        final var expectedBasePrice = canonicalTotalPricesInUsd.get(function).get(subType);
        final var canonicalUsage = baseOperationUsage.baseUsageFor(function, subType);

        testExpected(expectedBasePrice, canonicalUsage, function, subType, allowedDeviation);
    }

    protected void testExpected(
            final BigDecimal expectedBasePrice,
            final UsageAccumulator usage,
            final HederaFunctionality function,
            final SubType subType,
            final double allowedDeviation)
            throws IOException {
        final var computedResourcePrices = subject.canonicalPricesFor(function, subType);

        final var actualBasePrice = feeInUsd(computedResourcePrices, usage);

        // then:
        assertEquals(
                expectedBasePrice.doubleValue(), actualBasePrice.doubleValue(), allowedDeviation);
    }

    private BigDecimal feeInUsd(
            Map<ResourceProvider, Map<UsableResource, Long>> prices, UsageAccumulator usage) {
        var sum = BigDecimal.ZERO;
        for (var provider : ResourceProvider.class.getEnumConstants()) {
            final var providerPrices = prices.get(provider);
            for (var resource : UsableResource.class.getEnumConstants()) {
                final var bdPrice = BigDecimal.valueOf(providerPrices.get(resource));
                final var bdUsage = BigDecimal.valueOf(usage.get(provider, resource));
                sum = sum.add(bdPrice.multiply(bdUsage));
            }
        }
        return sum.divide(FEE_SCHEDULE_MULTIPLIER, DECIMAL128)
                .divide(USD_TO_TINYCENTS, new MathContext(5, HALF_EVEN));
    }
}
