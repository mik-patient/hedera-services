package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUpdate;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

public class RandomTokenUpdate implements OpProvider {
	private final List<Consumer<HapiTokenUpdate>> OPS = List.of(
			this::mayUpdateName,
			this::mayUpdateSymbol,
			this::mayUpdateTreasury,
			this::mayUpdateAutoRenewAccout,
			this::mayUpdateAutoRenewPeriod,
			this::mayUpdateAutoExpiry,
			this::mayUpdateKeys);

	private static final List<BiConsumer<HapiTokenUpdate, String>> KEY_SETTERS = List.of(
			HapiTokenUpdate::kycKey,
			HapiTokenUpdate::wipeKey,
			HapiTokenUpdate::adminKey,
			HapiTokenUpdate::supplyKey,
			HapiTokenUpdate::freezeKey);

	private static final int DEFAULT_MAX_STRING_LEN = 100;
	private static final long DEFAULT_MAX_SUPPLY = 1_000;
	private static final long MAX_PERIOD = 1_000_000_000;

	private double kycKeyUpdateProb = 0.5;
	private double wipeKeyUpdateProb = 0.5;
	private double adminKeyUpdateProb = 0.5;
	private double supplyKeyUpdateProb = 0.5;
	private double freezeKeyUpdateProb = 0.5;
	private static double defaultUpdateProb = 0.5;
	private static double autoRenewUpdateProb = 0.5;
	private static double nameUpdateProb = 0.5;

	private final EntityNameProvider<Key> keys;
	private final RegistrySourcedNameProvider<TokenID> tokens;
	private final RegistrySourcedNameProvider<AccountID> accounts;

	private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
			INVALID_AUTORENEW_ACCOUNT, TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED,
			INVALID_TREASURY_ACCOUNT_FOR_TOKEN, TOKEN_WAS_DELETED,
			INVALID_RENEWAL_PERIOD, TOKEN_SYMBOL_TOO_LONG,
			TOKEN_NAME_TOO_LONG, INVALID_SIGNATURE,
			TOKEN_IS_IMMUTABLE, INVALID_ADMIN_KEY, INVALID_EXPIRATION_TIME,
			INVALID_TREASURY_ACCOUNT_FOR_TOKEN,
			INVALID_KYC_KEY, INVALID_KYC_KEY,INVALID_FREEZE_KEY,INVALID_SUPPLY_KEY,
			INVALID_WIPE_KEY
	);


	public RandomTokenUpdate(
			EntityNameProvider<Key> keys,
			RegistrySourcedNameProvider<TokenID> tokens,
			RegistrySourcedNameProvider<AccountID> accounts
	) {
		this.keys = keys;
		this.tokens = tokens;
		this.accounts = accounts;
	}

	@Override
	public Optional<HapiSpecOperation> get() {
		Optional<String> token = tokens.getQualifying();
		if (token.isEmpty())	{
			return Optional.empty();
		}

		HapiTokenUpdate op = tokenUpdate(token.get())
				.hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
				.hasKnownStatusFrom(permissibleOutcomes);

		OPS.stream().forEach(o -> o.accept(op));

		return Optional.of(op);
	}

    private void mayUpdateName(HapiTokenUpdate op) {
		if(BASE_RANDOM.nextDouble() < nameUpdateProb) {
			op.name(randomUppercase(1 + BASE_RANDOM.nextInt(DEFAULT_MAX_STRING_LEN)));
		}
	}

	private void mayUpdateSymbol(HapiTokenUpdate op) {
		if(BASE_RANDOM.nextDouble() < defaultUpdateProb) {
			op.symbol(randomUppercase(1 + BASE_RANDOM.nextInt(DEFAULT_MAX_STRING_LEN)));
		}
	}

	private void mayUpdateTreasury(HapiTokenUpdate op) {
		if(BASE_RANDOM.nextDouble() < defaultUpdateProb) {
			Optional<String> newTreasury = accounts.getQualifying();
			newTreasury.ifPresent(t -> op.treasury(t));
		}
	}

	private void mayUpdateAutoRenewAccout(HapiTokenUpdate op) {
		if(BASE_RANDOM.nextDouble() < defaultUpdateProb) {
			Optional<String> newAutoRenewAccount = accounts.getQualifying();
			newAutoRenewAccount.ifPresent(t -> op.autoRenewAccount(t));
		}
	}

	private void mayUpdateAutoRenewPeriod(HapiTokenUpdate op) {
		if(BASE_RANDOM.nextDouble() < defaultUpdateProb) {
			op.autoRenewPeriod(BASE_RANDOM.nextLong(MAX_PERIOD));
		}
	}

	private void mayUpdateAutoExpiry(HapiTokenUpdate op) {
		if(BASE_RANDOM.nextDouble() < defaultUpdateProb) {
			op.autoRenewPeriod(BASE_RANDOM.nextLong(MAX_PERIOD));
		}
	}


	private void mayUpdateKeys(HapiTokenUpdate op) {
		double[] probs = new double[] { kycKeyUpdateProb, wipeKeyUpdateProb, adminKeyUpdateProb, supplyKeyUpdateProb, freezeKeyUpdateProb };

		for (int i = 0; i < probs.length; i++) {
			if (BASE_RANDOM.nextDouble() < probs[i]) {
				var key = keys.getQualifying();
				if (key.isPresent()) {
					KEY_SETTERS.get(i).accept(op, key.get());
				}
			}
		}
	}


	private String my(String opName) {
		return unique(opName, RandomTokenUpdate.class);
	}
}
