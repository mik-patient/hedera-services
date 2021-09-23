package com.hedera.services.pricing;

/*-
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;

class TokenFeeSchedulesTest extends FeeSchedulesTestHelper {
	@Test
	void computesExpectedPriceForTokenCreateSubyptes() throws IOException {
		testCanonicalPriceFor(TokenCreate, TOKEN_FUNGIBLE_COMMON);
		testCanonicalPriceFor(TokenCreate, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES);
		testCanonicalPriceFor(TokenCreate, TOKEN_NON_FUNGIBLE_UNIQUE);
		testCanonicalPriceFor(TokenCreate, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES);
	}

	@Test
	void computesExpectedPriceForUniqueTokenMint() throws IOException {
		testCanonicalPriceFor(TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE);
		testCanonicalPriceFor(TokenMint, TOKEN_FUNGIBLE_COMMON);
	}

	@Test
	void computesExpectedPriceForUniqueTokenWipe() throws IOException {
		testCanonicalPriceFor(TokenAccountWipe, TOKEN_NON_FUNGIBLE_UNIQUE);
		testCanonicalPriceFor(TokenAccountWipe, TOKEN_FUNGIBLE_COMMON);
	}

	@Test
	void computesExpectedPriceForUniqueTokenBurn() throws IOException {
		testCanonicalPriceFor(TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE);
		testCanonicalPriceFor(TokenBurn, TOKEN_FUNGIBLE_COMMON);
	}

	@Test
	void computesExpectedPriceForFeeScheduleUpdate() throws IOException {
		testCanonicalPriceFor(TokenFeeScheduleUpdate, DEFAULT);
	}
	@Test
	void computesExpectedPriceForTokenFreezeAccount() throws IOException {
		testExpectedPriceFor(TokenFreezeAccount, DEFAULT);
	}
	@Test
	void computesExpectedPriceForTokenUnfreezeAccount() throws IOException {
		testExpectedPriceFor(TokenUnfreezeAccount, DEFAULT);
	}
}
