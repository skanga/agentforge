package com.skanga.observability.events;

import com.skanga.chat.messages.Message;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents events occurring during various stages of structured output processing
 * when an agent attempts to convert an LLM's response into a specific Java object.
 *
 * <p>This event can be used to trace the lifecycle of structured output generation, including:
 * <ul>
 *   <li><b>extracting:</b> When the system is attempting to extract a potential JSON string (or other structured format) from the LLM's raw response.</li>
 *   <li><b>extracted:</b> After a JSON string (or other intermediate format) has been successfully extracted.</li>
 *   <li><b>deserializing:</b> When the system is attempting to deserialize the extracted string into the target Java class.</li>
 *   <li><b>deserialized:</b> After successful deserialization into the target Java object.</li>
 *   <li><b>validating:</b> When the deserialized object is being validated against a schema or other rules.</li>
 *   <li><b>validated:</b> After successful validation.</li>
 *   <li><b>validation_failed:</b> If validation of the deserialized object fails.</li>
 * </ul>
 * </p>
 *
 * @param stage              The specific stage of processing (e.g., "extracting", "deserialized", "validated"). Must not be null.
 * @param rawResponse        The raw {@link Message} from the LLM. Relevant for "extracting", "extracted" stages. Can be null otherwise.
 * @param extractedJson      The extracted JSON string (or other intermediate structured string). Relevant for "extracted", "deserializing", "deserialized" stages. Can be null.
 * @param deserializedObject The Java object resulting from deserialization. Relevant for "deserialized", "validating", "validated", "validation_failed" stages. Can be null.
 * @param validationSchema   The schema (e.g., JSON Schema as a Map, or another schema object) used for validation. Relevant for "validating", "validated", "validation_failed". Can be null.
 * @param validationErrors   Details of validation errors if validation failed. Could be a List of error messages or a more structured error object. Relevant for "validation_failed". Can be null.
 * @param targetClass        The target Java {@link Class} into which the LLM response is intended to be deserialized. Can be null if not applicable to the stage.
 * @param context            Optional map for additional context. A defensive copy is made.
 */
public record StructuredOutputEvent(
    String stage,
    Message rawResponse,
    String extractedJson,
    Object deserializedObject,
    Object validationSchema,
    Object validationErrors,
    Class<?> targetClass,
    Map<String, Object> context
) {
    /**
     * Canonical constructor for StructuredOutputEvent.
     * Ensures stage is not null and makes context map unmodifiable.
     */
    public StructuredOutputEvent {
        Objects.requireNonNull(stage, "Stage cannot be null for StructuredOutputEvent.");
        context = (context != null) ? Collections.unmodifiableMap(context) : Collections.emptyMap();
    }

    // --- Convenience static factory methods for different stages ---

    /** Creates an event for the "extracting" stage. */
    public static StructuredOutputEvent extracting(Message rawResponse, Class<?> targetClass, Map<String, Object> context) {
        return new StructuredOutputEvent("extracting", rawResponse, null, null, null, null, targetClass, context);
    }
    public static StructuredOutputEvent extracting(Message rawResponse, Class<?> targetClass) {
        return extracting(rawResponse, targetClass, null);
    }

    /** Creates an event for the "extracted" stage. */
    public static StructuredOutputEvent extracted(Message rawResponse, String extractedJson, Class<?> targetClass, Map<String, Object> context) {
        return new StructuredOutputEvent("extracted", rawResponse, extractedJson, null, null, null, targetClass, context);
    }
     public static StructuredOutputEvent extracted(Message rawResponse, String extractedJson, Class<?> targetClass) {
        return extracted(rawResponse, extractedJson, targetClass, null);
    }

    /** Creates an event for the "deserializing" stage. */
    public static StructuredOutputEvent deserializing(String extractedJson, Class<?> targetClass, Map<String, Object> context) {
        return new StructuredOutputEvent("deserializing", null, extractedJson, null, null, null, targetClass, context);
    }
    public static StructuredOutputEvent deserializing(String extractedJson, Class<?> targetClass) {
        return deserializing(extractedJson, targetClass, null);
    }

    /** Creates an event for the "deserialized" stage. */
    public static StructuredOutputEvent deserialized(String extractedJson, Object deserializedObject, Class<?> targetClass, Map<String, Object> context) {
        return new StructuredOutputEvent("deserialized", null, extractedJson, deserializedObject, null, null, targetClass, context);
    }
     public static StructuredOutputEvent deserialized(String extractedJson, Object deserializedObject, Class<?> targetClass) {
        return deserialized(extractedJson, deserializedObject, targetClass, null);
    }

    /** Creates an event for the "validating" stage. */
    public static StructuredOutputEvent validating(Object deserializedObject, Object validationSchema, Class<?> targetClass, Map<String, Object> context) {
        return new StructuredOutputEvent("validating", null, null, deserializedObject, validationSchema, null, targetClass, context);
    }
    public static StructuredOutputEvent validating(Object deserializedObject, Object validationSchema, Class<?> targetClass) {
        return validating(deserializedObject, validationSchema, targetClass, null);
    }

    /** Creates an event for the "validated" (successful validation) stage. */
    public static StructuredOutputEvent validated(Object deserializedObject, Object validationSchema, Class<?> targetClass, Map<String, Object> context) {
        return new StructuredOutputEvent("validated", null, null, deserializedObject, validationSchema, null, targetClass, context);
    }
     public static StructuredOutputEvent validated(Object deserializedObject, Object validationSchema, Class<?> targetClass) {
        return validated(deserializedObject, validationSchema, targetClass, null);
    }

    /** Creates an event for the "validation_failed" stage. */
    public static StructuredOutputEvent validationFailed(Object deserializedObject, Object validationSchema, Object validationErrors, Class<?> targetClass, Map<String, Object> context) {
        return new StructuredOutputEvent("validation_failed", null, null, deserializedObject, validationSchema, validationErrors, targetClass, context);
    }
    public static StructuredOutputEvent validationFailed(Object deserializedObject, Object validationSchema, Object validationErrors, Class<?> targetClass) {
        return validationFailed(deserializedObject, validationSchema, validationErrors, targetClass, null);
    }
}
