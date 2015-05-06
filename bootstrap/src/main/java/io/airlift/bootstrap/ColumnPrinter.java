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

import com.google.common.collect.Lists;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkElementIndex;
import static java.lang.Math.max;

/**
 * A utility for outputting columnar text
 */
class ColumnPrinter
{
    private static final int DEFAULT_MARGIN = 2;

    private final List<List<String>> data = Lists.newArrayList();
    private final List<String> columnNames = Lists.newArrayList();
    private final List<Integer> columnWidths = Lists.newArrayList();
    private final int margin;

    public ColumnPrinter()
    {
        this(DEFAULT_MARGIN);
    }

    public ColumnPrinter(int margin)
    {
        this.margin = margin;
    }

    /**
     * Add a column
     *
     * @param columnName name of the column
     */
    public void addColumn(String columnName)
    {
        data.add(new ArrayList<>());
        columnNames.add(columnName);
        columnWidths.add(columnName.length());
    }

    /**
     * Add a value to the first column with the given name
     *
     * @param columnName name of the column to add to
     * @param value value to add
     */
    public void addValue(String columnName, String value)
    {
        addValue(columnNames.indexOf(columnName), value);
    }

    private void addValue(int columnIndex, String value)
    {
        checkElementIndex(columnIndex, data.size(), "columnIndex");

        data.get(columnIndex).add(value);
        columnWidths.set(columnIndex, max(value.length(), columnWidths.get(columnIndex)));
    }

    public void print(PrintWriter out)
    {
        for (String line : generateOutput()) {
            out.println(line.trim());
        }
    }

    private List<String> generateOutput()
    {
        List<String> lines = Lists.newArrayList();
        StringBuilder buffer = new StringBuilder();

        List<Iterator<String>> dataIterators = data.stream()
                .map(Collection::iterator)
                .collect(Collectors.toList());

        Iterator<Integer> columnWidthIterator = columnWidths.iterator();
        for (String columnName : columnNames) {
            int thisWidth = columnWidthIterator.next();
            printValue(buffer, columnName, thisWidth);
        }
        pushLine(lines, buffer);

        boolean done = false;
        while (!done) {
            boolean hadValue = false;
            Iterator<Iterator<String>> rowIterator = dataIterators.iterator();
            for (int width : columnWidths) {
                Iterator<String> thisDataIterator = rowIterator.next();
                if (thisDataIterator.hasNext()) {
                    hadValue = true;

                    String value = thisDataIterator.next();
                    printValue(buffer, value, width);
                }
                else {
                    printValue(buffer, "", width);
                }
            }
            pushLine(lines, buffer);

            if (!hadValue) {
                done = true;
            }
        }

        return lines;
    }

    private static void pushLine(List<String> lines, StringBuilder workStr)
    {
        lines.add(workStr.toString());
        workStr.setLength(0);
    }

    private void printValue(StringBuilder str, String value, int thisWidth)
    {
        str.append(String.format(widthSpec(thisWidth), value));
    }

    private String widthSpec(int thisWidth)
    {
        return "%-" + (thisWidth + margin) + "s";
    }
}
