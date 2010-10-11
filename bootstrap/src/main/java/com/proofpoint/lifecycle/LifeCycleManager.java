package com.proofpoint.lifecycle;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.proofpoint.log.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages PostConstruct and PreDestroy life cycles
 */
public class LifeCycleManager
{
    private final Logger                    log = Logger.get(getClass());
    private final AtomicReference<State>    state = new AtomicReference<State>(State.LATENT);
    private final List<Object>              managedInstances;

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
     */
    @Inject
    public LifeCycleManager(List<Object> managedInstances)
    {
        this.managedInstances = Lists.newArrayList(managedInstances);
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
        log.debug("Life cycle starting...");

        for ( Object obj : managedInstances )
        {
            log.debug("Starting %s", obj.getClass().getName());
            LifeCycleMethods        methods = new LifeCycleMethods(obj.getClass());
            Method                  postConstruct = methods.methodFor(PostConstruct.class);
            if ( postConstruct != null )
            {
                log.debug("\t%s()", postConstruct.getName());
                postConstruct.invoke(obj);     // TODO - support optional arguments?
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
        log.debug("Life cycle startup complete. System ready.");
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
        
        log.debug("Life cycle stopping...");

        List<Object>        reversedInstances = Lists.newArrayList(managedInstances);
        Collections.reverse(reversedInstances);

        for ( Object obj : reversedInstances )
        {
            log.debug("Stopping %s", obj.getClass().getName());
            LifeCycleMethods        methods = new LifeCycleMethods(obj.getClass());
            Method                  preDestroy = methods.methodFor(PreDestroy.class);
            if ( preDestroy != null )
            {
                log.debug("\t%s()", preDestroy.getName());
                preDestroy.invoke(obj);     // TODO - support optional arguments?
            }
        }

        state.set(State.STOPPED);
        log.debug("Life cycle stopped.");
    }
}
