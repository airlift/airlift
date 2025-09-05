package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class PathItem
{
    private List<Parameter> parameters;
    private Operation get;
    private Operation put;
    private Operation post;
    private Operation delete;
    private Operation patch;

    @JsonProperty
    public List<Parameter> getParameters()
    {
        return parameters;
    }

    public void setParameters(List<Parameter> parameters)
    {
        this.parameters = parameters;
    }

    public PathItem parameters(List<Parameter> parameters)
    {
        this.parameters = parameters;
        return this;
    }

    public PathItem addParametersItem(Parameter parametersItem)
    {
        if (this.parameters == null) {
            this.parameters = new ArrayList<>();
        }
        this.parameters.add(parametersItem);
        return this;
    }

    public PathItem get(Operation get)
    {
        this.get = get;
        return this;
    }

    public PathItem put(Operation put)
    {
        this.put = put;
        return this;
    }

    public PathItem post(Operation post)
    {
        this.post = post;
        return this;
    }

    public PathItem delete(Operation delete)
    {
        this.delete = delete;
        return this;
    }

    public PathItem patch(Operation patch)
    {
        this.patch = patch;
        return this;
    }

    @JsonProperty
    public Operation getGet()
    {
        return get;
    }

    @JsonProperty
    public Operation getPut()
    {
        return put;
    }

    @JsonProperty
    public Operation getPost()
    {
        return post;
    }

    @JsonProperty
    public Operation getDelete()
    {
        return delete;
    }

    @JsonProperty
    public Operation getPatch()
    {
        return patch;
    }
}
