package com.skanga.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.skanga.core.AgentObserver;
import com.skanga.core.Usage;
import com.skanga.core.messages.ToolCallMessage;
import com.skanga.core.messages.ToolCallResultMessage;
import com.skanga.observability.events.*;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link AgentObserver} that creates OpenTelemetry traces and spans for agent operations.
 * This class maps agent events to OpenTelemetry semantic conventions where possible,
 * providing insights into agent execution flow, LLM interactions, tool usage, and RAG processes.
 *
 * <p><b>Span Management Strategy:</b>
 * <ul>
 *   <li>A "flow ID" is derived from event data (e.g., hash of initial request) to correlate spans
 *       belonging to the same overall agent interaction (e.g., a single `agent.chat()` call).</li>
 *   <li>Top-level spans (e.g., for "AgentChat") are stored in {@code activeFlowSpans} using this flow ID.</li>
 *   <li>Child spans (e.g., "LLMInference", "ToolExecution") are created with the flow span as parent.
 *       If these child operations are themselves composed of start/stop events (like an inference call
 *       or a specific tool execution), their spans are stored in {@code activeSubSpans} keyed by a
 *       more specific identifier (e.g., flowId + "_inference", or "tool_" + toolCallId).</li>
 *   <li>This approach allows ending the correct span when corresponding "stop" or "result" events occur.</li>
 *   <li>Error events record exceptions on the relevant span and set its status to ERROR.</li>
 * </ul>
 * </p>
 *
 * <p><b>Attributes and Events:</b>
 * <ul>
 *   <li>Spans are enriched with attributes based on event data (e.g., model name, tool name,
 *       token counts, document counts).</li>
 *   <li>Complex event data or parts of it might be serialized to JSON and added as attributes,
 *       respecting potential size limits (truncation is applied).</li>
 *   <li>Generic events not specifically mapped to start/stop a span are added as OpenTelemetry
 *       {@code Span Events} on the current flow span.</li>
 * </ul>
 * </p>
 *
 * <p><b>Note on Context Propagation:</b> This monitor currently does not handle distributed context
 * propagation (e.g., continuing a trace from an incoming HTTP request). For distributed tracing,
 * the initial {@code Context} would need to be obtained from headers or another carrier and used as
 * the parent for the first span created by this monitor.</p>
 */
public class OpenTelemetryAgentMonitor implements AgentObserver {

    private final Tracer tracer;
    private final ObjectMapper objectMapper;

    // Stores top-level spans for an entire agent flow (e.g., one `chat()` call).
    // Key: A flow correlation ID (e.g., derived from initial request).
    private final Map<String, Span> activeFlowSpans = new ConcurrentHashMap<>();

    // Stores active sub-spans for operations within a flow (e.g., a specific inference call or tool call).
    // Key: A more specific ID (e.g., flowId + "_inference", or "tool_" + toolCallId).
    private final Map<String, Span> activeSubSpans = new ConcurrentHashMap<>();

    /** Max length for attribute values to prevent overly large telemetry data. */
    private static final int MAX_ATTRIBUTE_LENGTH = 2048;
    /** Max length for content attributes (like message content, tool arguments). */
    private static final int MAX_CONTENT_ATTRIBUTE_LENGTH = 512;


