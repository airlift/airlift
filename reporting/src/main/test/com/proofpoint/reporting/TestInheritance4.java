/*
 *  Copyright 2009 Martin Traverso
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.proofpoint.reporting;

// Implemented method, inherit annotation from interface => A
public class TestInheritance4
    extends TestInheritanceBase
{
    public TestInheritance4()
    {
        super(B.class, A.class);
    }

    private static interface A
    {
        @Reported
        Object getValue();
    }

    private static class B
        implements A
    {
        public Object getValue() { return null; }
    }


}