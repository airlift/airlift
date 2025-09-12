package io.airlift.api.builders;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class RecursionChecker
{
    private final Set<Type> activeValidatingResources = new HashSet<>();
    private final List<Boolean> allowedStack = new ArrayList<>();

    void addValidatingResources(Type resource)
    {
        activeValidatingResources.add(resource);
    }

    void removeValidatingResources(Type resource)
    {
        activeValidatingResources.remove(resource);
    }

    boolean isActiveValidatingResource(Type resource)
    {
        return activeValidatingResources.contains(resource);
    }

    boolean recursionAllowed()
    {
        return !allowedStack.isEmpty() && allowedStack.getLast();
    }

    void pushRecursionAllowed(boolean allowed)
    {
        allowedStack.add(allowed);
    }

    void popRecursionAllowed()
    {
        allowedStack.removeLast();
    }
}
