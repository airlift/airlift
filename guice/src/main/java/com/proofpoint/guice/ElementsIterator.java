package com.proofpoint.guice;

import com.google.common.collect.Lists;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;

import java.util.Iterator;
import java.util.List;

/**
 * Utility for getting Guice Elements set. Instances of this _must_ be
 * added to your Guice injector creation.
 */
public class ElementsIterator implements Module, Iterable<Element>
{
    private final List<Element>         boundElements;
    private final List<Element>         elements;

    /**
     * Modules to get elements for
     *
     * @param modules the modules
     */
    public ElementsIterator(Module... modules)
    {
        elements = Elements.getElements(modules);
        boundElements = Lists.newArrayList(elements);
    }

    @Override
    public void configure(Binder binder)
    {
        Module      baseModule = Elements.getModule(boundElements);
        binder.install(baseModule);
    }

    /**
     * Normally, all bindings from the modules are used. This method removes
     * an element
     *
     * @param element the element to unbind
     */
    public void     unbindElement(Element element)
    {
        if ( element == null )
        {
            throw new IllegalStateException();
        }
        boundElements.remove(element);
    }

    @Override
    public Iterator<Element> iterator()
    {
        return elements.iterator();
    }
}
