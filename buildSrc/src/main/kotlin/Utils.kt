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
import net.swiftzer.semver.SemVer
import org.gradle.api.Project
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.util.*


class Utils {
    companion object {
        @JvmStatic
        fun updateVersion(project: Project, newVersion: SemVer) {
            val gradlePropFile = File(project.projectDir, "gradle.properties")
            val props = Properties()

            if (gradlePropFile.exists()) {
                props.load(gradlePropFile.inputStream())
            }

            props.setProperty("version", newVersion.toString())
            props.store(gradlePropFile.bufferedWriter(), null)
        }

        @JvmStatic
        fun generateProjectVersionReport(rootProject: Project, ostream: OutputStream) {
            val writer = PrintStream(ostream, false, Charsets.UTF_8)

            ostream.use {
                writer.use {
                    // Writer headers
                    writer.println("### Deployed Version Information")
                    writer.println()
                    writer.println("| Artifact Name | Version Number |")
                    writer.println("| --- | --- |")

                    // Write table rows
                    rootProject.childProjects.values.onEach {
                        writer.printf("| %s | %s |\n", it.name, it.version.toString())
                    }
                    writer.flush()
                    ostream.flush()
                }
            }
        }
    }
}
