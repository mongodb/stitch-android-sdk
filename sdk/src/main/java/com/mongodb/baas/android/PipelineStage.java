package com.mongodb.baas.android;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * A PipelineStage represents a single stage within a pipeline that specifies
 * an action, service, and its arguments.
 */
public class PipelineStage {

    /**
     * The action that represents this stage.
     */
    @JsonProperty("action")
    private final String _action;

    /**
     * The service that can handle the {@link PipelineStage#_action}. A null
     * service means that the {@link PipelineStage#_action} is builtin.
     */
    @JsonProperty("service")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String _service;

    /**
     * The arguments to invoke the action with.
     */
    @JsonProperty("args")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Map<String, Object> _args;

    /**
     * The expression to evaluate for use within the arguments via expansion.
     */
    @JsonProperty("let")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Object _let;

    /**
     * Constructs a completely specified pipeline stage
     *
     * @param action  The action that represents this stage.
     * @param service The service that can handle the {@code action}. A null
     *                service means that the {@code action} is builtin.
     * @param args    The arguments to invoke the action with.
     * @param let     The expression to evaluate for use within the arguments via expansion.
     */
    public PipelineStage(
            final String action,
            final String service,
            final Map<String, Object> args,
            final Object let
    ) {
        _action = action;
        _service = service;
        _args = args;
        _let = let;
    }

    /**
     * Constructs a pipeline stage invoking a builtin action with no arguments.
     *
     * @param action The builtin action that represents this stage.
     */
    public PipelineStage(final String action) {
        this(action, null, null, null);
    }

    /**
     * Constructs a pipeline stage invoking a builtin action with some arguments.
     *
     * @param action The builtin action that represents this stage.
     * @param args   The arguments to invoke the action with.
     */
    public PipelineStage(final String action, final Map<String, Object> args) {
        this(action, null, args, null);
    }

    /**
     * Constructs a pipeline stage invoking a builtin action with some arguments and a let expression.
     *
     * @param action The builtin action that represents this stage.
     * @param args   The arguments to invoke the action with.
     * @param let    The expression to evaluate for use within the arguments via expansion.
     */
    public PipelineStage(final String action, final Map<String, Object> args, final Object let) {
        this(action, null, args, let);
    }

    /**
     * Constructs a pipeline stage invoking an action in a service with no arguments.
     *
     * @param action  The action that represents this stage.
     * @param service The service that can handle the {@code action}.
     *                service means that the {@code action} is builtin.
     */
    public PipelineStage(final String action, final String service) {
        this(action, service, null, null);
    }

    /**
     * Constructs a pipeline stage invoking an action in a service with some arguments.
     *
     * @param action  The action that represents this stage.
     * @param service The service that can handle the {@code action}.
     * @param args    The arguments to invoke the action with.
     *                service means that the {@code action} is builtin.
     */
    public PipelineStage(final String action, final String service, final Map<String, Object> args) {
        this(action, service, args, null);
    }
}
