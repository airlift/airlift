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
package io.airlift.stats.cardinality;

public class Stats
{
    private int count;
    private double mean;
    private double accumulator;

    public void add(double value)
    {
        count++;

        double delta = value - mean;
        mean += (delta / count);
        accumulator += delta * (value - mean);
    }

    public int count()
    {
        return count;
    }

    public double mean()
    {
        return mean;
    }

    public double stdev()
    {
        return Math.sqrt(accumulator / count);
    }

    public static Stats[] array(int size)
    {
        Stats[] result = new Stats[size];
        for (int i = 0; i < size; i++) {
            result[i] = new Stats();
        }

        return result;
    }
}
