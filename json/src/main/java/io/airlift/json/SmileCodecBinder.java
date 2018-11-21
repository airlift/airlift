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
package io.airlift.json;

import com.google.common.annotations.Beta;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.internal.MoreTypes.ParameterizedTypeImpl;

import java.lang.reflect.Type;

@Beta
public class SmileCodecBinder
        extends CodecBinder
{
    public static SmileCodecBinder smileCodecBinder(Binder binder)
    {
        return new SmileCodecBinder(binder);
    }

    private SmileCodecBinder(Binder binder)
    {
        super(binder, (type) -> new SmileCodecProvider(type));
    }

    @SuppressWarnings("unchecked")
    @Override
    Key<Codec<?>> getCodecKey(Type type)
    {
        return (Key<Codec<?>>) Key.get(new ParameterizedTypeImpl(null, SmileCodec.class, type));
    }
}
