package com.proofpoint.configuration.inspector;

import com.google.common.collect.Lists;
import org.apache.commons.lang.mutable.MutableInt;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A utility for outputting columnar text
 */
class ColumnPrinter
{
    private final List<List<String>>    data = Lists.newArrayList();
    private final List<String>          columnNames = Lists.newArrayList();
    private int                         margin;

    private static final int        DEFAULT_MARGIN = 2;

    public ColumnPrinter()
    {
        margin = DEFAULT_MARGIN;
    }

    /**
     * Add a column
     *
     * @param columnName name of the column
     */
    public void     addColumn(String columnName)
    {
        data.add(new ArrayList<String>());
        columnNames.add(columnName);
    }

    /**
     * Add a value to the first column with the given name
     *
     * @param columnName name of the column to add to
     * @param value value to add
     */
    public void     addValue(String columnName, String value)
    {
        addValue(columnNames.indexOf(columnName), value);
    }

    /**
     * Add a value to the nth column
     *
     * @param columnIndex n
     * @param value value to add
     */
    public void     addValue(int columnIndex, String value)
    {
        if ( (columnIndex < 0) || (columnIndex >= data.size()) )
        {
            throw new IllegalArgumentException();
        }

        List<String>    stringList = data.get(columnIndex);
        stringList.add(value);
    }

    /**
     * Change the margin from the default
     *
     * @param margin new margin between columns
     */
    public void     setMargin(int margin)
    {
        this.margin = margin;
    }

    /**
     * Output the columns/data
     *
     * @param out stream
     */
    public void     print(PrintWriter out)
    {
        for ( String s : generate() )
        {
            out.println(s);
        }
    }

    /**
     * Generate the output as a list of string lines
     *
     * @return lines
     */
    public List<String> generate()
    {
        List<String>            lines = Lists.newArrayList();
        StringBuilder           workStr = new StringBuilder();

        List<MutableInt>        columnWidths = getColumnWidths();
        List<Iterator<String>>  dataIterators = getDataIterators();

        Iterator<MutableInt>    columnWidthIterator = columnWidths.iterator();
        for ( String columnName : columnNames )
        {
            int     thisWidth = columnWidthIterator.next().intValue();
            printValue(workStr, columnName, thisWidth);
        }
        pushLine(lines, workStr);

        boolean         done = false;
        while ( !done )
        {
            boolean             hadValue = false;
            Iterator<Iterator<String>>  rowIterator = dataIterators.iterator();
            for ( MutableInt width : columnWidths )
            {
                Iterator<String>    thisDataIterator = rowIterator.next();
                if ( thisDataIterator.hasNext() )
                {
                    hadValue = true;

                    String      value = thisDataIterator.next();
                    printValue(workStr, value, width.intValue());
                }
                else
                {
                    printValue(workStr, "", width.intValue());
                }
            }
            pushLine(lines, workStr);

            if ( !hadValue )
            {
                done = true;
            }
        }

        return lines;
    }

    private void pushLine(List<String> lines, StringBuilder workStr)
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

    private List<Iterator<String>> getDataIterators()
    {
        List<Iterator<String>>      dataIterators = Lists.newArrayList();
        for ( List<String> valueList : data )
        {
            dataIterators.add(valueList.iterator());
        }
        return dataIterators;
    }

    private List<MutableInt> getColumnWidths()
    {
        List<MutableInt> columnWidths = Lists.newArrayList();
        for ( String columnName : columnNames )
        {
            columnWidths.add(new MutableInt(columnName.length()));
        }

        int                       columnIndex = 0;
        for ( List<String> valueList : data )
        {
            MutableInt       width = columnWidths.get(columnIndex++);
            for ( String value : valueList )
            {
                width.setValue(Math.max(value.length(), width.intValue()));
            }
        }

        return columnWidths;
    }
}
