package com.mongodb.baas.sdk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class PipelineStage {
    @JsonProperty("action")
    private final String _action;

    @JsonProperty("service")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String _service;

    @JsonProperty("args")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Map<String, Object> _args;

    public PipelineStage(
            final String action,
            final String service,
            final Map<String, Object> args
    ) {
        _action = action;
        _service = service;
        _args = args;
    }

    public PipelineStage(final String action) {
        this(action, null, null);
    }

    public PipelineStage(final String action, final Map<String, Object> args) {
        this(action, null, args);
    }

    public PipelineStage(final String action, final String service) {
        this(action, service, null);
    }
}
