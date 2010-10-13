package com.proofpoint.lifecycle;

import com.google.common.collect.Lists;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.PrivateElements;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

import static com.google.inject.matcher.Matchers.any;

/**
 * Guice module for binding the LifeCycle manager
 */
public class LifeCycleModule implements Module
{
    private final List<Key<?>>      injectedKeys = Lists.newArrayList();
    private final List<Object>      injectedInstances = Lists.newArrayList();

    /**
     * @param elements Set of elements from Guice
     */
    public LifeCycleModule(Iterable<Element> elements)
    {
        for (final Element element : elements )
        {
            element.acceptVisitor
            (
                new DefaultElementVisitor<Void>()
                {
                    @Override
                    public Void visit(PrivateElements privateElements)
                    {
                        for ( Element element : privateElements.getElements() )
                        {
                            if ( element instanceof LinkedKeyBinding )
                            {
                                Key<?>      key = ((LinkedKeyBinding)element).getLinkedKey();
                                if ( privateElements.getExposedKeys().contains(key) )
                                {
                                    checkKey(key);
                                }
                            }

                            if ( element instanceof Binding )
                            {
                                Key<?>      key = ((Binding)element).getKey();
                                if ( privateElements.getExposedKeys().contains(key) )
                                {
                                    checkKey(key);
                                }
                            }
                        }
                        return null;
                    }

                    @Override
                    public <T> Void visit(Binding<T> binding)
                    {
                        Key<T>      key;
                        if ( binding instanceof LinkedKeyBinding )
                        {
                            //noinspection unchecked
                            key = ((LinkedKeyBinding)binding).getLinkedKey();
                        }
                        else
                        {
                            key = binding.getKey();
                        }
                        checkKey(key);
                        return null;
                    }
                }
            );
        }
    }

    @Override
    public void configure(Binder binder)
    {
        binder.bindListener(any(), new TypeListener()
        {
            @Override
            public <T> void hear(TypeLiteral<T> type, TypeEncounter<T> encounter)
            {
                encounter.register(new InjectionListener<T>()
                {
                    @Override
                    public void afterInjection(T obj)
                    {
                        if ( isLifeCycleClass(obj.getClass()) )
                        {
                            injectedInstances.add(obj);
                        }
                    }
                });
            }
        });
    }

    @Provides
    @Singleton
    public LifeCycleManager     getServerManager(Injector injector)
    {
        for ( Key<?> key : injectedKeys )
        {
            // causes the bindListener to get called adding instances to injectedInstances
            injector.getInstance(key);
        }

        return new LifeCycleManager(injectedInstances);
    }

    private <T> void checkKey(Key<T> key)
    {
        if ( isLifeCycleClass(key.getTypeLiteral().getRawType()) )
        {
            injectedKeys.add(key);
        }
    }

    private static boolean isLifeCycleClass(Class<?> clazz)
    {
        LifeCycleMethods        methods = new LifeCycleMethods(clazz);
        return (methods.methodFor(PostConstruct.class) != null) || (methods.methodFor(PreDestroy.class) != null);
    }
}
