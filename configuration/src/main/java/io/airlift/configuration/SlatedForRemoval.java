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
package io.airlift.configuration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Annotated configuration setting is slated for removal.
 * The annotated property name is suffixed with {@value #PROPERTY_SUFFIX}
 * and removal date in {@link #PROPERTY_DATE_FORMAT}.
 * Must be on same method as {@code @Config}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SlatedForRemoval
{
    DateTimeFormatter VALUE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US);
    String PROPERTY_SUFFIX = ".slated-for-removal-";
    DateTimeFormatter PROPERTY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US);

    /**
     * Describes date after which config can be removed.
     * <p>
     * Must be in the format accepted by {@link #VALUE_DATE_FORMAT}, for example {@code "2042-10"}.
     */
    String after();
}
