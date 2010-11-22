package com.proofpoint.dbpool;

import org.apache.commons.lang.StringUtils;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public final class Duration implements Comparable<Duration>
{
    public static Duration nanosSince(long nanos)
    {
        long l = System.nanoTime();
        return new Duration(l - nanos,  TimeUnit.NANOSECONDS);
    }

    private final double millis;

    public Duration(double value, TimeUnit timeUnit)
    {
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("value is infinite");
        }
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("value is not a number");
        }
        if (value < 0) {
            throw new IllegalArgumentException("value is negative");
        }
        if (timeUnit == null) {
            throw new NullPointerException("timeUnit is null");
        }

        double conversionFactor = getConversionFactor(timeUnit);
        millis = value * conversionFactor;
    }

    public double toMillis()
    {
        return millis;
    }

    public double convertTo(TimeUnit timeUnit)
    {
        if (timeUnit == null) {
            throw new NullPointerException("timeUnit is null");
        }
        return convertTo(millis, timeUnit);
    }

    private static double convertTo(double value, TimeUnit timeUnit)
    {
        double conversionFactor = getConversionFactor(timeUnit);
        return value / conversionFactor;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Duration duration = (Duration) o;

        if (Double.compare(duration.millis, millis) != 0) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        long temp = Double.doubleToLongBits(millis);
        return (int) (temp ^ (temp >>> 32));
    }

    @Override
    public int compareTo(Duration o)
    {
        return Double.compare(millis, o.millis);
    }

    @Override
    public String toString()
    {
        return toString(TimeUnit.MILLISECONDS);
    }

    public String toString(TimeUnit timeUnit)
    {
        if (timeUnit == null) {
            throw new NullPointerException("timeUnit is null");
        }

        double magnitude = convertTo(millis, timeUnit);
        String timeUnitAbbreviation;
        switch (timeUnit) {
            case MILLISECONDS:
                timeUnitAbbreviation = "ms";
                break;
            case SECONDS:
                timeUnitAbbreviation = "s";
                break;
            case MINUTES:
                timeUnitAbbreviation = "m";
                break;
            case HOURS:
                timeUnitAbbreviation = "h";
                break;
            case DAYS:
                timeUnitAbbreviation = "d";
                break;
            default:
                throw new IllegalArgumentException("Unsupported time unit " + timeUnit);
        }
        return String.format("%.2f %s", magnitude, timeUnitAbbreviation);
    }

    private static double getConversionFactor(TimeUnit timeUnit)
    {
        double conversionFactor;
        switch (timeUnit) {
            case NANOSECONDS:
                conversionFactor = 1.0/ 1000000.0;
                break;
            case MICROSECONDS:
                conversionFactor = 1.0/ 1000.0;
                break;
            case MILLISECONDS:
                conversionFactor = 1;
                break;
            case SECONDS:
                conversionFactor = 1000;
                break;
            case MINUTES:
                conversionFactor = 1000 * 60;
                break;
            case HOURS:
                conversionFactor = 1000 * 60 * 60;
                break;
            case DAYS:
                conversionFactor = 1000 * 60 * 60 * 24;
                break;
            default:
                throw new IllegalArgumentException("Unsupported time unit " + timeUnit);
        }
        return conversionFactor;
    }


    private static final Pattern DURATION_PATTERN = Pattern.compile("^\\s*(\\d+\\.\\d+)\\s*(s|m|h|d|ms)\\s*$");
    public static Duration valueOf(String duration)
            throws IllegalArgumentException
    {
        if (duration == null) {
            throw new NullPointerException("duration is null");
        }

        if (duration.isEmpty()) {
            throw new IllegalArgumentException("duration is empty");
        }

        // Parse the duration string
        Matcher matcher = DURATION_PATTERN.matcher(duration);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("duration is not a valid duration string: " + duration);
        }

        // Determine the magnitude of the duration
        String magnitudeString = matcher.group(1);
        double magnitude = Double.parseDouble(magnitudeString);

        // Determine TimeEnit of the duration
        String timeUnitString = matcher.group(2);
        TimeUnit timeUnit;
        if (timeUnitString.equals("ms")) {
            timeUnit = TimeUnit.MILLISECONDS;
        }
        else if (timeUnitString.equals("s")) {
            timeUnit = TimeUnit.SECONDS;
        }
        else if (timeUnitString.equals("m")) {
            timeUnit = TimeUnit.MINUTES;
        }
        else if (timeUnitString.equals("h")) {
            timeUnit = TimeUnit.HOURS;
        }
        else if (timeUnitString.equals("d")) {
            timeUnit = TimeUnit.DAYS;
        }
        else {
            throw new IllegalArgumentException("Unknown time unit: " + timeUnitString);
        }

        return new Duration(magnitude, timeUnit);
    }
}
