package com.proofpoint.configuration;

import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.spi.ProviderInstanceBinding;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;
import com.proofpoint.formatting.ColumnPrinter;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Utility to output all the Configuration properties used in an Injection instance.
 * The property names used, the default values for each and the runtime values for each
 * are displayed (sorted by property name).
 * <p/>
 * To use, call: <code>injector.getInstance(ConfigInspector.class).print()</code><br>
 * This outputs via a logger. You can optionally pass a stream to capture the output.
 */
public class ConfigurationInspector
{
    private static final String PROPERTY_NAME_COLUMN = "PROPERTY";
    private static final String DEFAULT_VALUE_COLUMN = "DEFAULT";
    private static final String CURRENT_VALUE_COLUMN = "RUNTIME";
    private static final String DESCRIPTION_COLUMN = "DESCRIPTION";

    private final Set<ConfigRecord> configs;

    private static class ConfigRecord implements Comparable<ConfigRecord>
    {
        final String propertyName;
        final String defaultValue;
        final String currentValue;
        final String description;
        // todo this class needs to be updated to include the concept of deprecated property names

        @Override
        public int compareTo(ConfigRecord rhs)
        {
            return propertyName.compareTo(rhs.propertyName);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ConfigRecord that = (ConfigRecord) o;

            //noinspection RedundantIfStatement
            if (!propertyName.equals(that.propertyName)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            return propertyName.hashCode();
        }

        private ConfigRecord(String propertyName, String defaultValue, String currentValue, String description)
        {
            if (propertyName == null) {
                throw new NullPointerException("propertyName is null");
            }
            if (defaultValue == null) {
                throw new NullPointerException("defaultValue is null");
            }
            if (currentValue == null) {
                throw new NullPointerException("currentValue is null");
            }
            if (description == null) {
                throw new NullPointerException("description is null");
            }
            this.propertyName = propertyName;
            this.defaultValue = defaultValue;
            this.currentValue = currentValue;
            this.description = description;
        }
    }

    @Inject
    public ConfigurationInspector(Injector injector)
            throws InvocationTargetException, IllegalAccessException
    {
        ImmutableSortedSet.Builder<ConfigRecord> builder = ImmutableSortedSet.naturalOrder();
        for (Entry<Key<?>, Binding<?>> entry : injector.getBindings().entrySet()) {
            Binding<?> binding = entry.getValue();
            if (binding instanceof ProviderInstanceBinding) {
                ProviderInstanceBinding<?> providerInstanceBinding = (ProviderInstanceBinding<?>) binding;
                Provider<?> provider = providerInstanceBinding.getProviderInstance();
                if (provider instanceof ConfigurationProvider) {
                    ConfigurationProvider<?> configurationProvider = (ConfigurationProvider<?>) provider;
                    addConfig(configurationProvider, builder);
                }
            }
        }

        configs = builder.build();
    }

    /**
     * Print the config details to the given stream
     *
     * @param out stream
     */
    public void print(PrintWriter out)
    {
        ColumnPrinter columnPrinter = makePrinter();
        columnPrinter.print(out);
        out.flush();
    }

    private ColumnPrinter makePrinter()
    {
        ColumnPrinter columnPrinter = new ColumnPrinter();

        columnPrinter.addColumn(PROPERTY_NAME_COLUMN);
        columnPrinter.addColumn(DEFAULT_VALUE_COLUMN);
        columnPrinter.addColumn(CURRENT_VALUE_COLUMN);
        columnPrinter.addColumn(DESCRIPTION_COLUMN);

        for (ConfigRecord record : configs) {
            columnPrinter.addValue(PROPERTY_NAME_COLUMN, record.propertyName);
            columnPrinter.addValue(DEFAULT_VALUE_COLUMN, record.defaultValue);
            columnPrinter.addValue(CURRENT_VALUE_COLUMN, record.currentValue);
            columnPrinter.addValue(DESCRIPTION_COLUMN, record.description);
        }
        return columnPrinter;
    }

    private void addConfig(ConfigurationProvider<?> configurationProvider, ImmutableSortedSet.Builder<ConfigRecord> builder)
            throws InvocationTargetException, IllegalAccessException
    {
        ConfigurationMetadata<?> metadata = configurationProvider.getConfigurationMetadata();

        Object instance = configurationProvider.get();
        Object defaults = newDefaultInstance(configurationProvider);

        String prefix = configurationProvider.getPrefix();
        prefix = prefix == null ? "" : (prefix + ".");

        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            String propertyName = prefix + attribute.getPropertyName();
            Method getter = attribute.getGetter();

            String defaultValue = getValue(getter, defaults, "-- none --");
            String currentValue = getValue(getter, instance, "-- n/a --");
            String description = attribute.getDescription();
            if (description == null) {
                description = "";
            }

            builder.add(new ConfigRecord(propertyName, defaultValue, currentValue, description));

        }
    }

    private Object newDefaultInstance(ConfigurationProvider<?> configurationProvider)
    {
        try {
            return configurationProvider.getConfigurationMetadata().getConstructor().newInstance();
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private String getValue(Method getter, Object instance, String defaultValue)
    {
        if (getter == null || instance == null) {
            return defaultValue;
        }

        try {
            Object value = getter.invoke(instance);
            if (value == null) {
                return "null";
            }
            return value.toString();
        }
        catch (Throwable e) {
            return "-- ERROR --";
        }
    }
}
