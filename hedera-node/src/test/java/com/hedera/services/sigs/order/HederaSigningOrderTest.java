package com.hedera.services.sigs.order;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.config.EntityNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.files.HederaFs;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.sigs.metadata.ContractSigningMetadata;
import com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.metadata.TopicSigningMetadata;
import com.hedera.services.sigs.metadata.lookups.AccountSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.ContractSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.FileSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.SafeLookupResult;
import com.hedera.services.sigs.metadata.lookups.TopicSigMetaLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.defaultLookupsFor;
import static com.hedera.test.factories.scenarios.BadPayerScenarios.INVALID_PAYER_ID_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.CONSENSUS_SUBMIT_MESSAGE_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.CONSENSUS_SUBMIT_MESSAGE_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.DILIGENT_SIGNING_PAYER_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.EXISTING_TOPIC_ID;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.FIRST_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_ACCOUNT_ID;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_CONTRACT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_FILE_WACL_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_TOPIC_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_TOPIC_SUBMIT_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISSING_ACCOUNT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISSING_TOPIC;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.NEW_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.SCHEDULE_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.SECOND_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.SIMPLE_NEW_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.SIMPLE_NEW_WACL_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_KYC_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_REPLACE_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_SUPPLY_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_TREASURY_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_WIPE_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.UPDATE_TOPIC_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_EXPIRY_ONLY_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.CONTRACT_CREATE_DEPRECATED_CID_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.CONTRACT_CREATE_NO_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.CONTRACT_CREATE_WITH_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_XFER_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_XFER_CONTRACT_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_ONLY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_AUTORENEW_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_DEPRECATED_CID_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_FILE_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_PROXY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_WITH_NEW_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.CRYPTO_CREATE_NO_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.CRYPTO_CREATE_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.CRYPTO_DELETE_TARGET_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_MOVING_HBARS_WITH_EXTANT_SENDER;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_MOVING_HBARS_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDER;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_EXTANT_SENDERS;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_MISSING_SENDERS;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_NO_NEW_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NEW_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NO_NEW_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_SYS_ACCOUNT_WITH_PRIVILEGED_PAYER;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NEW_KEY;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NO_NEW_KEY;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_WITH_NEW_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.FILE_APPEND_MISSING_TARGET_SCENARIO;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.IMMUTABLE_FILE_APPEND_SCENARIO;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.MASTER_SYS_FILE_APPEND_SCENARIO;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.SYSTEM_FILE_APPEND_WITH_PRIVILEGD_PAYER;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.TREASURY_SYS_FILE_APPEND_SCENARIO;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.VANILLA_FILE_APPEND_SCENARIO;
import static com.hedera.test.factories.scenarios.FileCreateScenarios.VANILLA_FILE_CREATE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileDeleteScenarios.IMMUTABLE_FILE_DELETE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileDeleteScenarios.VANILLA_FILE_DELETE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.FILE_UPDATE_NEW_WACL_SCENARIO;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.IMMUTABLE_FILE_UPDATE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.MASTER_SYS_FILE_UPDATE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.TREASURY_SYS_FILE_UPDATE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.TREASURY_SYS_FILE_UPDATE_SCENARIO_NO_NEW_KEY;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.VANILLA_FILE_UPDATE_SCENARIO;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_INVALID_XFER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_NO_ADMIN;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_MISSING_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_KNOWN_SCHEDULE;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_MISSING_SCHEDULE;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_MISSING_SCHEDULE_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.ScheduleSignScenarios.SCHEDULE_SIGN_KNOWN_SCHEDULE;
import static com.hedera.test.factories.scenarios.ScheduleSignScenarios.SCHEDULE_SIGN_KNOWN_SCHEDULE_WITH_NOW_INVALID_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleSignScenarios.SCHEDULE_SIGN_KNOWN_SCHEDULE_WITH_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleSignScenarios.SCHEDULE_SIGN_MISSING_SCHEDULE;
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.SYSTEM_DELETE_FILE_SCENARIO;
import static com.hedera.test.factories.scenarios.SystemUndeleteScenarios.SYSTEM_UNDELETE_FILE_SCENARIO;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_MISSING_TARGET;
import static com.hedera.test.factories.scenarios.TokenBurnScenarios.BURN_WITH_SUPPLY_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_MISSING_ADMIN;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ADMIN_AND_FREEZE;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ADMIN_ONLY;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_AUTO_RENEW;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_MISSING_AUTO_RENEW;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_MISSING_COLLECTOR;
import static com.hedera.test.factories.scenarios.TokenDeleteScenarios.DELETE_WITH_KNOWN_TOKEN;
import static com.hedera.test.factories.scenarios.TokenDeleteScenarios.DELETE_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenDeleteScenarios.DELETE_WITH_MISSING_TOKEN_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_MISSING_TARGET;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_FEE_SCHEDULE_BUT_TOKEN_DOESNT_EXIST;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_MISSING_FEE_COLLECTOR;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_NO_FEE_SCHEDULE_KEY;
import static com.hedera.test.factories.scenarios.TokenFreezeScenarios.VALID_FREEZE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenKycGrantScenarios.VALID_GRANT_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenKycRevokeScenarios.REVOKE_FOR_TOKEN_WITHOUT_KYC;
import static com.hedera.test.factories.scenarios.TokenKycRevokeScenarios.REVOKE_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenKycRevokeScenarios.VALID_REVOKE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenMintScenarios.MINT_WITH_SUPPLY_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.VALID_UNFREEZE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_TREASURY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_WITH_MISSING_TREASURY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_FREEZE_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_KYC_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_MISSING_TOKEN_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_NO_KEYS_AFFECTED;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_SUPPLY_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_WIPE_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenWipeScenarios.VALID_WIPE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SYS_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FEE_SCHEDULE_KT;
import static com.hedera.test.factories.txns.ConsensusCreateTopicFactory.SIMPLE_TOPIC_ADMIN_KEY;
import static com.hedera.test.factories.txns.ContractCreateFactory.DEFAULT_ADMIN_KT;
import static com.hedera.test.factories.txns.CryptoCreateFactory.DEFAULT_ACCOUNT_KT;
import static com.hedera.test.factories.txns.FileCreateFactory.DEFAULT_WACL_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HederaSigningOrderTest {
	private static class TopicAdapter {
		public static TopicSigMetaLookup throwingUoe() {
			return id -> {
				throw new UnsupportedOperationException();
			};
		}

		public static TopicSigMetaLookup withSafe(
				Function<TopicID, SafeLookupResult<TopicSigningMetadata>> fn
		) {
			return fn::apply;
		}
	}

	private static class FileAdapter {
		public static FileSigMetaLookup throwingUoe() {
			return id -> {
				throw new UnsupportedOperationException();
			};
		}
	}

	private static class AccountAdapter {
		public static AccountSigMetaLookup withSafe(
				Function<AccountID, SafeLookupResult<AccountSigningMetadata>> fn
		) {
			return fn::apply;
		}
	}

	private static class ContractAdapter {
		public static ContractSigMetaLookup withSafe(
				Function<ContractID, SafeLookupResult<ContractSigningMetadata>> fn
		) {
			return fn::apply;
		}
	}

	private static final boolean IN_HANDLE_TXN_DYNAMIC_CTX = false;
	private static final Function<ContractSigMetaLookup, SigMetadataLookup> EXC_LOOKUP_FN = contractSigMetaLookup ->
			new DelegatingSigMetadataLookup(
					FileAdapter.throwingUoe(),
					AccountAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.MISSING_FILE)),
					contractSigMetaLookup,
					TopicAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.MISSING_FILE)),
					id -> null,
					id -> null);
	private static final SigMetadataLookup EXCEPTION_THROWING_LOOKUP = EXC_LOOKUP_FN.apply(
			ContractAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT))
	);
	private static final SigMetadataLookup INVALID_CONTRACT_THROWING_LOOKUP = EXC_LOOKUP_FN.apply(
			ContractAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT))
	);
	private static final SigMetadataLookup IMMUTABLE_CONTRACT_THROWING_LOOKUP = EXC_LOOKUP_FN.apply(
			ContractAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT))
	);

	private HederaFs hfs;
	private TokenStore tokenStore;
	private ScheduleStore scheduleStore;
	private TransactionBody txn;
	private HederaSigningOrder subject;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	private FCMap<MerkleEntityId, MerkleTopic> topics;
	private EntityNumbers mockEntityNumbers = new MockEntityNumbers();
	private SystemOpPolicies mockSystemOpPolicies = new SystemOpPolicies(mockEntityNumbers);
	private SignatureWaivers mockSignatureWaivers = new PolicyBasedSigWaivers(mockEntityNumbers, mockSystemOpPolicies);
	private SigStatusOrderResultFactory summaryFactory = new SigStatusOrderResultFactory(IN_HANDLE_TXN_DYNAMIC_CTX);
	private SigningOrderResultFactory<SignatureStatus> mockSummaryFactory;

	@Test
	void reportsInvalidPayerId() throws Throwable {
		// given:
		setupFor(INVALID_PAYER_ID_SCENARIO);
		aMockSummaryFactory();

		// when:
		subject.keysForPayer(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forInvalidAccount(MISSING_ACCOUNT, txn.getTransactionID());
	}

	@Test
	void reportsGeneralPayerError() throws Throwable {
		// given:
		setupForNonStdLookup(CRYPTO_CREATE_NO_RECEIVER_SIG_SCENARIO, EXCEPTION_THROWING_LOOKUP);
		aMockSummaryFactory();

		// when:
		subject.keysForPayer(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forGeneralPayerError(asAccount(DEFAULT_PAYER_ID), txn.getTransactionID());
	}

	@Test
	void getsCryptoCreateNoReceiverSigReq() throws Throwable {
		// given:
		setupFor(CRYPTO_CREATE_NO_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForPayer(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
	}

	@Test
	void getsCryptoCreateReceiverSigReq() throws Throwable {
		// given:
		setupFor(CRYPTO_CREATE_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_ACCOUNT_KT.asKey()));
	}

	@Test
	void getsCryptoTransferReceiverNoSigReq() throws Throwable {
		// given:
		setupFor(CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> payerSummary = subject.keysForPayer(txn, summaryFactory);
		SigningOrderResult<SignatureStatus> nonPayerSummary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(payerSummary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
		assertThat(sanityRestored(nonPayerSummary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
	}

	@Test
	void getsCryptoTransferReceiverSigReq() throws Throwable {
		// given:
		setupFor(CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(DEFAULT_PAYER_KT.asKey(), RECEIVER_SIG_KT.asKey()));
	}

	@Test
	void reportsMissingCryptoTransferReceiver() throws Throwable {
		// given:
		setupFor(CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO);
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingAccount(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingAccount(MISSING_ACCOUNT, txn.getTransactionID());
	}

	@Test
	void reportsGeneralErrorInCryptoTransfer() throws Throwable {
		// given:
		setupForNonStdLookup(
				CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO,
				new DelegatingSigMetadataLookup(
						FileAdapter.throwingUoe(),
						AccountAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.MISSING_FILE)),
						ContractAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT)),
						TopicAdapter.throwingUoe(),
						id -> null,
						id -> null));
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forGeneralError(any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forGeneralError(txn.getTransactionID());
	}

	@Test
	void getsCryptoUpdateVanillaNewKey() throws Throwable {
		// given:
		setupFor(CRYPTO_UPDATE_WITH_NEW_KEY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ACCOUNT_KT.asKey(), NEW_ACCOUNT_KT.asKey()));
	}

	@Test
	void getsCryptoUpdateProtectedSysAccountNewKey() throws Throwable {
		setupFor(CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NEW_KEY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(
				summary.getOrderedKeys()),
				contains(SYS_ACCOUNT_KT.asKey(), NEW_ACCOUNT_KT.asKey()));
	}

	@Test
	void getsCryptoUpdateProtectedSysAccountNoNewKey() throws Throwable {
		setupFor(CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NO_NEW_KEY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(SYS_ACCOUNT_KT.asKey()));
	}

	@Test
	void getsCryptoUpdateSysAccountWithPrivilegedPayer() throws Throwable {
		setupFor(CRYPTO_UPDATE_SYS_ACCOUNT_WITH_PRIVILEGED_PAYER);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsCryptoUpdateTreasuryWithTreasury() throws Throwable {
		setupFor(CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NO_NEW_KEY);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsCryptoUpdateTreasuryWithTreasuryAndNewKey() throws Throwable {
		setupFor(CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NEW_KEY);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(NEW_ACCOUNT_KT.asKey()));
	}

	@Test
	void getsCryptoUpdateVanillaNoNewKey() throws Throwable {
		setupFor(CRYPTO_UPDATE_NO_NEW_KEY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	void reportsCryptoUpdateMissingAccount() throws Throwable {
		setupFor(CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingAccount(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingAccount(MISSING_ACCOUNT, txn.getTransactionID());
	}

	@Test
	void getsCryptoDeleteNoTransferSigRequired() throws Throwable {
		// given:
		setupFor(CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	void getsCryptoDeleteTransferSigRequired() throws Throwable {
		// given:
		setupFor(CRYPTO_DELETE_TARGET_RECEIVER_SIG_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ACCOUNT_KT.asKey(), RECEIVER_SIG_KT.asKey()));
	}

	@Test
	void getsFileCreate() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_CREATE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_WACL_KT.asKey()));
	}

	@Test
	void getsFileAppend() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_APPEND_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
	}

	@Test
	void getsFileAppendProtected() throws Throwable {
		// given:
		setupFor(SYSTEM_FILE_APPEND_WITH_PRIVILEGD_PAYER);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsFileAppendImmutable() throws Throwable {
		// given:
		setupFor(IMMUTABLE_FILE_APPEND_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsSysFileAppendByTreasury() throws Throwable {
		// given:
		setupFor(TREASURY_SYS_FILE_APPEND_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsSysFileAppendByMaster() throws Throwable {
		// given:
		setupFor(MASTER_SYS_FILE_APPEND_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsSysFileUpdateByMaster() throws Throwable {
		// given:
		setupFor(MASTER_SYS_FILE_UPDATE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsSysFileUpdateByTreasury() throws Throwable {
		// given:
		setupFor(TREASURY_SYS_FILE_UPDATE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void reportsMissingFile() throws Throwable {
		// given:
		setupFor(FILE_APPEND_MISSING_TARGET_SCENARIO);
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingFile(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingFile(TxnHandlingScenario.MISSING_FILE, txn.getTransactionID());
	}

	@Test
	void getsFileUpdateNoNewWacl() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_UPDATE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
	}

	@Test
	void getsTreasuryUpdateNoNewWacl() throws Throwable {
		// given:
		setupFor(TREASURY_SYS_FILE_UPDATE_SCENARIO_NO_NEW_KEY);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsFileUpdateImmutable() throws Throwable {
		// given:
		setupFor(IMMUTABLE_FILE_UPDATE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsNonSystemFileUpdateNoNewWacl() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_UPDATE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(
				summary.getOrderedKeys()),
				contains(MISC_FILE_WACL_KT.asKey()));
	}

	@Test
	void getsFileUpdateNewWacl() throws Throwable {
		// given:
		setupFor(FILE_UPDATE_NEW_WACL_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(
				summary.getOrderedKeys()),
				contains(MISC_FILE_WACL_KT.asKey(), SIMPLE_NEW_WACL_KT.asKey()));
	}

	@Test
	void getsFileDelete() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_DELETE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
	}

	@Test
	void getsFileDeleteProtected() throws Throwable {
		// given:
		setupFor(VANILLA_FILE_DELETE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
	}

	@Test
	void getsFileDeleteImmutable() throws Throwable {
		// given:
		setupFor(IMMUTABLE_FILE_DELETE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsContractCreateNoAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_CREATE_NO_ADMIN_KEY);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsContractCreateDeprecatedAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_CREATE_DEPRECATED_CID_ADMIN_KEY);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsContractCreateWithAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_CREATE_WITH_ADMIN_KEY);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_ADMIN_KT.asKey()));
	}

	@Test
	void getsContractUpdateWithAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_WITH_NEW_ADMIN_KEY);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ADMIN_KT.asKey(), SIMPLE_NEW_ADMIN_KT.asKey()));
	}

	@Test
	void getsContractUpdateNewExpirationTimeOnly() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_ONLY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsContractUpdateWithDeprecatedAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_DEPRECATED_CID_ADMIN_KEY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsContractUpdateNewExpirationTimeAndAdminKey() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_ADMIN_KEY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ADMIN_KT.asKey(), SIMPLE_NEW_ADMIN_KT.asKey()));
	}

	@Test
	void getsContractUpdateNewExpirationTimeAndProxy() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_PROXY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
	}

	@Test
	void getsContractUpdateNewExpirationTimeAndAutoRenew() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_AUTORENEW_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
	}

	@Test
	void getsContractUpdateNewExpirationTimeAndFile() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_FILE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
	}

	@Test
	void getsContractUpdateNewExpirationTimeAndMemo() throws Throwable {
		// given:
		setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
	}

	@Test
	void reportsInvalidContract() throws Throwable {
		// given:
		setupForNonStdLookup(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO, INVALID_CONTRACT_THROWING_LOOKUP);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forInvalidContract(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forInvalidContract(MISC_CONTRACT, txn.getTransactionID());
	}

	@Test
	void reportsImmutableContract() throws Throwable {
		// given:
		setupForNonStdLookup(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO, IMMUTABLE_CONTRACT_THROWING_LOOKUP);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forInvalidContract(any(), any()))
				.willReturn(result);

		// when:
		var summary = subject.keysForOtherParties(txn, mockSummaryFactory);

		//then:
		verify(mockSummaryFactory).forInvalidContract(MISC_CONTRACT, txn.getTransactionID());
	}

	@Test
	void getsContractDelete() throws Throwable {
		// given:
		setupFor(CONTRACT_DELETE_XFER_ACCOUNT_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ADMIN_KT.asKey(), RECEIVER_SIG_KT.asKey()));
	}

	@Test
	void getsContractDeleteContractXfer() throws Throwable {
		// given:
		setupFor(CONTRACT_DELETE_XFER_CONTRACT_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ADMIN_KT.asKey(), DILIGENT_SIGNING_PAYER_KT.asKey()));
	}

	@Test
	void getsSystemDelete() throws Throwable {
		// given:
		setupFor(SYSTEM_DELETE_FILE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsSystemUndelete() throws Throwable {
		// given:
		setupFor(SYSTEM_UNDELETE_FILE_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsConsensusCreateTopicNoAdminKeyOrAutoRenewAccount() throws Throwable {
		// given:
		setupFor(CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForPayer(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
	}

	@Test
	void getsConsensusCreateTopicAdminKey() throws Throwable {
		// given:
		setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(SIMPLE_TOPIC_ADMIN_KEY.asKey()));
	}

	@Test
	void getsConsensusCreateTopicAdminKeyAndAutoRenewAccount() throws Throwable {
		// given:
		setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO);

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()),
				contains(SIMPLE_TOPIC_ADMIN_KEY.asKey(), MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	void invalidAutoRenewAccountOnConsensusCreateTopicThrows() throws Throwable {
		// given:
		setupFor(CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingAutoRenewAccount(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingAutoRenewAccount(MISSING_ACCOUNT, txn.getTransactionID());
	}

	@Test
	void getsConsensusSubmitMessageNoSubmitKey() throws Throwable {
		// given:
		setupForNonStdLookup(CONSENSUS_SUBMIT_MESSAGE_SCENARIO, hcsMetadataLookup(null, null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsConsensusSubmitMessageWithSubmitKey() throws Throwable {
		// given:
		setupForNonStdLookup(CONSENSUS_SUBMIT_MESSAGE_SCENARIO, hcsMetadataLookup(null, MISC_TOPIC_SUBMIT_KT.asJKey()));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_SUBMIT_KT.asKey()));
	}

	@Test
	void reportsConsensusSubmitMessageMissingTopic() throws Throwable {
		// given:
		setupFor(CONSENSUS_SUBMIT_MESSAGE_MISSING_TOPIC_SCENARIO);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingTopic(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingTopic(MISSING_TOPIC, txn.getTransactionID());
	}

	@Test
	void getsConsensusDeleteTopicNoAdminKey() throws Throwable {
		// given:
		setupForNonStdLookup(CONSENSUS_DELETE_TOPIC_SCENARIO, hcsMetadataLookup(null, null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsConsensusDeleteTopicWithAdminKey() throws Throwable {
		// given:
		setupForNonStdLookup(CONSENSUS_DELETE_TOPIC_SCENARIO, hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KT.asKey()));
	}

	@Test
	void reportsConsensusDeleteTopicMissingTopic() throws Throwable {
		// given:
		setupFor(CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO);
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingTopic(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingTopic(MISSING_TOPIC, txn.getTransactionID());
	}

	@Test
	void getsConsensusUpdateTopicNoAdminKey() throws Throwable {
		// given:
		setupForNonStdLookup(CONSENSUS_UPDATE_TOPIC_SCENARIO, hcsMetadataLookup(null, null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsConsensusUpdateTopicWithExistingAdminKey() throws Throwable {
		// given:
		setupForNonStdLookup(CONSENSUS_UPDATE_TOPIC_SCENARIO, hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KT.asKey()));
	}

	@Test
	void getsConsensusUpdateTopicExpiryOnly() throws Throwable {
		// given:
		setupForNonStdLookup(CONSENSUS_UPDATE_TOPIC_EXPIRY_ONLY_SCENARIO,
				hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void reportsConsensusUpdateTopicMissingTopic() throws Throwable {
		setupForNonStdLookup(CONSENSUS_UPDATE_TOPIC_MISSING_TOPIC_SCENARIO, hcsMetadataLookup(null, null));
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingTopic(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingTopic(MISSING_TOPIC, txn.getTransactionID());
	}

	@Test
	void invalidAutoRenewAccountOnConsensusUpdateTopicThrows() throws Throwable {
		// given:
		setupForNonStdLookup(CONSENSUS_UPDATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO, hcsMetadataLookup(null, null));
		// and:
		aMockSummaryFactory();
		// and:
		SigningOrderResult<SignatureStatus> result = mock(SigningOrderResult.class);

		given(mockSummaryFactory.forMissingAutoRenewAccount(any(), any()))
				.willReturn(result);

		// when:
		subject.keysForOtherParties(txn, mockSummaryFactory);

		// then:
		verify(mockSummaryFactory).forMissingAutoRenewAccount(MISSING_ACCOUNT, txn.getTransactionID());
	}

	@Test
	void getsConsensusUpdateTopicNewAdminKey() throws Throwable {
		// given:
		setupForNonStdLookup(CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_SCENARIO, hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KT.asKey(),
				UPDATE_TOPIC_ADMIN_KT.asKey()));
	}

	@Test
	void getsConsensusUpdateTopicNewAdminKeyAndAutoRenewAccount() throws Throwable {
		// given:
		setupForNonStdLookup(CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO,
				hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

		// when:
		SigningOrderResult<SignatureStatus> summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KT.asKey(),
				UPDATE_TOPIC_ADMIN_KT.asKey(), MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	void getsTokenCreateAdminKeyOnly() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_ADMIN_ONLY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_TREASURY_KT.asKey(), TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	void getsTokenCreateAdminAndFreeze() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_ADMIN_AND_FREEZE);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_TREASURY_KT.asKey(), TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	void getsTokenCreateCustomFixedFeeNoCollectorSigReq() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_TREASURY_KT.asKey()));
	}

	@Test
	void getsTokenCreateCustomFixedFeeAndCollectorSigReq() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
	}

	@Test
	void getsTokenCreateCustomFractionalFeeNoCollectorSigReq() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_TREASURY_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
	}

	@Test
	void getsTokenCreateCustomFeeAndCollectorMissing() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_MISSING_COLLECTOR);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_FEE_COLLECTOR, summary.getErrorReport().getStatusCode());
	}

	@Test
	void getsTokenCreateMissingAdmin() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_MISSING_ADMIN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_TREASURY_KT.asKey()));
	}

	@Test
	void getsTokenTransactAllSenders() throws Throwable {
		// given:
		setupFor(TOKEN_TRANSACT_WITH_EXTANT_SENDERS);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(FIRST_TOKEN_SENDER_KT.asKey(), SECOND_TOKEN_SENDER_KT.asKey()));
	}

	@Test
	void getsTokenTransactMovingHbarsReceiverSigReq() throws Throwable {
		// given:
		setupFor(TOKEN_TRANSACT_MOVING_HBARS_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDER);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(FIRST_TOKEN_SENDER_KT.asKey(), RECEIVER_SIG_KT.asKey()));
	}

	@Test
	void getsTokenTransactMovingHbars() throws Throwable {
		// given:
		setupFor(TOKEN_TRANSACT_MOVING_HBARS_WITH_EXTANT_SENDER);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(FIRST_TOKEN_SENDER_KT.asKey()));
	}

	@Test
	void getsTokenTransactMissingSenders() throws Throwable {
		// given:
		setupFor(TOKEN_TRANSACT_WITH_MISSING_SENDERS);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsTokenTransactWithReceiverSigReq() throws Throwable {
		// given:
		setupFor(TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(
						FIRST_TOKEN_SENDER_KT.asKey(),
						SECOND_TOKEN_SENDER_KT.asKey(),
						RECEIVER_SIG_KT.asKey()));
	}

	@Test
	void getsAssociateWithKnownTarget() throws Throwable {
		// given:
		setupFor(TOKEN_ASSOCIATE_WITH_KNOWN_TARGET);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	void getsAssociateWithMissingTarget() throws Throwable {
		// given:
		setupFor(TOKEN_ASSOCIATE_WITH_MISSING_TARGET);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsDissociateWithKnownTarget() throws Throwable {
		// given:
		setupFor(TOKEN_DISSOCIATE_WITH_KNOWN_TARGET);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	void getsDissociateWithMissingTarget() throws Throwable {
		// given:
		setupFor(TOKEN_DISSOCIATE_WITH_MISSING_TARGET);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsTokenFreezeWithExtantFreezable() throws Throwable {
		// given:
		setupFor(VALID_FREEZE_WITH_EXTANT_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_FREEZE_KT.asKey()));
	}

	@Test
	void getsTokenUnfreezeWithExtantFreezable() throws Throwable {
		// given:
		setupFor(VALID_UNFREEZE_WITH_EXTANT_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_FREEZE_KT.asKey()));
	}

	@Test
	void getsTokenGrantKycWithExtantFreezable() throws Throwable {
		// given:
		setupFor(VALID_GRANT_WITH_EXTANT_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_KYC_KT.asKey()));
	}

	@Test
	void getsTokenRevokeKycWithExtantFreezable() throws Throwable {
		// given:
		setupFor(VALID_REVOKE_WITH_EXTANT_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_KYC_KT.asKey()));
	}

	@Test
	void getsTokenRevokeKycWithMissingToken() throws Throwable {
		// given:
		setupFor(REVOKE_WITH_MISSING_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_TOKEN_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	void getsTokenRevokeKycWithoutKyc() throws Throwable {
		// given:
		setupFor(REVOKE_FOR_TOKEN_WITHOUT_KYC);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsTokenMintWithValidId() throws Throwable {
		// given:
		setupFor(MINT_WITH_SUPPLY_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_SUPPLY_KT.asKey()));
	}

	@Test
	void getsTokenBurnWithValidId() throws Throwable {
		// given:
		setupFor(BURN_WITH_SUPPLY_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_SUPPLY_KT.asKey()));
	}

	@Test
	void getsTokenDeletionWithValidId() throws Throwable {
		// given:
		setupFor(DELETE_WITH_KNOWN_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	void getsTokenDeletionWithMissingToken() throws Throwable {
		// given:
		setupFor(DELETE_WITH_MISSING_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_TOKEN_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	void getsTokenDeletionWithNoAdminKey() throws Throwable {
		// given:
		setupFor(DELETE_WITH_MISSING_TOKEN_ADMIN_KEY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsTokenWipeWithRelevantKey() throws Throwable {
		// given:
		setupFor(VALID_WIPE_WITH_EXTANT_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_WIPE_KT.asKey()));
	}

	@Test
	void getsUpdateNoSpecialKeys() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_NO_KEYS_AFFECTED);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	void getsUpdateWithWipe() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_WIPE_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	void getsUpdateWithSupply() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_SUPPLY_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	void getsUpdateWithKyc() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_KYC_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	void getsUpdateWithMissingTreasury() throws Throwable {
		// given:
		setupFor(UPDATE_REPLACING_WITH_MISSING_TREASURY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_ACCOUNT_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	void getsUpdateWithNewTreasury() throws Throwable {
		// given:
		setupFor(UPDATE_REPLACING_TREASURY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey(), TOKEN_TREASURY_KT.asKey()));
	}

	@Test
	void getsUpdateWithFreeze() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_FREEZE_KEYED_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey()));
	}

	@Test
	void getsUpdateReplacingAdmin() throws Throwable {
		// given:
		setupFor(UPDATE_REPLACING_ADMIN_KEY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey(), TOKEN_REPLACE_KT.asKey()));
	}

	@Test
	void getsTokenUpdateWithMissingToken() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_MISSING_TOKEN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_TOKEN_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	void getsTokenUpdateWithNoAdminKey() throws Throwable {
		// given:
		setupFor(UPDATE_WITH_MISSING_TOKEN_ADMIN_KEY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsTokenCreateWithAutoRenew() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_AUTO_RENEW);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_TREASURY_KT.asKey(), MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	void getsTokenCreateWithMissingAutoRenew() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		// and:
		assertEquals(SignatureStatusCode.INVALID_AUTO_RENEW_ACCOUNT_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	void getsTokenUpdateWithAutoRenew() throws Throwable {
		// given:
		setupFor(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_ADMIN_KT.asKey(), MISC_ACCOUNT_KT.asKey()));
	}

	@Test
	void getsTokenFeeScheduleUpdateWithMissingFeeScheduleKey() throws Throwable {
		// given:
		setupFor(UPDATE_TOKEN_WITH_NO_FEE_SCHEDULE_KEY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
	}

	@Test
	void getsTokenFeeScheduleUpdateWithMissingToken() throws Throwable {
		// given:
		setupFor(UPDATE_TOKEN_FEE_SCHEDULE_BUT_TOKEN_DOESNT_EXIST);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_TOKEN_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	void getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorSigReq() throws Throwable {
		// given:
		setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_MISSING_FEE_COLLECTOR);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.hasErrorReport());
		assertEquals(INVALID_CUSTOM_FEE_COLLECTOR, summary.getErrorReport().getResponseCode());
	}

	@Test
	void getsTokenFeeScheduleUpdateWithFeeScheduleKey() throws Throwable {
		// given:
		setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_SIG_REQ);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(TOKEN_FEE_SCHEDULE_KT.asKey()));
	}

	@Test
	void getsTokenUpdateWithMissingAutoRenew() throws Throwable {
		// given:
		setupFor(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		// and:
		assertEquals(SignatureStatusCode.INVALID_AUTO_RENEW_ACCOUNT_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	void getsScheduleCreateInvalidXfer() throws Throwable {
		// given:
		setupFor(SCHEDULE_CREATE_INVALID_XFER);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.hasErrorReport());
		assertEquals(UNRESOLVABLE_REQUIRED_SIGNERS, summary.getErrorReport().getResponseCode());
	}

	@Test
	void getsScheduleCreateXferNoAdmin() throws Throwable {
		// given:
		setupFor(SCHEDULE_CREATE_XFER_NO_ADMIN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ACCOUNT_KT.asKey(), RECEIVER_SIG_KT.asKey()));
		// and:
		assertTrue(summary.getOrderedKeys().get(0).isForScheduledTxn());
		assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
	}

	@Test
	void getsScheduleCreateWithAdmin() throws Throwable {
		// given:
		setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(
						SCHEDULE_ADMIN_KT.asKey(),
						MISC_ACCOUNT_KT.asKey(),
						RECEIVER_SIG_KT.asKey()));
		// and:
		assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
		assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
	}

	@Test
	void getsScheduleCreateWithMissingDesignatedPayer() throws Throwable {
		// given:
		setupFor(SCHEDULE_CREATE_XFER_WITH_MISSING_PAYER);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.hasErrorReport());
		assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport().getResponseCode());
	}

	@Test
	void getsScheduleCreateWithAdminAndDesignatedPayer() throws Throwable {
		// given:
		setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(
						SCHEDULE_ADMIN_KT.asKey(),
						DILIGENT_SIGNING_PAYER_KT.asKey(),
						MISC_ACCOUNT_KT.asKey(),
						RECEIVER_SIG_KT.asKey()));
		// and:
		assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
		assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
		assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
		assertTrue(summary.getOrderedKeys().get(3).isForScheduledTxn());
	}

	@Test
	void getsScheduleSignKnownScheduleWithPayer() throws Throwable {
		// given:
		setupFor(SCHEDULE_SIGN_KNOWN_SCHEDULE_WITH_PAYER);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(
						DILIGENT_SIGNING_PAYER_KT.asKey(),
						MISC_ACCOUNT_KT.asKey(),
						RECEIVER_SIG_KT.asKey()));
		// and:
		assertTrue(summary.getOrderedKeys().get(0).isForScheduledTxn());
		assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
		assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
	}

	@Test
	void getsScheduleSignKnownScheduleWithNowInvalidPayer() throws Throwable {
		// given:
		setupFor(SCHEDULE_SIGN_KNOWN_SCHEDULE_WITH_NOW_INVALID_PAYER);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_ACCOUNT_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	void getsScheduleSignKnownSchedule() throws Throwable {
		// given:
		setupFor(SCHEDULE_SIGN_KNOWN_SCHEDULE);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(MISC_ACCOUNT_KT.asKey(), RECEIVER_SIG_KT.asKey()));
		// and:
		assertTrue(summary.getOrderedKeys().get(0).isForScheduledTxn());
		assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
	}

	@Test
	void getsScheduleSignWithMissingSchedule() throws Throwable {
		// given:
		setupFor(SCHEDULE_SIGN_MISSING_SCHEDULE);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_SCHEDULE_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	void getsScheduleDeleteWithMissingSchedule() throws Throwable {
		// given:
		setupFor(SCHEDULE_DELETE_WITH_MISSING_SCHEDULE);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
		assertEquals(SignatureStatusCode.INVALID_SCHEDULE_ID, summary.getErrorReport().getStatusCode());
	}

	@Test
	void getsScheduleDeleteWithMissingAdminKey() throws Throwable {
		// given:
		setupFor(SCHEDULE_DELETE_WITH_MISSING_SCHEDULE_ADMIN_KEY);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);

		// then:
		assertTrue(summary.getOrderedKeys().isEmpty());
	}

	@Test
	void getsScheduleDeleteKnownSchedule() throws Throwable {
		// given:
		setupFor(SCHEDULE_DELETE_WITH_KNOWN_SCHEDULE);

		// when:
		var summary = subject.keysForOtherParties(txn, summaryFactory);
		// then:
		assertThat(
				sanityRestored(summary.getOrderedKeys()),
				contains(SCHEDULE_ADMIN_KT.asKey()));
	}

	private void setupFor(TxnHandlingScenario scenario) throws Throwable {
		setupFor(scenario, Optional.empty(), mockSignatureWaivers);
	}

	private void setupForNonStdLookup(
			TxnHandlingScenario scenario,
			SigMetadataLookup sigMetadataLookup
	) throws Throwable {
		setupFor(
				scenario,
				Optional.of(sigMetadataLookup),
				mockSignatureWaivers);
	}

	private void setupFor(
			TxnHandlingScenario scenario,
			Optional<SigMetadataLookup> sigMetaLookup,
			SignatureWaivers signatureWaivers
	) throws Throwable {
		txn = scenario.platformTxn().getTxn();
		hfs = scenario.hfs();
		accounts = scenario.accounts();
		topics = scenario.topics();
		tokenStore = scenario.tokenStore();
		scheduleStore = scenario.scheduleStore();

		subject = new HederaSigningOrder(
				sigMetaLookup.orElse(
						defaultLookupsFor(
								hfs,
								() -> accounts,
								() -> topics,
								SigMetadataLookup.REF_LOOKUP_FACTORY.apply(tokenStore),
								SigMetadataLookup.SCHEDULE_REF_LOOKUP_FACTORY.apply(scheduleStore))),
				new MockGlobalDynamicProps(),
				signatureWaivers);
	}

	private void aMockSummaryFactory() {
		mockSummaryFactory = (SigningOrderResultFactory<SignatureStatus>) mock(SigningOrderResultFactory.class);
	}

	private SigMetadataLookup hcsMetadataLookup(JKey adminKey, JKey submitKey) {
		return new DelegatingSigMetadataLookup(
				FileAdapter.throwingUoe(),
				AccountAdapter.withSafe(id -> {
					if (id.equals(asAccount(MISC_ACCOUNT_ID))) {
						try {
							return new SafeLookupResult<>(
									new AccountSigningMetadata(MISC_ACCOUNT_KT.asJKey(), false));
						} catch (Exception e) {
							throw new IllegalArgumentException(e);
						}
					} else {
						return SafeLookupResult.failure(KeyOrderingFailure.MISSING_ACCOUNT);
					}
				}),
				ContractAdapter.withSafe(id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT)),
				TopicAdapter.withSafe(id -> {
					if (id.equals(asTopic(EXISTING_TOPIC_ID))) {
						return new SafeLookupResult<>(new TopicSigningMetadata(adminKey, submitKey));
					} else {
						return SafeLookupResult.failure(KeyOrderingFailure.INVALID_TOPIC);
					}
				}),
				id -> null,
				id -> null
		);
	}

	static List<Key> sanityRestored(List<JKey> jKeys) {
		return jKeys.stream().map(jKey -> {
					try {
						return JKey.mapJKey(jKey);
					} catch (Exception ignore) {
					}
					throw new AssertionError("All keys should be mappable!");
				}
		).collect(toList());
	}
}
