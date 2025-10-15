/*
 * Copyright 2010 Proofpoint, Inc.
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
package io.airlift.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.Stage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestLifeCycleManager {
    private static final List<String> stateLog = new CopyOnWriteArrayList<>();

    @BeforeEach
    public void setup() {
        stateLog.clear();
    }

    static void note(String str) {
        // I'm assuming that tests are run serially
        stateLog.add(str);
    }

    @Test
    public void testImmediateStarts() {
        Injector injector = Guice.createInjector(Stage.PRODUCTION, new LifeCycleModule(), binder -> {
            binder.bind(InstanceThatRequiresStart.class).in(Scopes.SINGLETON);
            binder.bind(InstanceThatUsesInstanceThatRequiresStart.class).in(Scopes.SINGLETON);
        });

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();

        assertThat(stateLog).isEqualTo(ImmutableList.of("InstanceThatUsesInstanceThatRequiresStart:OK"));
    }

    @Test
    public void testPrivateModule() {
        Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                new LifeCycleModule(),
                binder -> binder.install(new PrivateModule() {
                    @Override
                    protected void configure() {
                        binder().bind(SimpleBase.class).to(SimpleBaseImpl.class).in(Scopes.SINGLETON);
                        binder().expose(SimpleBase.class);
                    }
                }));

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        assertThat(stateLog).isEqualTo(ImmutableList.of("postSimpleBaseImpl"));

        lifeCycleManager.stop();
        assertThat(stateLog).isEqualTo(ImmutableList.of("postSimpleBaseImpl", "preSimpleBaseImpl"));
    }

    @Test
    public void testSubClassAnnotated() {
        Injector injector =
                Guice.createInjector(Stage.PRODUCTION, new LifeCycleModule(), binder -> binder.bind(SimpleBase.class)
                        .to(SimpleBaseImpl.class)
                        .in(Scopes.SINGLETON));

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        assertThat(stateLog).isEqualTo(ImmutableList.of("postSimpleBaseImpl"));

        lifeCycleManager.stop();

        assertThat(stateLog).isEqualTo(ImmutableList.of("postSimpleBaseImpl", "preSimpleBaseImpl"));
    }

    @Test
    public void testExecuted() throws Exception {
        Injector injector = Guice.createInjector(
                Stage.PRODUCTION, new LifeCycleModule(), binder -> binder.bind(ExecutedInstance.class)
                        .in(Scopes.SINGLETON));
        ExecutedInstance instance = injector.getInstance(ExecutedInstance.class);

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        instance.waitForStart();
        assertThat(stateLog).isEqualTo(ImmutableList.of("Starting"));

        lifeCycleManager.stop();
        instance.waitForEnd();

        assertThat(stateLog).isEqualTo(ImmutableList.of("Starting", "Done"));
    }

    @Test
    public void testDeepDependency() {
        Injector injector = Guice.createInjector(Stage.PRODUCTION, new LifeCycleModule(), binder -> {
            binder.bind(AnInstance.class).in(Scopes.SINGLETON);
            binder.bind(AnotherInstance.class).in(Scopes.SINGLETON);
            binder.bind(DependentInstance.class).in(Scopes.SINGLETON);
        });

        injector.getInstance(AnotherInstance.class);

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        assertThat(stateLog).isEqualTo(ImmutableList.of("postDependentInstance"));

        lifeCycleManager.stop();
        assertThat(stateLog).isEqualTo(ImmutableList.of("postDependentInstance", "preDependentInstance"));
    }

    @Test
    public void testIllegalMethods() {
        try {
            Guice.createInjector(
                    Stage.PRODUCTION,
                    binder -> binder.bind(IllegalInstance.class).in(Scopes.SINGLETON),
                    new LifeCycleModule());
            fail();
        } catch (CreationException dummy) {
            // correct behavior
        }
    }

    @Test
    public void testDuplicateMethodNames() {
        Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                binder -> binder.bind(FooTestInstance.class).in(Scopes.SINGLETON),
                new LifeCycleModule());

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        lifeCycleManager.stop();

        assertThat(stateLog).isEqualTo(ImmutableList.of("foo"));
    }

    @Test
    public void testJITInjection() {
        Injector injector = Guice.createInjector(Stage.PRODUCTION, new LifeCycleModule(), binder -> {
            binder.bind(AnInstance.class).in(Scopes.SINGLETON);
            binder.bind(DependentInstance.class).in(Scopes.SINGLETON);
        });
        injector.getInstance(AnInstance.class);

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        lifeCycleManager.stop();

        assertThat(stateLog).isEqualTo(ImmutableList.of("postDependentInstance", "preDependentInstance"));
    }

    @Test
    public void testPreDestroySuppressedExceptionHandling() {
        Injector injector = Guice.createInjector(Stage.PRODUCTION, new LifeCycleModule(), binder -> {
            binder.bind(DependentInstance.class).in(Scopes.SINGLETON);
            binder.bind(DestroyExceptionInstance.class).in(Scopes.SINGLETON);
        });

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        assertThat(stateLog).isEqualTo(ImmutableList.of("postDependentInstance"));
        try {
            lifeCycleManager.stopWithoutFailureLogging();
            fail("Expected exception to be thrown");
        } catch (LifeCycleStopException e) {
            assertThat(e.getSuppressed())
                    .as("Expected two suppressed exceptions")
                    .hasSize(2);
            Set<String> suppressedMessages =
                    Arrays.stream(e.getSuppressed()).map(Throwable::getMessage).collect(Collectors.toSet());
            assertThat(ImmutableSet.copyOf(suppressedMessages))
                    .isEqualTo(ImmutableSet.of("preDestroyExceptionOne", "preDestroyExceptionTwo"));
        }

        assertThat(ImmutableSet.copyOf(stateLog))
                .isEqualTo(ImmutableSet.of(
                        "postDependentInstance",
                        "preDestroyExceptionOne",
                        "preDestroyExceptionTwo",
                        "preDependentInstance"));
    }

    @Test
    public void testPreDestroyLoggingExceptionHandling() {
        Injector injector = Guice.createInjector(Stage.PRODUCTION, new LifeCycleModule(), binder -> {
            binder.bind(DependentInstance.class).in(Scopes.SINGLETON);
            binder.bind(DestroyExceptionInstance.class).in(Scopes.SINGLETON);
        });

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        assertThat(stateLog).isEqualTo(ImmutableList.of("postDependentInstance"));
        try {
            lifeCycleManager.stop();
            fail("Expected exception to be thrown");
        } catch (LifeCycleStopException e) {
            assertThat(e.getSuppressed())
                    .as("Suppressed exceptions list should be empty")
                    .hasSize(0);
        }

        assertThat(ImmutableSet.copyOf(stateLog))
                .isEqualTo(ImmutableSet.of(
                        "postDependentInstance",
                        "preDestroyExceptionOne",
                        "preDestroyExceptionTwo",
                        "preDependentInstance"));
    }

    @Test
    public void testPostConstructExceptionCallsPreDestroy() {
        try {
            Guice.createInjector(
                    Stage.PRODUCTION, new LifeCycleModule(), binder -> binder.bind(PostConstructExceptionInstance.class)
                            .in(Scopes.SINGLETON));
            fail("Expected injector creation to fail with an exception");
        } catch (CreationException e) {
            assertThat(ImmutableSet.copyOf(stateLog))
                    .isEqualTo(ImmutableSet.of(
                            "postConstructFailure",
                            "preDestroyFailureAfterPostConstructFailureOne",
                            "preDestroyFailureAfterPostConstructFailureTwo"));
            assertThat(e.getCause().getClass())
                    .as("Expected LifeCycleStartException to be thrown, found: "
                            + e.getCause().getClass())
                    .isEqualTo(LifeCycleStartException.class);
            assertThat(e.getCause().getSuppressed())
                    .as("Expected two suppressed exceptions")
                    .hasSize(2);
            assertThat(ImmutableSet.copyOf(Arrays.stream(e.getCause().getSuppressed())
                            .map(Throwable::getCause)
                            .map(Throwable::getMessage)
                            .collect(Collectors.toSet())))
                    .isEqualTo(ImmutableSet.of(
                            "preDestroyFailureAfterPostConstructFailureOne",
                            "preDestroyFailureAfterPostConstructFailureTwo"));
        }
    }

    @Test
    public void testNoPreDestroy() {
        Injector injector = Guice.createInjector(Stage.PRODUCTION, new LifeCycleModule(), binder -> {
            binder.bind(PostConstructOnly.class).in(Scopes.SINGLETON);
            binder.bind(PreDestroyOnly.class).in(Scopes.SINGLETON);
        });
        injector.getInstance(PostConstructOnly.class);

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        assertThat(stateLog).isEqualTo(ImmutableList.of("makeMe"));

        lifeCycleManager.stop();
        assertThat(stateLog).isEqualTo(ImmutableList.of("makeMe", "unmakeMe"));
    }

    @Test
    public void testModule() {
        Injector injector = Guice.createInjector(Stage.PRODUCTION, new LifeCycleModule(), binder -> {
            binder.bind(DependentBoundInstance.class)
                    .to(DependentInstanceImpl.class)
                    .in(Scopes.SINGLETON);

            binder.bind(DependentInstance.class).in(Scopes.SINGLETON);
            binder.bind(InstanceOne.class).in(Scopes.SINGLETON);
            binder.bind(InstanceTwo.class).in(Scopes.SINGLETON);
        });

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        lifeCycleManager.stop();

        Set<String> stateLogSet = new HashSet<>(stateLog);
        assertThat(stateLogSet)
                .isEqualTo(Sets.newHashSet(
                        "postDependentBoundInstance",
                        "postDependentInstance",
                        "postMakeOne",
                        "postMakeTwo",
                        "preDestroyTwo",
                        "preDestroyOne",
                        "preDependentInstance",
                        "preDependentBoundInstance"));
    }

    @Test
    public void testProvider() {
        Injector injector =
                Guice.createInjector(Stage.PRODUCTION, new LifeCycleModule(), binder -> binder.bind(BarInstance.class)
                        .toProvider(BarProvider.class)
                        .in(Scopes.SINGLETON));

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        lifeCycleManager.start();
        assertThat(stateLog).isEqualTo(ImmutableList.of("postBarProvider", "postBarInstance"));

        lifeCycleManager.stop();
        assertThat(stateLog)
                .isEqualTo(ImmutableList.of("postBarProvider", "postBarInstance", "preBarInstance", "preBarProvider"));
    }

    @Test
    public void testProviderMethod() {
        Injector injector = Guice.createInjector(Stage.PRODUCTION, new LifeCycleModule(), new Module() {
            @Override
            public void configure(Binder binder) {}

            @Provides
            @Singleton
            public BarInstance create() {
                return new BarInstance();
            }
        });

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        lifeCycleManager.start();
        assertThat(stateLog).isEqualTo(ImmutableList.of("postBarInstance"));

        lifeCycleManager.stop();
        assertThat(stateLog).isEqualTo(ImmutableList.of("postBarInstance", "preBarInstance"));
    }

    @Test
    public void testProviderReturningNull() {
        Injector injector = Guice.createInjector(Stage.PRODUCTION, new LifeCycleModule(), new Module() {
            @Override
            public void configure(Binder binder) {}

            @Provides
            public BarInstance createBar() {
                return null;
            }
        });

        assertThat(injector.getInstance(BarInstance.class)).isNull();

        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        lifeCycleManager.start();
        lifeCycleManager.stop();

        assertThat(injector.getInstance(BarInstance.class)).isNull();
    }
}
