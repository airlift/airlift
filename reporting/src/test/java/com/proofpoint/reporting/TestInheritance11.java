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

import java.util.ArrayList;
import java.util.List;

// Annotation inherited from parent, covariant generic return type in child => A
public class TestInheritance11
        extends TestInheritanceBase
{
    public TestInheritance11()
    {
        super(B.class, A.class);
    }

    private static class A
    {
        @Reported
        public List<Object> getValue()
        {
            return null;
        }
    }

    private static class B
        extends A
    {
        @Override
        public ArrayList<Object> getValue()
        {
            return null;
        }
    }

}