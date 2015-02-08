package io.airlift.configuration;

import com.google.inject.Provider;

/**
 * A provider with access to the airlift {@link ConfigurationFactory}.
 * <p>
 * Implementing this interface ensures that the provider gets access to the
 * {@link ConfigurationFactory} and {@link WarningsMonitor} before the first
 * call to {@link Provider#get()}.
 *
 * @param <T> Element type that is returned by this provider.
 */
public interface ConfigurationAwareProvider<T>
        extends Provider<T>
{
    /**
     * Called by the airlift framework before the first call to get.
     *
     * @param configurationFactory The airlift configuration factory.
     */
    void setConfigurationFactory(ConfigurationFactory configurationFactory);

    /**
     * May be called by the airlift framework before the first call to get.
     * This is an optional call.
     *
     * @param warningsMonitor An airlift {@link WarningsMonitor}.
     */
    void setWarningsMonitor(WarningsMonitor warningsMonitor);
}
