package com.proofpoint.guice;

import com.google.inject.ConfigurationException;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Utility for iterating over dependent classes in a Guice injector
 */
public class GuiceDependencyIterator implements Iterator<Class<?>>, Iterable<Class<?>>
{
    private final Set<Key<?>>                   visited;
    private final Iterator<Dependency<?>>       currentDependencyIterator;
    private final TypeLiteral<?>                creationTypeLiteral;
    private final Class<?>                      creationClass;

    private Class<?>                            currentClass = null;

    /**
     * @param typeLiteral the type literal to iterate over
     */
    public GuiceDependencyIterator(TypeLiteral<?> typeLiteral)
    {
        this(null, typeLiteral, new HashSet<Key<?>>());
    }

    /**
     * @param clazz the class to iterate over
     */
    public GuiceDependencyIterator(Class<?> clazz)
    {
        this(clazz, null, new HashSet<Key<?>>());
    }

    /**
     * Returns whether or not there was an injection point for the type/class
     *
     * @return true/false
     */
    public boolean hasInjectionPoint()
    {
        return (currentDependencyIterator != null);
    }

    /**
     * Use an external set of visited classes - this is used to avoid recursive iteration
     *
     * @param visited the visited set. A copy is _not_ made. The original instance will be mutated
     * @return new iterator that uses the given visited set instance
     */
    public GuiceDependencyIterator  substituteVisitedSet(Set<Key<?>> visited)
    {
        return new GuiceDependencyIterator(creationClass, creationTypeLiteral, visited);
    }

    @Override
    public Iterator<Class<?>> iterator()
    {
        return new GuiceDependencyIterator(creationClass, creationTypeLiteral, visited);
    }

    @Override
    public boolean hasNext()
    {
        if ( currentDependencyIterator != null )
        {
            while ( (currentClass == null) && currentDependencyIterator.hasNext() )
            {
                currentClass = GuiceInjectorIterator.parseKey(visited, currentDependencyIterator.next().getKey());
            }
        }

        return (currentClass != null);
    }

    @Override
    public Class<?> next()
    {
        Class<?> localClass = currentClass;
        currentClass = null;
        return localClass;
    }

    @Override
    public void remove()
    {
        throw new UnsupportedOperationException();
    }

    private GuiceDependencyIterator(Class<?> clazz, TypeLiteral<?> typeLiteral, Set<Key<?>> visited)
    {
        this.creationClass = clazz;
        this.creationTypeLiteral = typeLiteral;
        this.visited = visited;

        // must be called last
        currentDependencyIterator = initInjectionPoint();
    }

    private Iterator<Dependency<?>> initInjectionPoint()
    {
        try
        {
            InjectionPoint injectionPoint = (creationTypeLiteral != null) ? InjectionPoint.forConstructorOf(creationTypeLiteral) : InjectionPoint.forConstructorOf(creationClass);
            return injectionPoint.getDependencies().iterator();
        }
        catch ( ConfigurationException dummy )
        {
            // ignore
        }

        return null;
    }
}