    /**
     * Constructs an OpenTelemetryAgentMonitor.
     * @param tracer The OpenTelemetry {@link Tracer} to use for creating spans. Must not be null.
     */
    public OpenTelemetryAgentMonitor(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "Tracer cannot be null.");
        this.objectMapper = new ObjectMapper();
        // Configure ObjectMapper for consistent serialization if needed
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @Override
    public void update(String eventType, Object eventData) {
        try {
            // Attempt to get a correlation ID for the current flow.
            // This is a simplified approach. Robust correlation might require explicit IDs passed in events.
            String flowId = getFlowCorrelationId(eventData, eventType);
            Span currentFlowSpan = (flowId != null) ? activeFlowSpans.get(flowId) : null;

            // Dispatch handling based on event type
            switch (eventType) {
                case "chat-start":
                    handleChatStart(eventData, flowId);
                    break;
                case "chat-stop":
                    handleChatStop(eventData, flowId);
                    break;
                case "inference-start":
                    handleInferenceStart(eventData, flowId, currentFlowSpan);
                    break;
                case "inference-stop":
                    handleInferenceStop(eventData, flowId);
                    break;
                case "tool-calling":
                    handleToolCalling(eventData, flowId, currentFlowSpan);
                    break;
                case "tool-called":
                    handleToolCalled(eventData);
                    break;
                case "rag-vectorstore-searching":
                    handleVectorStoreSearching(eventData, flowId, currentFlowSpan);
                    break;
                case "rag-vectorstore-result":
                    handleVectorStoreResult(eventData, flowId);
                    break;
                case "instructions-changed":
                    handleInstructionsChanged(eventData, currentFlowSpan);
                    break;
                // Structured Output Events
                case "structured-output-event": // This is the generic event from BaseAgent
                    if (eventData instanceof StructuredOutputEvent) {
                       handleStructuredOutputEvent((StructuredOutputEvent) eventData, flowId, currentFlowSpan);
                    }
                    break;
                case "error": // AgentError event
                    handleAgentError(eventData, currentFlowSpan);
                    break;
                default:
                    // For unhandled specific events, add them as a generic event to the flow span if it exists.
                    if (currentFlowSpan != null && currentFlowSpan.getSpanContext().isValid()) {
                        try {
                            currentFlowSpan.addEvent(eventType, buildJsonAttributes("event_data", eventData, MAX_ATTRIBUTE_LENGTH));
                        } catch (JsonProcessingException e) {
                            currentFlowSpan.addEvent(eventType + "_serialization_error");
                        }
                    } else {
                        // Log that an event occurred without a parent flow span if that's unexpected
                        // System.err.println("OTelMonitor: Event '" + eventType + "' received without an active flow span.");
                    }
                    break;
            }
        } catch (Exception e) {
            // Catch-all for errors within the monitor itself to prevent crashing the application.
            System.err.println("OpenTelemetryAgentMonitor internal error processing event '" + eventType + "': " + e.getMessage());
            e.printStackTrace(System.err); // Or use a dedicated internal logger for the monitor.
        }
    }

    // --- Specific Event Handlers ---

    private void handleChatStart(Object eventData, String flowId) {
        if (!(eventData instanceof ChatStart) || flowId == null) return;
        ChatStart cs = (ChatStart) eventData;

        Span chatSpan = tracer.spanBuilder("AgentChat").startSpan();
        setCommonAgentAttributes(chatSpan, cs.agentContext());
        if (cs.request() != null && cs.request().messages() != null && !cs.request().messages().isEmpty()) {
            chatSpan.setAttribute("jmcp.chat.input.message_count", (long) cs.request().messages().size());
            Object firstMsgContentObj = cs.request().messages().get(0).getContent();
            if (firstMsgContentObj != null) {
                 try {
                    chatSpan.setAttribute("jmcp.chat.input.first_message_content", truncate(objectMapper.writeValueAsString(firstMsgContentObj), MAX_CONTENT_ATTRIBUTE_LENGTH));
                } catch (JsonProcessingException e) { /* ignore attr */ }
            }
        }
        activeFlowSpans.put(flowId, chatSpan);
    }

    private void handleChatStop(Object eventData, String flowId) {
        if (!(eventData instanceof ChatStop) || flowId == null) return;
        Span chatSpanToEnd = activeFlowSpans.remove(flowId);
        if (chatSpanToEnd != null) {
            ChatStop cst = (ChatStop) eventData;
            chatSpanToEnd.setAttribute("jmcp.chat.duration_ms", cst.durationMs());
            if (cst.response() != null && cst.response().getUsage() != null) {
                Usage usage = cst.response().getUsage();
                chatSpanToEnd.setAttribute("llm.usage.prompt_tokens", (long) usage.promptTokens());
                chatSpanToEnd.setAttribute("llm.usage.completion_tokens", (long) usage.completionTokens());
                chatSpanToEnd.setAttribute("llm.usage.total_tokens", (long) usage.totalTokens());
            }
            chatSpanToEnd.setStatus(StatusCode.OK);
            chatSpanToEnd.end();
        }
    }

