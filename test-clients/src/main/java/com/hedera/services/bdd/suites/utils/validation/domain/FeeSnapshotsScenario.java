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
package com.hedera.services.bdd.suites.utils.validation.domain;

public class FeeSnapshotsScenario {
    final long DEFAULT_TINYBARS_TO_OFFER = 10_000_000_000L;
    final String DEFAULT_SCHEDULE_DESC = "Bootstrap";

    Long tinyBarsToOffer = DEFAULT_TINYBARS_TO_OFFER;
    String scheduleDesc = DEFAULT_SCHEDULE_DESC;
    Boolean ignoreCostAnswer = Boolean.TRUE;
    Boolean appendToSnapshotCsv = Boolean.TRUE;
    SnapshotOpsConfig opsConfig = new SnapshotOpsConfig();

    public Long getTinyBarsToOffer() {
        return tinyBarsToOffer;
    }

    public void setTinyBarsToOffer(Long tinyBarsToOffer) {
        this.tinyBarsToOffer = tinyBarsToOffer;
    }

    public SnapshotOpsConfig getOpsConfig() {
        return opsConfig;
    }

    public void setOpsConfig(SnapshotOpsConfig opsConfig) {
        this.opsConfig = opsConfig;
    }

    public Boolean getAppendToSnapshotCsv() {
        return appendToSnapshotCsv;
    }

    public void setAppendToSnapshotCsv(Boolean appendToSnapshotCsv) {
        this.appendToSnapshotCsv = appendToSnapshotCsv;
    }

    public String getScheduleDesc() {
        return scheduleDesc;
    }

    public void setScheduleDesc(String scheduleDesc) {
        this.scheduleDesc = scheduleDesc;
    }

    public Boolean getIgnoreCostAnswer() {
        return ignoreCostAnswer;
    }

    public void setIgnoreCostAnswer(Boolean ignoreCostAnswer) {
        this.ignoreCostAnswer = ignoreCostAnswer;
    }
}
