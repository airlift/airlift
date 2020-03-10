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

import io.airlift.log.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

/**
 * Manages PostConstruct and PreDestroy life cycles
 */
public final class LifeCycleManager
{
    private final Logger log = Logger.get(LifeCycleManager.class);
    private final AtomicReference<State> state = new AtomicReference<>(State.LATENT);
    private final Queue<Object> managedInstances = new ConcurrentLinkedQueue<>();
    private final LifeCycleMethodsMap methodsMap;
    private final AtomicReference<Thread> shutdownHook = new AtomicReference<>();

    /**
     * Provides a mechanism to handle exceptions raised from {@link PreDestroy} methods in
     * whatever way makes the most sense. Implementations should not raise exceptions of
     * their own
     */
    private interface LifeCycleStopFailureHandler
    {
        void handlePreDestroyException(Class<?> klass, Method method, Exception exception);
    }

    private enum State
    {
        LATENT,
        STARTING,
        STARTED,
        STOPPING,
        STOPPED
    }

    /**
     * @param managedInstances list of objects that have life cycle annotations
     * @param methodsMap existing or new methods map
     * @throws LifeCycleStartException exceptions starting instances (depending on mode)
     */
    public LifeCycleManager(List<Object> managedInstances, LifeCycleMethodsMap methodsMap)
            throws LifeCycleStartException
    {
        this.methodsMap = (methodsMap != null) ? methodsMap : new LifeCycleMethodsMap();
        for (Object instance : managedInstances) {
            addInstance(instance);
        }
    }

    /**
     * Returns the number of managed instances
     *
     * @return qty
     */
    public int size()
    {
        return managedInstances.size();
    }

    /**
     * Start the life cycle - all instances will have their {@link PostConstruct} method(s) called
     */
    public void start()
            throws LifeCycleStartException
    {
        if (!state.compareAndSet(State.LATENT, State.STARTING)) {
            throw new LifeCycleStartException("System already starting");
        }
        log.info("Life cycle starting...");

        for (Object obj : managedInstances) {
            LifeCycleMethods methods = methodsMap.get(obj.getClass());
            if (!methods.hasFor(PreDestroy.class)) {
                managedInstances.remove(obj);   // remove reference to instances that aren't needed anymore
            }
        }

        Thread thread = new Thread(() -> {
            try {
                log.info("JVM is shutting down, cleaning up");
                stop();
            }
            catch (Exception e) {
                log.error(e, "Trying to shut down");
            }
        });
        shutdownHook.set(thread);
        Runtime.getRuntime().addShutdownHook(thread);

        state.set(State.STARTED);
        log.info("Life cycle startup complete");
    }

    /**
     * Stop the life cycle - all instances will have their {@link PreDestroy} method(s) called
     * and any exceptions raised will be collected and thrown in a wrapped {@link LifeCycleStopException} as
     * suppressed exceptions. Those failures will not be logged and are the responsibility of the caller to
     * handle appropriately.
     *
     * @throws LifeCycleStopException If any failure occurs during the clean up process
     */
    public void stopWithoutFailureLogging()
            throws LifeCycleStopException
    {
        List<Exception> failures = new ArrayList<>();
        stop((klass, method, exception) -> failures.add(exception));
        if (!failures.isEmpty()) {
            LifeCycleStopException stopException = new LifeCycleStopException();
            for (Exception e : failures) {
                stopException.addSuppressed(e);
            }
            throw stopException;
        }
    }

    /**
     * Stop the life cycle - all instances will have their {@link PreDestroy} method(s) called
     * and any exceptions raised will be immediately logged. If any such exceptions occur, a single
     * {@link LifeCycleStopException} will be raised at the end of processing which will <b>not</b>
     * contain any reference to exceptions already logged.
     *
     * @throws LifeCycleStopException If any failure occurs during the clean up process
     */
    public void stop()
            throws LifeCycleStopException
    {
        AtomicBoolean failure = new AtomicBoolean(false);
        stop((klass, method, exception) -> {
            failure.set(true);
            log.error(exception, "Exception in PreDestroy method %s::%s()", klass.getName(), method.getName());
        });

        if (failure.get()) {
            throw new LifeCycleStopException();
        }
    }

    /**
     * Stop the life cycle - all instances will have their {@link PreDestroy} method(s) called and any
     * exceptions raised will be passed to the provided {@link LifeCycleStopFailureHandler} to collect.
     */
    private void stop(LifeCycleStopFailureHandler handler)
    {
        if (!state.compareAndSet(State.STARTED, State.STOPPING)) {
            return;
        }

        Thread thread = shutdownHook.getAndSet(null);
        if (thread != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(thread);
            }
            catch (IllegalStateException ignored) {
            }
        }

        log.info("Life cycle stopping...");

        List<Object> reversedInstances = new ArrayList<>(managedInstances);
        Collections.reverse(reversedInstances);

        for (Object obj : reversedInstances) {
            stopInstance(obj, handler);
        }

        state.set(State.STOPPED);
        log.info("Life cycle stopped");
    }

    /**
     * Add an additional managed instance
     *
     * @param instance instance to add
     * @throws LifeCycleStartException errors during {@link PostConstruct} method invocation
     * @throws IllegalStateException if the life cycle has been stopped
     */
    public void addInstance(Object instance)
            throws LifeCycleStartException
    {
        State currentState = state.get();
        checkState((currentState != State.STOPPING) && (currentState != State.STOPPED), "life cycle is stopped");
        startInstance(instance);
        if (methodsMap.get(instance.getClass()).hasFor(PreDestroy.class)) {
            managedInstances.add(instance);
        }
    }

    private void stopInstance(Object obj, LifeCycleStopFailureHandler handler)
    {
        log.debug("Stopping %s", obj.getClass().getName());
        LifeCycleMethods methods = methodsMap.get(obj.getClass());
        for (Method preDestroy : methods.methodsFor(PreDestroy.class)) {
            log.debug("\t%s()", preDestroy.getName());
            try {
                preDestroy.invoke(obj);
            }
            catch (Exception e) {
                handler.handlePreDestroyException(obj.getClass(), preDestroy, unwrapInvocationTargetException(e));
            }
        }
    }

    private void startInstance(Object obj)
            throws LifeCycleStartException
    {
        log.debug("Starting %s", obj.getClass().getName());
        LifeCycleMethods methods = methodsMap.get(obj.getClass());
        for (Method postConstruct : methods.methodsFor(PostConstruct.class)) {
            log.debug("\t%s()", postConstruct.getName());
            try {
                postConstruct.invoke(obj);
            }
            catch (Exception e) {
                LifeCycleStartException failure = new LifeCycleStartException(
                        format("Exception in PostConstruct method %s::%s()", obj.getClass().getName(), postConstruct.getName()),
                        unwrapInvocationTargetException(e));
                stopInstance(obj, (Class<?> klass, Method method, Exception exception) -> {
                    String message = format("Exception in PreDestroy method %1$s::%2$s() after PostConstruct failure in %1$s::%3$s()", klass.getName(), method.getName(), postConstruct.getName());
                    failure.addSuppressed(new RuntimeException(message, exception));
                });
                throw failure;
            }
        }
    }

    /**
     * Unwraps {@link InvocationTargetException} instances to their underlying cause, otherwise returns the provided Exception
     */
    private static Exception unwrapInvocationTargetException(Exception e)
    {
        return (e instanceof InvocationTargetException && e.getCause() instanceof Exception) ? (Exception) e.getCause() : e;
    }
}
