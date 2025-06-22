package com.skanga.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.skanga.core.AgentObserver;
import com.skanga.observability.events.AgentError; // To check for critical errors
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * An implementation of {@link AgentObserver} that logs agent events using SLF4J.
 *
 * <p>Events are logged at different levels:
 * <ul>
 *   <li>{@code ERROR} for critical {@link AgentError} events.</li>
 *   <li>{@code WARN} for non-critical {@link AgentError} events.</li>
 *   <li>{@code INFO} for all other event types.</li>
 * </ul>
 * Event data is serialized to JSON using Jackson for structured logging, providing
 * detailed context for each event. If serialization fails, a fallback representation is logged.
 * </p>
 *
 * <p>Usage:
 * <pre>{@code
 * // Create a logger for a specific agent or component
 * Logger agentSpecificLogger = LoggerFactory.getLogger(MyAgent.class);
 * LoggingObserver loggerObserver = new LoggingObserver(agentSpecificLogger);
 *
 * // Or use a logger for a specific class
 * LoggingObserver classLoggerObserver = new LoggingObserver(MyAgent.class);
 *
 * // Or use a default logger
 * LoggingObserver defaultLoggerObserver = new LoggingObserver();
 *
 * // Register with an agent or observable component
 * myAgent.addObserver(loggerObserver, "*"); // Observe all events
 * }</pre>
 * </p>
 */
public class LoggingObserver implements AgentObserver {

    private final Logger logger;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a LoggingObserver with a specific SLF4J {@link Logger} instance.
     * @param logger The SLF4J logger to use for logging events. Must not be null.
     */
    public LoggingObserver(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null.");
        this.objectMapper = new ObjectMapper();
        // Configure objectMapper for potentially pretty printing and to handle common serialization issues.
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT); // Makes JSON log more readable
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS); // Prevents errors for objects with no serializable properties
        // Consider registering JavaTimeModule if eventData objects might contain Java 8 Time types:
        // this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    /**
     * Constructs a LoggingObserver, creating an SLF4J {@link Logger} associated with the given class.
     * This is a common pattern for obtaining a logger specific to a component.
     * @param clazz The class for which the logger should be named. Must not be null.
     */
    public LoggingObserver(Class<?> clazz) {
        this(LoggerFactory.getLogger(Objects.requireNonNull(clazz, "Class for logger name cannot be null.")));
    }

    /**
     * Constructs a LoggingObserver with a default logger named after this {@code LoggingObserver} class.
     */
    public LoggingObserver() {
        this(LoggerFactory.getLogger(LoggingObserver.class.getName()));
    }


    /**
     * {@inheritDoc}
     * <p>This method is called by an {@link com.skanga.core.ObservableAgentComponent}
     * when an event occurs. It logs the event type and a JSON representation of the event data.
     * {@link AgentError} events are logged at WARN or ERROR level based on their criticality,
     * including the exception stack trace. Other events are logged at INFO level.</p>
     */
    @Override
    public void update(String eventType, Object eventData) {
        // Determine appropriate log level (default to INFO)
        // Check if specific level is enabled before doing expensive serialization.

        if ("error".equalsIgnoreCase(eventType) && eventData instanceof AgentError) {
            AgentError agentError = (AgentError) eventData;
            String eventDataJson = serializeEventData(eventData);
            if (agentError.critical()) {
                if (logger.isErrorEnabled()) {
                    logger.error("CRITICAL AGENT EVENT: Type=[{}], Data: {}", eventType, eventDataJson, agentError.exception());
                }
            } else {
                if (logger.isWarnEnabled()) {
                    logger.warn("AGENT EVENT: Type=[{}], Data: {}", eventType, eventDataJson, agentError.exception());
                }
            }
        } else {
            if (logger.isInfoEnabled()) {
                String eventDataJson = serializeEventData(eventData);
                logger.info("AGENT EVENT: Type=[{}], Data: {}", eventType, eventDataJson);
            }
        }
    }

    /**
     * Serializes the event data to a JSON string for logging.
     * Handles null data and basic types directly, otherwise uses Jackson ObjectMapper.
     * @param eventData The data to serialize.
     * @return JSON string representation, or a fallback string on error/null.
     */
    private String serializeEventData(Object eventData) {
        if (eventData == null) {
            return "null";
        }
        // For simple types, their toString() is often sufficient and less overhead than JSON.
        if (eventData instanceof String || eventData instanceof Number || eventData instanceof Boolean) {
            return eventData.toString();
        }
        try {
            return objectMapper.writeValueAsString(eventData);
        } catch (JsonProcessingException e) {
            // Fallback if serialization fails
            return "[Serialization Error for type " + eventData.getClass().getName() + ": " + e.getMessage() +
                   ", Object.toString(): " + eventData.toString() + "]";
        }
    }
}
