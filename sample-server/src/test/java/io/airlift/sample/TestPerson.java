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
package io.airlift.sample;

import org.testng.annotations.Test;

import static io.airlift.testing.EquivalenceTester.equivalenceTester;

public class TestPerson
{
    @Test
    public void testEquivalence()
    {
        equivalenceTester()
                .addEquivalentGroup(new Person("foo@example.com", "Mr Foo"), new Person("foo@example.com", "Mr Foo"))
                .addEquivalentGroup(new Person("bar@example.com", "Mr Bar"), new Person("bar@example.com", "Mr Bar"))
                .addEquivalentGroup(new Person("foo@example.com", "Mr Bar"), new Person("foo@example.com", "Mr Bar"))
                .addEquivalentGroup(new Person("bar@example.com", "Mr Foo"), new Person("bar@example.com", "Mr Foo"))
                .check();
    }
}