    private void handleInferenceStart(Object eventData, String flowId, Span parentFlowSpan) {
        if (!(eventData instanceof InferenceStart) || flowId == null) return;
        InferenceStart is = (InferenceStart) eventData;

        Context parentContext = parentFlowSpan != null ? Context.current().with(parentFlowSpan) : Context.current();
        Span inferenceSpan = tracer.spanBuilder("LLMInference").setParent(parentContext).startSpan();

        inferenceSpan.setAttribute("llm.system", is.providerName());
        inferenceSpan.setAttribute("llm.request.model", is.modelName());
        if (is.messages() != null) {
            inferenceSpan.setAttribute("llm.request.message_count", (long) is.messages().size());
        }
        if (is.tools() != null && !is.tools().isEmpty()) {
            inferenceSpan.setAttribute("llm.request.tool_count", (long) is.tools().size());
        }
        try {
            inferenceSpan.setAttribute("llm.request.parameters", truncate(objectMapper.writeValueAsString(is.parameters()), MAX_ATTRIBUTE_LENGTH));
        } catch (JsonProcessingException e) { /* ignore */ }

        activeSubSpans.put(getSubSpanKey(flowId, "inference"), inferenceSpan);
    }

    private void handleInferenceStop(Object eventData, String flowId) {
        if (!(eventData instanceof InferenceStop) || flowId == null) return;
        Span inferenceSpanToEnd = activeSubSpans.remove(getSubSpanKey(flowId, "inference"));
        if (inferenceSpanToEnd != null) {
            InferenceStop ist = (InferenceStop) eventData;
            inferenceSpanToEnd.setAttribute("llm.response.duration_ms", ist.durationMs());
            if (ist.response() != null && ist.response().getUsage() != null) {
                Usage usage = ist.response().getUsage();
                inferenceSpanToEnd.setAttribute("llm.usage.prompt_tokens", (long) usage.promptTokens());
                inferenceSpanToEnd.setAttribute("llm.usage.completion_tokens", (long) usage.completionTokens());
                inferenceSpanToEnd.setAttribute("llm.usage.total_tokens", (long) usage.totalTokens());
            }
            if (ist.response() != null && ist.response().getContent() instanceof ToolCallMessage) {
                inferenceSpanToEnd.setAttribute("llm.response.has_tool_calls", true);
            }
            inferenceSpanToEnd.setStatus(StatusCode.OK);
            inferenceSpanToEnd.end();
        }
    }

    private void handleToolCalling(Object eventData, String flowId, Span parentFlowSpan) {
        if (!(eventData instanceof ToolCalling)) return;
        ToolCalling tcStart = (ToolCalling) eventData;
        if (tcStart.toolCallMessage() != null && tcStart.toolCallMessage().toolCalls() != null) {
            Context parentContext = parentFlowSpan != null ? Context.current().with(parentFlowSpan) : Context.current();
            for (ToolCallMessage.ToolCall requestedCall : tcStart.toolCallMessage().toolCalls()) {
                Span toolSpan = tracer.spanBuilder("ToolExecution: " + requestedCall.function().name())
                        .setParent(parentContext)
                        .startSpan();
                toolSpan.setAttribute("tool.name", requestedCall.function().name());
                toolSpan.setAttribute("tool.call_id", requestedCall.id()); // ID from LLM request
                try {
                    toolSpan.setAttribute("tool.arguments", truncate(requestedCall.function().arguments(), MAX_CONTENT_ATTRIBUTE_LENGTH));
                } catch (Exception e) {
                    toolSpan.setAttribute("tool.arguments", "[serialization_failed]");
                }
                activeSubSpans.put(getSubSpanKey(flowId, "tool_" + requestedCall.id()), toolSpan);
            }
        }
    }

