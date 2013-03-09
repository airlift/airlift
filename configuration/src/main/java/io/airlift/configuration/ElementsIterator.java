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
package io.airlift.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;

import java.util.Iterator;
import java.util.List;

class ElementsIterator
        implements Module, Iterable<Element>
{
    private final List<Element> boundElements;
    private final List<Element> elements;

    public ElementsIterator(Module... modules)
    {
        this(ImmutableList.copyOf(modules));
    }

    public ElementsIterator(Iterable<? extends Module> modules)
    {
        elements = Elements.getElements(modules);
        boundElements = Lists.newArrayList(elements);
    }

    @Override
    public void configure(Binder binder)
    {
        Module baseModule = Elements.getModule(boundElements);
        binder.install(baseModule);
    }

    /**
     * Normally, all bindings from the modules are used. This method removes
     * an element
     *
     * @param element the element to unbind
     */
    public void unbindElement(Element element)
    {
        if (element == null) {
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
