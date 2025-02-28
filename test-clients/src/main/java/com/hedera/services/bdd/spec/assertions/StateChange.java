/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import java.util.List;

public class StateChange {
    private String contractID;
    private List<StorageChange> storageChanges;

    private StateChange(String contractID) {
        this.contractID = contractID;
    }

    public static com.hedera.services.bdd.spec.assertions.StateChange stateChangeFor(
            String contractID) {
        return new com.hedera.services.bdd.spec.assertions.StateChange(contractID);
    }

    public StateChange withStorageChanges(StorageChange... changes) {
        this.storageChanges = List.of(changes);
        return this;
    }

    public String getContractID() {
        return contractID;
    }

    public List<StorageChange> getStorageChanges() {
        return storageChanges;
    }
}