    private void handleToolCalled(Object eventData) {
        if (!(eventData instanceof ToolCalled)) return;
        ToolCalled tcEnd = (ToolCalled) eventData;
        // Note: flowId might not be easily derivable from ToolCalled event directly if it doesn't carry original request context.
        // Assuming tool_call_id is globally unique enough for this demo, or flowId needs to be part of ToolCalled event.
        // For now, we try to find related flowId from originalToolCallMessage if possible (not directly in eventData now)
        // This part needs robust correlation. Using tool_call_id directly for sub-span.
        if (tcEnd.toolResults() != null) {
            for (ToolCallResultMessage result : tcEnd.toolResults()) {
                // Need a way to get flowId here if subspan key includes it.
                // If "tool_" + result.toolCallId() was globally unique, flowId isn't strictly needed for subspan key.
                Span toolSpanToEnd = activeSubSpans.remove(getSubSpanKey(null, "tool_" + result.toolCallId())); // Assuming null flowId for now
                if (toolSpanToEnd != null) {
                    toolSpanToEnd.setAttribute("tool.result.content_summary", truncate(result.content(), MAX_CONTENT_ATTRIBUTE_LENGTH));
                    // Assuming tcEnd.durationMs() is for the whole batch. If per-tool, it should be on ToolCallResultMessage.
                    toolSpanToEnd.setAttribute("tool.execution.batch_duration_ms", tcEnd.durationMs());
                    toolSpanToEnd.setStatus(StatusCode.OK);
                    toolSpanToEnd.end();
                }
            }
        }
    }

    private void handleVectorStoreSearching(Object eventData, String flowId, Span parentFlowSpan) {
        if (!(eventData instanceof VectorStoreSearching) || flowId == null) return;
        VectorStoreSearching vss = (VectorStoreSearching) eventData;
        Context parentContext = parentFlowSpan != null ? Context.current().with(parentFlowSpan) : Context.current();
        Span vsSpan = tracer.spanBuilder("VectorStoreSearch")
                .setParent(parentContext)
                .startSpan();
        vsSpan.setAttribute("db.system", "vectorstore");
        vsSpan.setAttribute("db.operation", "search");
        vsSpan.setAttribute("db.vectorstore.name", vss.vectorStoreName());
        vsSpan.setAttribute("db.vectorstore.top_k", (long) vss.topK());
        if (vss.queryMessage() != null && vss.queryMessage().getContent() instanceof String) {
            vsSpan.setAttribute("db.vectorstore.query_text", truncate((String)vss.queryMessage().getContent(), MAX_CONTENT_ATTRIBUTE_LENGTH));
        }
        activeSubSpans.put(getSubSpanKey(flowId, "vectorsearch"), vsSpan);
    }

    private void handleVectorStoreResult(Object eventData, String flowId) {
        if (!(eventData instanceof VectorStoreResult) || flowId == null) return;
        VectorStoreResult vsr = (VectorStoreResult) eventData;
        Span vsSpanToEnd = activeSubSpans.remove(getSubSpanKey(flowId, "vectorsearch"));
        if (vsSpanToEnd != null) {
            vsSpanToEnd.setAttribute("db.vectorstore.result_count", vsr.documents() != null ? (long) vsr.documents().size() : 0L);
            vsSpanToEnd.setAttribute("db.vectorstore.duration_ms", vsr.durationMs());
            vsSpanToEnd.setStatus(StatusCode.OK);
            vsSpanToEnd.end();
        }
    }

    private void handleInstructionsChanged(Object eventData, Span currentFlowSpan) {
        if (!(eventData instanceof InstructionsChanged) || currentFlowSpan == null) return;
        InstructionsChanged ic = (InstructionsChanged) eventData;
        Attributes eventAttrs = Attributes.builder()
            // .put("jmcp.agent.old_instructions", truncate(ic.oldInstructions(), MAX_ATTRIBUTE_LENGTH)) // Potentially too large
            .put("jmcp.agent.new_instructions_summary", truncate(ic.newInstructions(), MAX_CONTENT_ATTRIBUTE_LENGTH))
            .put("jmcp.agent.instructions_change_context", ic.context() != null ? ic.context().toString() : "")
            .build();
        currentFlowSpan.addEvent("InstructionsChanged", eventAttrs);
    }

