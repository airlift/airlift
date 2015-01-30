/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.jaxrs;

import com.google.common.base.Supplier;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;

import static com.google.inject.Scopes.SINGLETON;

public class LinkedInjectionProviderBindingBuilder<T>
{
    private final Class<T> type;
    private final MapBinder<Class<?>, Supplier<?>> injectionProviderBinder;

    LinkedInjectionProviderBindingBuilder(Class<T> type, MapBinder<Class<?>, Supplier<?>> injectionProviderBinder)
    {
        this.type = type;
        this.injectionProviderBinder = injectionProviderBinder;
    }
    
    public void to(Class<? extends Supplier<? extends T>> implementation)
    {
        injectionProviderBinder.addBinding(type).to(implementation).in(SINGLETON);
    }

    public void to(TypeLiteral<? extends Supplier<? extends T>> implementation)
    {
        injectionProviderBinder.addBinding(type).to(implementation).in(SINGLETON);
    }

    public void to(Key<? extends Supplier<? extends T>> targetKey)
    {
        injectionProviderBinder.addBinding(type).to(targetKey).in(SINGLETON);
    }

    public void toInstance(Supplier<? extends T> supplier) {
        injectionProviderBinder.addBinding(type).toInstance(supplier);
    }

    public void toProvider(Provider<? extends Supplier<? extends T>> provider)
    {
        injectionProviderBinder.addBinding(type).toProvider(provider).in(SINGLETON);
    }

    public void toProvider(
            Class<? extends javax.inject.Provider<? extends Supplier<? extends T>>> providerType)
    {
        injectionProviderBinder.addBinding(type).toProvider(providerType).in(SINGLETON);
    }

    public void toProvider(
            TypeLiteral<? extends javax.inject.Provider<? extends Supplier<? extends T>>> providerType)
    {
        injectionProviderBinder.addBinding(type).toProvider(providerType).in(SINGLETON);
    }

    public void toProvider(
            Key<? extends javax.inject.Provider<? extends Supplier<? extends T>>> providerKey)
    {
        injectionProviderBinder.addBinding(type).toProvider(providerKey).in(SINGLETON);
    }
}
