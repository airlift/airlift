package io.airlift.mcp.reflection;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.mcp.model.Icon;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class IconHelper
{
    private final Map<String, Icon> iconBindings;

    @Inject
    public IconHelper(Map<String, Icon> iconBindings)
    {
        this.iconBindings = ImmutableMap.copyOf(iconBindings);
    }

    public Optional<List<Icon>> mapIcons(Collection<String> iconNames)
    {
        if (iconNames.isEmpty()) {
            return Optional.empty();
        }

        List<Icon> icons = iconNames.stream()
                .map(name -> {
                    Icon icon = iconBindings.get(name);
                    if (icon == null) {
                        throw new IllegalArgumentException("No icon bound with name: " + name);
                    }
                    return icon;
                })
                .collect(toImmutableList());

        return Optional.of(icons);
    }
}