    private void handleStructuredOutputEvent(StructuredOutputEvent soe, String flowId, Span parentFlowSpan) {
        if (soe == null) return;
        Span currentSpan = parentFlowSpan != null ? parentFlowSpan : Span.current();
        if (currentSpan == null || !currentSpan.getSpanContext().isValid()) return;

        AttributesBuilder attrsBuilder = Attributes.builder();
        attrsBuilder.put("jmcp.structured_output.stage", soe.stage());
        if (soe.targetClass() != null) {
            attrsBuilder.put("jmcp.structured_output.target_class", soe.targetClass().getName());
        }
        // Add more relevant data from soe depending on stage, ensure truncation
        // Example: for "extracted", add hash or length of extractedJson.
        // For "validation_failed", add summary of validationErrors.
        currentSpan.addEvent("StructuredOutputLifecycle", attrsBuilder.build());
    }


    private void handleAgentError(Object eventData, Span currentFlowSpan) {
        if (!(eventData instanceof AgentError)) return;
        AgentError ae = (AgentError) eventData;

        Span spanToRecordError = currentFlowSpan;
        // If no specific flow span, try to get any current span or start a general error span
        if (spanToRecordError == null || !spanToRecordError.getSpanContext().isValid()) {
            spanToRecordError = Span.current();
        }
        if (spanToRecordError == null || !spanToRecordError.getSpanContext().isValid()) {
            // As a last resort, create a new span just for this error.
            spanToRecordError = tracer.spanBuilder("AgentGlobalError").startSpan();
        }

        spanToRecordError.recordException(ae.exception());
        spanToRecordError.setStatus(StatusCode.ERROR, ae.message() != null ? truncate(ae.message(), 128) : truncate(ae.exception().getMessage(), 128));
        spanToRecordError.setAttribute("error.critical", ae.critical());
        if (ae.context() != null && !ae.context().isEmpty()) {
             try {
                spanToRecordError.setAttribute("error.context", truncate(objectMapper.writeValueAsString(ae.context()), MAX_ATTRIBUTE_LENGTH));
            } catch (JsonProcessingException e) { /* ignore */ }
        }

        // If this was a last-resort global error span, end it. Otherwise, let the flow span end normally.
        if (spanToRecordError.toString().contains("AgentGlobalError")) { // Heuristic check
            spanToRecordError.end();
        }
    }


    // --- Helpers ---

    private String getFlowCorrelationId(Object eventData, String eventType) {
        // This is a simplified correlation logic. Robust systems might use explicit IDs.
        // For chat start/stop, use hash of initial request.
        // For events related to an ongoing flow, they should ideally carry the flow's correlation ID.
        if (eventData instanceof ChatStart) {
            return "chatflow_" + Objects.hashCode(((ChatStart) eventData).request());
        }
        if (eventData instanceof ChatStop) {
            return "chatflow_" + Objects.hashCode(((ChatStop) eventData).request());
        }
        // For other events, if they don't have a direct link to the initial request,
        // correlation becomes tricky. This example assumes they might occur within a context
        // where a flow ID was established (e.g., InferenceStart happens after ChatStart).
        // If eventData itself contains a traceable ID (e.g. from MessageRequest), use that.
        // This needs to be made more robust for production.
        return "default_flow"; // Fallback, not ideal for concurrent operations
    }

    private String getSubSpanKey(String flowId, String subOperation) {
        // Creates a unique key for sub-spans within a flow.
        // If flowId is null (e.g. for a tool call result where flow context is lost),
        // just use subOperation key, hoping it's unique enough (e.g. tool_call_id).
        return (flowId != null ? flowId + "_" : "") + subOperation;
    }

    private void setCommonAgentAttributes(Span span, Map<String, Object> agentContext) {
        if (agentContext != null) {
            for (Map.Entry<String, Object> entry : agentContext.entrySet()) {
                if (entry.getValue() != null) {
                    // Prefix to avoid collision with standard OpenTelemetry attributes
                    span.setAttribute("jmcp.agent.ctx." + entry.getKey(), truncate(entry.getValue().toString(), MAX_ATTRIBUTE_LENGTH));
                }
            }
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private Attributes buildJsonAttributes(String key, Object data, int maxLength) throws JsonProcessingException {
        if (data == null) return Attributes.empty();
        String jsonData = objectMapper.writeValueAsString(data);
        return Attributes.of(AttributeKey.stringKey(key), truncate(jsonData, maxLength));
    }
}
