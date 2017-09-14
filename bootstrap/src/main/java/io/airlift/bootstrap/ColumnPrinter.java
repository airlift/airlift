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
package io.airlift.bootstrap;

import com.google.common.collect.ImmutableList;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.repeat;
import static java.lang.Math.max;
import static java.util.stream.Collectors.toCollection;

/**
 * A utility for outputting columnar text
 */
class ColumnPrinter
{
    private static final int DEFAULT_MARGIN = 2;

    private final Collection<List<String>> data = new LinkedHashSet<>();
    private final List<String> columnNames;
    private final List<Integer> columnWidths;
    private final int margin;

    public ColumnPrinter(String... columnNames)
    {
        this(DEFAULT_MARGIN, columnNames);
    }

    public ColumnPrinter(int margin, String... columnNames)
    {
        this.margin = margin;
        this.columnNames = ImmutableList.copyOf(columnNames);
        this.columnWidths = Arrays.stream(columnNames)
                .map(String::length)
                .collect(toCollection(ArrayList::new));
    }

    public void addValues(String... values)
    {
        checkArgument(values.length == columnNames.size(), "wrong value count");
        for (int i = 0; i < values.length; i++) {
            columnWidths.set(i, max(values[i].length(), columnWidths.get(i)));
        }
        data.add(ImmutableList.copyOf(values));
    }

    public void print(PrintWriter out)
    {
        for (String line : generateOutput()) {
            out.println(line.trim());
        }
    }

    private List<String> generateOutput()
    {
        List<String> lines = new ArrayList<>();
        lines.add(printRow(columnNames));
        for (List<String> row : data) {
            lines.add(printRow(row));
        }
        return lines;
    }

    private String printRow(List<String> values)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            sb.append(value(values.get(i), columnWidths.get(i) + margin));
        }
        return sb.toString();
    }

    private static String value(String value, int width)
    {
        return value + repeat(" ", width - value.length());
    }
}
