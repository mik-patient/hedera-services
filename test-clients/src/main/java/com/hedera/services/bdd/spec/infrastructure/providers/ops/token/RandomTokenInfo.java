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
package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Optional;

public class RandomTokenInfo implements OpProvider {
    private final RegistrySourcedNameProvider<TokenID> tokens;

    private final ResponseCodeEnum[] permissibleCostAnswerPrechecks =
            standardQueryPrechecksAnd(TOKEN_WAS_DELETED);
    private final ResponseCodeEnum[] permissibleAnswerOnlyPrechecks =
            standardQueryPrechecksAnd(TOKEN_WAS_DELETED);

    public RandomTokenInfo(RegistrySourcedNameProvider<TokenID> tokens) {
        this.tokens = tokens;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        Optional<String> token = tokens.getQualifying();
        if (token.isEmpty()) {
            return Optional.empty();
        }

        var op =
                getTokenInfo(token.get())
                        .hasCostAnswerPrecheckFrom(permissibleCostAnswerPrechecks)
                        .hasAnswerOnlyPrecheckFrom(permissibleAnswerOnlyPrechecks);

        return Optional.of(op);
    }
}
