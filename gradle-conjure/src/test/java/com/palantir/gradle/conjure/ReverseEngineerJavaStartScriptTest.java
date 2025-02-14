/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.conjure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Paths;
import org.junit.Test;

public class ReverseEngineerJavaStartScriptTest {

    @Test
    public void dont_freak_out_with_windows_files() {
        assertThat(ReverseEngineerJavaStartScript.maybeParseStartScript(
                        Paths.get("src/test/resources/bin/start-script.bat")))
                .isEmpty();
    }

    @Test
    public void real_thingy() {
        assertThat(ReverseEngineerJavaStartScript.maybeParseStartScript(
                        Paths.get("src/test/resources/bin/start-script")))
                .hasValueSatisfying(info -> {
                    assertThat(info.mainClass()).isEqualTo("com.palantir.conjure.cli.ConjureCli");
                    assertThat(info.classpath())
                            .containsExactly(
                                    new File("src/test/resources/lib/foo-4.14.1.jar"),
                                    new File("src/test/resources/lib/bar-4.14.1.jar"));
                });
    }

    @Test
    public void test_gradle_7_format() {
        assertThat(ReverseEngineerJavaStartScript.maybeParseStartScript(
                        Paths.get("src/test/resources/bin/start-script-gradle-7")))
                .hasValueSatisfying(info -> {
                    assertThat(info.mainClass()).isEqualTo("com.palantir.conjure.cli.ConjureCli");
                    assertThat(info.classpath())
                            .containsExactly(
                                    new File("src/test/resources/lib/foo-4.14.1.jar"),
                                    new File("src/test/resources/lib/bar-4.14.1.jar"));
                });
    }
}
