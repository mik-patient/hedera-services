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
package com.hedera.services.bdd.spec.assertions;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;

public class NoTokenTransfers implements ErroringAssertsProvider<List<TokenTransferList>> {
    public static NoTokenTransfers emptyTokenTransfers() {
        return new NoTokenTransfers();
    }

    @Override
    public ErroringAsserts<List<TokenTransferList>> assertsFor(HapiApiSpec spec) {
        return tokenTransfers -> {
            try {
                Assertions.assertTrue(
                        tokenTransfers.isEmpty(),
                        () -> "Expected no token transfers, were: " + tokenTransfers);
            } catch (Throwable t) {
                return List.of(t);
            }
            return Collections.emptyList();
        };
    }
}
