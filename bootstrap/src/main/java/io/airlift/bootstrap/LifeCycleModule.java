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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.spi.ProvisionListener.ProvisionInvocation;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.inject.matcher.Matchers.any;

/**
 * Guice module for binding the LifeCycle manager
 */
public class LifeCycleModule
        implements Module
{
    private final List<Object> injectedInstances = new ArrayList<>();
    private final LifeCycleMethodsMap lifeCycleMethodsMap = new LifeCycleMethodsMap();
    private final AtomicReference<LifeCycleManager> lifeCycleManager = new AtomicReference<>(null);

    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bindListener(any(), this::provision);
    }

    private <T> void provision(ProvisionInvocation<T> provision)
    {
        Object obj = provision.provision();
        if ((obj == null) || !isLifeCycleClass(obj.getClass())) {
            return;
        }

        LifeCycleManager manager = lifeCycleManager.get();
        if (manager != null) {
            try {
                manager.addInstance(obj);
            }
            catch (Exception e) {
                throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
        }
        else {
            injectedInstances.add(obj);
        }
    }

    @Provides
    @Singleton
    public LifeCycleManager getServerManager()
            throws Exception
    {
        LifeCycleManager lifeCycleManager = new LifeCycleManager(injectedInstances, lifeCycleMethodsMap);
        this.lifeCycleManager.set(lifeCycleManager);
        return lifeCycleManager;
    }

    private boolean isLifeCycleClass(Class<?> clazz)
    {
        LifeCycleMethods methods = lifeCycleMethodsMap.get(clazz);
        return methods.hasFor(PostConstruct.class) || methods.hasFor(PreDestroy.class);
    }
}
