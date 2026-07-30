/*
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

import com.google.inject.Injector;

/**
 * Observes application bootstrap without changing application assembly. Listeners are registered
 * for all {@link Bootstrap} instances in the class loader, and each initialization attempt uses
 * the listeners registered when it begins.
 * <p>
 * A listener observes whether bootstrap completed; it does not own application resources or
 * participate in shutdown. Application components should use lifecycle annotations and
 * {@link LifeCycleManager} for startup and cleanup.
 */
public interface BootstrapListener
{
    default void bootstrapInitialized(Bootstrap bootstrap, Injector injector) {}

    default void bootstrapFailed(Bootstrap bootstrap, Throwable failure) {}
}
