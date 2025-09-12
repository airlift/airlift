package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class Schema<T>
{
    private String format;
    private String name;
    private List<String> required;
    private String type;
    private Map<String, Schema> properties;
    private String description;
    private Boolean nullable;
    private Boolean readOnly;
    private Set<String> types;
    private Schema<?> items;
    private Object additionalProperties;
    private String $ref;
    private List<T> _enum;
    private List<Schema> allOf;
    private Discriminator discriminator;
    private List<String> tags;

    public Schema()
    {
    }

    protected Schema(String type, String format)
    {
        this.type = type;
        this.addType(type);
        this.format = format;
    }

    @JsonProperty("x-tags")
    public List<String> getTags()
    {
        return tags;
    }

    public void setTags(List<String> tags)
    {
        this.tags = tags;
    }

    public Schema<?> tags(List<String> tags)
    {
        this.tags = tags;
        return this;
    }

    public Schema<?> name(String name)
    {
        this.setName(name);
        return this;
    }

    @JsonIgnore
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * returns the discriminator property from a AllOfSchema instance.
     *
     * @return Discriminator discriminator
     **/

    @JsonProperty
    public Discriminator getDiscriminator()
    {
        return discriminator;
    }

    public void setDiscriminator(Discriminator discriminator)
    {
        this.discriminator = discriminator;
    }

    public Schema discriminator(Discriminator discriminator)
    {
        this.discriminator = discriminator;
        return this;
    }

    public boolean addType(String type)
    {
        if (types == null) {
            types = new HashSet<>();
        }
        return types.add(type);
    }

    @JsonProperty
    public List<String> getRequired()
    {
        return required;
    }

    public void setRequired(List<String> required)
    {
        List<String> list = new ArrayList<>();
        if (required != null) {
            for (String req : required) {
                if (this.properties == null || this.properties.containsKey(req)) {
                    list.add(req);
                }
            }
        }
        Collections.sort(list);
        if (list.isEmpty()) {
            list = null;
        }
        this.required = list;
    }

    public Schema required(List<String> required)
    {
        this.required = required;
        return this;
    }

    public Schema addRequiredItem(String requiredItem)
    {
        if (this.required == null) {
            this.required = new ArrayList<>();
        }
        this.required.add(requiredItem);
        Collections.sort(required);
        return this;
    }

    @JsonProperty
    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public Schema type(String type)
    {
        this.type = type;
        return this;
    }

    @JsonProperty
    public Map<String, Schema> getProperties()
    {
        return properties;
    }

    public void setProperties(Map<String, Schema> properties)
    {
        this.properties = properties;
    }

    public Schema properties(Map<String, Schema> properties)
    {
        this.properties = properties;
        return this;
    }

    public Schema addProperty(String key, Schema property)
    {
        if (this.properties == null) {
            this.properties = new LinkedHashMap<>();
        }
        this.properties.put(key, property);
        return this;
    }

    @JsonProperty
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public Schema description(String description)
    {
        this.description = description;
        return this;
    }

    @JsonProperty
    public String getFormat()
    {
        return format;
    }

    public void setFormat(String format)
    {
        this.format = format;
    }

    public Schema format(String format)
    {
        this.format = format;
        return this;
    }

    @JsonProperty
    public Boolean getReadOnly()
    {
        return readOnly;
    }

    public void setReadOnly(Boolean readOnly)
    {
        this.readOnly = readOnly;
    }

    public Schema readOnly(Boolean readOnly)
    {
        this.readOnly = readOnly;
        return this;
    }

    /**
     * returns the allOf property from a ComposedSchema instance.
     *
     * @return List&lt;Schema&gt; allOf
     **/

    @JsonProperty
    public List<Schema> getAllOf()
    {
        return allOf;
    }

    public void setAllOf(List<Schema> allOf)
    {
        this.allOf = allOf;
    }

    public Schema allOf(List<Schema> allOf)
    {
        this.allOf = allOf;
        return this;
    }

    public Schema addAllOfItem(Schema allOfItem)
    {
        if (this.allOf == null) {
            this.allOf = new ArrayList<>();
        }
        this.allOf.add(allOfItem);
        return this;
    }

    @JsonProperty
    public Schema<?> getItems()
    {
        return items;
    }

    public void setItems(Schema<?> items)
    {
        this.items = items;
    }

    public Schema items(Schema<?> items)
    {
        this.items = items;
        return this;
    }

    @JsonProperty
    public Object getAdditionalProperties()
    {
        return additionalProperties;
    }

    public void setAdditionalProperties(Object additionalProperties)
    {
        if (additionalProperties != null && !(additionalProperties instanceof Boolean) && !(additionalProperties instanceof Schema)) {
            throw new IllegalArgumentException("additionalProperties must be either a Boolean or a Schema instance");
        }
        this.additionalProperties = additionalProperties;
    }

    public Schema additionalProperties(Object additionalProperties)
    {
        setAdditionalProperties(additionalProperties);
        return this;
    }

    @JsonProperty
    public String get$ref()
    {
        return $ref;
    }

    public void set$ref(String $ref)
    {
        if ($ref != null && !$ref.startsWith("#") && ($ref.indexOf('.') == -1 && $ref.indexOf('/') == -1)) {
            $ref = "#/components/schemas/" + $ref;
        }
        this.$ref = $ref;
    }

    public Schema $ref(String $ref)
    {
        set$ref($ref);
        return this;
    }

    @JsonProperty
    public Boolean getNullable()
    {
        return nullable;
    }

    public void setNullable(Boolean nullable)
    {
        this.nullable = nullable;
    }

    public Schema nullable(Boolean nullable)
    {
        this.nullable = nullable;
        return this;
    }

    @JsonProperty
    public List<T> getEnum()
    {
        return _enum;
    }

    public void setEnum(List<T> _enum)
    {
        this._enum = _enum;
    }

    public void addEnumItemObject(T _enumItem)
    {
        if (this._enum == null) {
            this._enum = new ArrayList<>();
        }
        this._enum.add(_enumItem);
    }
}
