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
package com.hedera.services.state.exports;

import com.google.common.primitives.Ints;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public class StandardSigFileWriter implements SigFileWriter {
    @Override
    public String writeSigFile(String signedFile, byte[] sig, byte[] signedFileHash) {
        var sigFile = signedFile + "_sig";
        try (FileOutputStream fout = new FileOutputStream(sigFile, false)) {
            fout.write(TYPE_FILE_HASH);
            fout.write(signedFileHash);
            fout.write(TYPE_SIGNATURE);
            fout.write(Ints.toByteArray(sig.length));
            fout.write(sig);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("I/O error writing sig of '%s'!", signedFile), e);
        }
        return sigFile;
    }
}
