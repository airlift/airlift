package com.proofpoint.lifecycle;

import com.google.common.collect.Lists;
import com.proofpoint.log.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages PostConstruct and PreDestroy life cycles
 */
public class LifeCycleManager
{
    private final Logger                    log = Logger.get(getClass());
    private final AtomicReference<State>    state = new AtomicReference<State>(State.LATENT);
    private final Queue<Object>             managedInstances = new ConcurrentLinkedQueue<Object>();
    private final LifeCycleMethodsMap       methodsMap;
    private final LifeCycleManager.Mode     mode;

    private enum State
    {
        LATENT,
        STARTING,
        STARTED,
        STOPPING,
        STOPPED
    }

    public enum Mode
    {
        /**
         * Start instances as they are added to {@link LifeCycleManager#addInstance(Object)}
         */
        START_INSTANCES_IMMEDIATELY,

        /**
         * Start instances all at once when {@link com.proofpoint.lifecycle.LifeCycleManager#start()} is called
         */
        START_INSTANCES_ALL_AT_ONCE
    }

    /**
     * @param managedInstances list of objects that have life cycle annotations
     * @param methodsMap existing or new methods map
     * @param mode run mode
     */
    public LifeCycleManager(List<Object> managedInstances, LifeCycleMethodsMap methodsMap, Mode mode)
    {
        this.mode = mode;
        this.managedInstances.addAll(managedInstances);
        this.methodsMap = methodsMap;
    }

    /**
     * Returns the number of managed instances
     *
     * @return qty
     */
    public int      size()
    {
        return managedInstances.size();
    }

    /**
     * Start the life cycle - all instances will have their {@link javax.annotation.PostConstruct} method(s) called
     *
     * @throws Exception errors
     */
    public void     start() throws Exception
    {
        if ( !state.compareAndSet(State.LATENT, State.STARTING) )
        {
            throw new Exception("System already starting");
        }
        log.info("Life cycle starting...");

        for ( Object obj : managedInstances )
        {
            if ( mode == Mode.START_INSTANCES_ALL_AT_ONCE )
            {
                startInstance(obj);
            }

            LifeCycleMethods methods = methodsMap.get(obj.getClass());
            if ( !methods.hasFor(PreDestroy.class) )
            {
                managedInstances.remove(obj);   // remove reference to instances that aren't needed anymore
            }
        }

        Runtime.getRuntime().addShutdownHook
        (
            new Thread()
            {
                @Override
                public void run()
                {
                    try
                    {
                        LifeCycleManager.this.stop();
                    }
                    catch ( Exception e )
                    {
                        log.error(e, "Trying to shut down");
                    }
                }
            }
        );

        state.set(State.STARTED);
        log.info("Life cycle startup complete. System ready.");
    }

    /**
     * Stop the life cycle - all instances will have their {@link javax.annotation.PreDestroy} method(s) called
     *
     * @throws Exception errors
     */
    public void     stop() throws Exception
    {
        if ( !state.compareAndSet(State.STARTED, State.STOPPING) )
        {
            return;
        }

        log.info("Life cycle stopping...");

        List<Object>        reversedInstances = Lists.newArrayList(managedInstances);
        Collections.reverse(reversedInstances);

        for ( Object obj : reversedInstances )
        {
            log.debug("Stopping %s", obj.getClass().getName());
            LifeCycleMethods        methods = methodsMap.get(obj.getClass());
            for ( Method preDestroy : methods.methodsFor(PreDestroy.class) )
            {
                log.debug("\t%s()", preDestroy.getName());
                preDestroy.invoke(obj);
            }
        }

        state.set(State.STOPPED);
        log.info("Life cycle stopped.");
    }

    /**
     * Add an additional managed instance
     *
     * @param instance instance to add
     * @throws Exception errors
     */
    public void addInstance(Object instance) throws Exception
    {
        State currentState = state.get();
        if ( (currentState == State.STOPPING) || (currentState == State.STOPPED) )
        {
            throw new IllegalStateException();
        }
        else if ( (currentState == State.STARTED) || (currentState == State.STARTING) || (mode == Mode.START_INSTANCES_IMMEDIATELY) )
        {
            startInstance(instance);
            if ( methodsMap.get(instance.getClass()).hasFor(PreDestroy.class) )
            {
                managedInstances.add(instance);
            }
        }
        else
        {
            managedInstances.add(instance);
        }
    }

    private void startInstance(Object obj) throws IllegalAccessException, InvocationTargetException
    {
        log.debug("Starting %s", obj.getClass().getName());
        LifeCycleMethods methods = methodsMap.get(obj.getClass());
        for ( Method postConstruct : methods.methodsFor(PostConstruct.class) )
        {
            log.debug("\t%s()", postConstruct.getName());
            postConstruct.invoke(obj);
        }
    }
}
