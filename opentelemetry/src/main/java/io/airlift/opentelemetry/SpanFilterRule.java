package io.airlift.opentelemetry;

import com.google.common.base.Splitter;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.opentelemetry.SpanFilterRule.ChildrenMode.DROP;
import static io.airlift.opentelemetry.SpanFilterRule.ChildrenMode.REPARENT;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

public record SpanFilterRule(Optional<String> scopeGlob, String nameGlob, ChildrenMode children)
{
    private static final String SCOPE_SEPARATOR = "=>";
    private static final String CHILDREN_OPTION = "children";
    private static final Splitter OPTION_SPLITTER = Splitter.on(';').trimResults();
    private static final Splitter GLOB_SPLITTER = Splitter.on('*');

    public enum ChildrenMode
    {
        REPARENT,
        DROP,
    }

    public SpanFilterRule
    {
        requireNonNull(scopeGlob, "scopeGlob is null");
        requireNonNull(nameGlob, "nameGlob is null");
        requireNonNull(children, "children is null");
    }

    public static List<SpanFilterRule> parseAll(List<String> entries)
    {
        return entries.stream()
                .map(SpanFilterRule::parse)
                .collect(toImmutableList());
    }

    public static SpanFilterRule parse(String entry)
    {
        List<String> parts = OPTION_SPLITTER.splitToList(entry);
        String target = parts.getFirst();

        Optional<String> scope = Optional.empty();
        String name = target;
        int separator = target.indexOf(SCOPE_SEPARATOR);
        if (separator >= 0) {
            scope = Optional.of(target.substring(0, separator).trim());
            name = target.substring(separator + SCOPE_SEPARATOR.length()).trim();
        }
        checkArgument(!name.isEmpty(), "Empty span name in span filter rule: %s", entry);
        checkArgument(scope.map(value -> !value.isEmpty()).orElse(true), "Empty scope in span filter rule: %s", entry);

        ChildrenMode children = null;
        for (String option : parts.subList(1, parts.size())) {
            int equals = option.indexOf('=');
            checkArgument(equals >= 0 && option.substring(0, equals).trim().equals(CHILDREN_OPTION), "Unknown option '%s' in span filter rule: %s", option, entry);
            checkArgument(children == null, "Duplicate option '%s' in span filter rule: %s", CHILDREN_OPTION, entry);
            children = parseChildren(option.substring(equals + 1).trim(), entry);
        }
        return new SpanFilterRule(scope, name, children == null ? REPARENT : children);
    }

    public boolean matchesScope(String scope)
    {
        return scopeGlob.map(glob -> globMatches(glob, scope)).orElse(true);
    }

    public boolean matchesName(String name)
    {
        return globMatches(nameGlob, name);
    }

    private static ChildrenMode parseChildren(String value, String entry)
    {
        return switch (value.toLowerCase(ENGLISH)) {
            case "reparent" -> REPARENT;
            case "drop" -> DROP;
            default -> throw new IllegalArgumentException("Invalid children mode '%s' in span filter rule: %s".formatted(value, entry));
        };
    }

    private static boolean globMatches(String glob, String value)
    {
        List<String> parts = GLOB_SPLITTER.splitToList(glob);
        if (parts.size() == 1) {
            return glob.equals(value);
        }
        String first = parts.getFirst();
        String last = parts.getLast();
        if (!value.startsWith(first)) {
            return false;
        }
        int position = first.length();
        for (String part : parts.subList(1, parts.size() - 1)) {
            int index = value.indexOf(part, position);
            if (index < 0) {
                return false;
            }
            position = index + part.length();
        }
        return value.length() - last.length() >= position && value.endsWith(last);
    }
}
