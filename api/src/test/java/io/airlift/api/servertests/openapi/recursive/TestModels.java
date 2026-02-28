package io.airlift.api.servertests.openapi.recursive;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.api.ApiResourceVersion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestModels
{
    public static final PolyTest TEST_POLY_TEST;
    public static final TransformLiveTableSchemaUpdate TEST_SCHEMA_UPDATE;
    public static final SimpleRecursive TEST_SIMPLE_RECURSIVE;

    private TestModels() {}

    static {
        Optional<Map<String, String>> testMap = Optional.of(ImmutableMap.of("a", "b", "c", "d"));

        JsonIngestColumn.JsonParserConfig fieldParserConfig = new JsonIngestColumn.JsonParserConfig("pointer", testMap);

        PolyTest.NewTypeProperties.NewJsonIngestColumn nestedTestColumnName = new PolyTest.NewTypeProperties.NewJsonIngestColumn("Nested test column name", fieldParserConfig, new JsonIngestColumn.Type(JsonIngestColumn.Type.TypeId.Double, testMap), Optional.of(ImmutableList.of()));

        PolyTest.NewTypeProperties newTypeProperties = new PolyTest.NewTypeProperties.NewJsonIngestColumn("Test column name", fieldParserConfig, new JsonIngestColumn.Type(JsonIngestColumn.Type.TypeId.Double, testMap), Optional.of(ImmutableList.of(nestedTestColumnName)));
        TEST_POLY_TEST = new PolyTest(Optional.of("Test description"), "Test schema name", "Test table name", Optional.of(1234L), newTypeProperties);

        List<UpdatedJsonIngestColumn.NewJsonIngestColumn> nestedColumns = new ArrayList<>();
        nestedColumns.add(new UpdatedJsonIngestColumn.NewJsonIngestColumn("Nested test column name", new JsonIngestColumn.JsonParserConfig("pointer", testMap), new JsonIngestColumn.Type(JsonIngestColumn.Type.TypeId.Double, testMap), ImmutableList.of()));

        List<UpdatedJsonIngestColumn> columns = new ArrayList<>();
        columns.add(new UpdatedJsonIngestColumn.NewJsonIngestColumn("Test column name", new JsonIngestColumn.JsonParserConfig("pointer", testMap), new JsonIngestColumn.Type(JsonIngestColumn.Type.TypeId.Double, testMap), nestedColumns));
        columns.add(new UpdatedJsonIngestColumn.ExistingJsonIngestColumn(101, "Existing test column name", new JsonIngestColumn.JsonParserConfig("pointer", testMap), new JsonIngestColumn.Type(JsonIngestColumn.Type.TypeId.Double, testMap), Optional.empty()));

        TransformLiveTableSchemaUpdate.NewSchema newSchema = new TransformLiveTableSchemaUpdate.NewSchema.NewJsonSchema(columns, Optional.of("something"));
        TEST_SCHEMA_UPDATE = new TransformLiveTableSchemaUpdate(new ApiResourceVersion(1), new TableId("hi"), newSchema);

        List<SimpleRecursive> nested = new ArrayList<>();
        nested.add(new SimpleRecursive.NameAndAge("test detail id", "test sync token", "test name", 42));
        nested.add(new SimpleRecursive.RecursiveDetail("test recursive id", "test recursive sync token", "test recursive name", ImmutableList.of()));
        nested.add(new SimpleRecursive.Schedule("test schedule id", "test schedule sync token", "test schedule name", Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-12-31T23:59:59Z")));
        TEST_SIMPLE_RECURSIVE = new SimpleRecursive.RecursiveDetail("test id", "test sync token", "test name", nested);
    }
}
