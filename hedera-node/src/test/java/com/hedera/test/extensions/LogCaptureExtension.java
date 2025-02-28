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
package com.hedera.test.extensions;

import java.lang.reflect.Field;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

/**
 * JUnit5 extension that captures the logging events generated by an annotated {@link
 * LoggingSubject} per executed {@code @Test} method. The events are accessible by methods on a
 * {@link LogCaptor} instance which must be a field on the test instance.
 *
 * <p>For example, suppose {@code ExampleService} is a class whose log events are generated on a
 * {@code Logger} acquired by {@code LogManager.getLogger(ExampleService.class)}. Then we can test
 * its logs as below.
 *
 * <pre>{@code
 * {@literal @}ExtendWith(LogCaptureExtension.class)
 *  class ExampleServiceTest {
 *     {@literal @}Inject
 *     private LogCaptor logCaptor;
 *
 *     {@literal @}LoggingSubject
 *     private ExampleService subject;
 *
 *     {@literal @}Test
 *     void logsWhatIsExpected() {
 *         // when:
 *         subject.doVerbosely();
 *
 *         // then:
 *         assertEquals("2 + 2 = 4", logCaptor.debugLogs().get(0));
 *         assertEquals(
 *           List.of("Beware the Jabberwock, my son!", "The jaws that bite, the claws that catch!"),
 *           logCaptor.warnLogs());
 *     }
 *  }
 * }</pre>
 */
public class LogCaptureExtension implements TestInstancePostProcessor, AfterEachCallback {
    private LogCaptor injectedCaptor = null;

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        if (injectedCaptor != null) {
            injectedCaptor.stopCapture();
        }
    }

    @Override
    public void postProcessTestInstance(Object o, ExtensionContext extensionContext)
            throws Exception {
        Class<?> testCls = o.getClass();

        Field subject = null, logCaptor = null;

        for (var field : testCls.getDeclaredFields()) {
            if (subject == null && isSubject(field)) {
                subject = field;
            } else if (isInjectableCaptor(field)) {
                logCaptor = field;
            }
        }

        if (subject == null) {
            throw new IllegalStateException("The test class has no designated subject");
        }
        if (logCaptor == null) {
            throw new IllegalStateException(
                    "The test class has no LogCaptor field marked with @LoggingTarget");
        }

        injectCaptor(o, subject, logCaptor);
    }

    private void injectCaptor(Object test, Field subject, Field logCaptor)
            throws IllegalAccessException {
        logCaptor.setAccessible(true);
        injectedCaptor = new LogCaptor(LogManager.getLogger(subject.getType()));
        logCaptor.set(test, injectedCaptor);
    }

    private boolean isSubject(Field field) {
        var annotations = field.getDeclaredAnnotations();
        return Stream.of(annotations)
                .anyMatch(a -> a.annotationType().equals(LoggingSubject.class));
    }

    private boolean isInjectableCaptor(Field field) {
        if (!field.getType().equals(LogCaptor.class)) {
            return false;
        }
        var annotations = field.getDeclaredAnnotations();
        return Stream.of(annotations).anyMatch(a -> a.annotationType().equals(LoggingTarget.class));
    }
}
