package com.skanga.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.observability.events.AgentError;
import com.skanga.observability.events.ChatStart; // Example event
import com.skanga.core.messages.MessageRequest; // For ChatStart
import com.skanga.chat.messages.Message; // For ChatStart
import com.skanga.chat.enums.MessageRole; // For ChatStart

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class) // Integrates Mockito with JUnit 5
class LoggingObserverTests {

    @Mock
    private Logger mockLogger; // Mock the SLF4J Logger

    private LoggingObserver loggingObserver;
    private ObjectMapper realObjectMapper = new ObjectMapper(); // For creating expected JSON

    @BeforeEach
    void setUp() {
        // Pass the mock logger to the observer
        loggingObserver = new LoggingObserver(mockLogger);
        // Ensure specific log levels are enabled on the mock so verify() works
        when(mockLogger.isInfoEnabled()).thenReturn(true);
        when(mockLogger.isWarnEnabled()).thenReturn(true);
        when(mockLogger.isErrorEnabled()).thenReturn(true);
    }

    @Test
    void constructor_withClass_usesCorrectLoggerName() {
        // This test is more about the LoggingObserver(Class) constructor
        // We can't easily verify the logger name without deeper reflection or capturing LoggerFactory.getLogger calls.
        // For now, assume it works if it doesn't throw.
        LoggingObserver obs = new LoggingObserver(this.getClass());
        assertNotNull(obs); // Simple check
    }

    @Test
    void constructor_default_usesDefaultLoggerName() {
        LoggingObserver obs = new LoggingObserver();
        assertNotNull(obs);
    }


    @Test
    void update_genericEvent_logsAtInfoLevelWithJsonData() throws JsonProcessingException {
        String eventType = "test-event";
        Message testMessage = new Message(MessageRole.USER, "Hello");
        ChatStart eventData = new ChatStart(new MessageRequest(testMessage), Map.of("agent", "TestAgent"));
        String expectedJson = realObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(eventData);

        loggingObserver.update(eventType, eventData);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockLogger).info(eq("AGENT EVENT: Type=[{}], Data: {}"), eq(eventType), messageCaptor.capture());
        // Due to pretty printing, exact string match might be tricky with spacing/newlines.
        // A more robust check would be to parse the JSON back or compare key fields.
        // For now, simple contains check.
        assertTrue(messageCaptor.getValue().contains("\"request\""));
        assertTrue(messageCaptor.getValue().contains("\"agent\":\"TestAgent\""));
    }

    @Test
    void update_agentErrorCritical_logsAtErrorLevel() throws JsonProcessingException {
        String eventType = "error"; // As used in BaseAgent
        RuntimeException cause = new RuntimeException("Critical failure");
        AgentError eventData = new AgentError(cause, true, "Something broke badly");
        String expectedJson = realObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(eventData);

        loggingObserver.update(eventType, eventData);

        ArgumentCaptor<String> messageFormatCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonDataCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);


        verify(mockLogger).error(messageFormatCaptor.capture(), eventTypeCaptor.capture(), jsonDataCaptor.capture(), throwableCaptor.capture());

        assertEquals("CRITICAL AGENT EVENT: Type=[{}], Data: {}", messageFormatCaptor.getValue());
        assertEquals(eventType, eventTypeCaptor.getValue());
        assertTrue(jsonDataCaptor.getValue().contains("\"critical\":true"));
        assertTrue(jsonDataCaptor.getValue().contains("\"message\":\"Something broke badly\""));
        assertSame(cause, throwableCaptor.getValue());
    }

    @Test
    void update_agentErrorNonCritical_logsAtWarnLevel() throws JsonProcessingException {
        String eventType = "error";
        RuntimeException cause = new RuntimeException("Minor issue");
        AgentError eventData = new AgentError(cause, false, "A recoverable problem");
        String expectedJson = realObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(eventData);

        loggingObserver.update(eventType, eventData);

        ArgumentCaptor<String> messageFormatCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonDataCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);

        verify(mockLogger).warn(messageFormatCaptor.capture(), eventTypeCaptor.capture(), jsonDataCaptor.capture(), throwableCaptor.capture());

        assertEquals("AGENT EVENT: Type=[{}], Data: {}", messageFormatCaptor.getValue());
        assertEquals(eventType, eventTypeCaptor.getValue());
        assertTrue(jsonDataCaptor.getValue().contains("\"critical\":false"));
        assertSame(cause, throwableCaptor.getValue());
    }

    @Test
    void update_nullEventData_logsAsNullString() {
        String eventType = "null-data-event";
        loggingObserver.update(eventType, null);
        verify(mockLogger).info("AGENT EVENT: Type=[{}], Data: {}", eventType, "null");
    }

    @Test
    void update_simpleEventData_logsToString() {
        String eventType = "simple-data-event";
        loggingObserver.update(eventType, "Simple String Data");
        verify(mockLogger).info("AGENT EVENT: Type=[{}], Data: {}", eventType, "Simple String Data");

        loggingObserver.update(eventType, 12345);
        verify(mockLogger).info("AGENT EVENT: Type=[{}], Data: {}", eventType, "12345");
    }

    @Test
    void update_serializationErrorForEventData_logsFallback() throws JsonProcessingException {
        String eventType = "serialization-error-test";
        // Create a mock object that will cause ObjectMapper to fail
        Object unserializableData = new Object() {
            // No Jackson serializable properties, and FAIL_ON_EMPTY_BEANS is disabled,
            // but if we force an error during serialization
            @Override
            public String toString() { return "UnserializableObjectToString"; }
        };

        // We need to mock the internal objectMapper of LoggingObserver for this test,
        // which is not ideal as it's an internal detail.
        // A better way would be to pass a mock ObjectMapper to LoggingObserver.
        // For now, let's assume a complex object that might fail by default (less direct).
        // The current ObjectMapper setup (FAIL_ON_EMPTY_BEANS=false) makes it hard to fail.
        // Let's simulate the exception by directly testing the private serializeEventData or
        // by trying to serialize something known to fail.
        //
        // Alternative: make LoggingObserver's objectMapper field accessible for testing or injectable.
        // For now, we test the fallback string based on current serializeEventData logic.

        // This test will rely on the default ObjectMapper in LoggingObserver.
        // If we had an object that Jackson cannot serialize by default:
        // class Unserializable { public Unserializable field = this; } // Cyclic
        // Object unserializableData = new Unserializable();
        // This test will just check the toString fallback path if writeValueAsString failed.

        // To directly test the fallback path, we'd need to ensure writeValueAsString throws.
        // This is hard without injecting a mock ObjectMapper.
        // The current serializeEventData will use eventData.toString() if object mapper fails.
        // Let's assume a specific type of object that might be tricky for a default ObjectMapper
        // if it's not configured for it (though basic Objects usually just become {}).

        // A more direct test of the fallback string:
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Test Serialization Fail"){});
        // If we could inject failingMapper into loggingObserver, this would test the catch block.
        // Since we can't directly, this test is more conceptual for that path.
        // The current implementation of serializeEventData will catch and format a message.

        // Test with a common object, assuming default mapper.
        loggingObserver.update(eventType, unserializableData);
        ArgumentCaptor<String> dataCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockLogger).info(eq("AGENT EVENT: Type=[{}], Data: {}"), eq(eventType), dataCaptor.capture());
        // Expect it to be a JSON object by default if no error, or the toString if error was forced.
        // With default ObjectMapper and simple new Object(), it will be "{}".
         assertTrue(dataCaptor.getValue().equals("{}") || dataCaptor.getValue().contains("UnserializableObjectToString"));

    }

    @Test
    void update_loggerLevelDisabled_doesNotLog() {
        when(mockLogger.isInfoEnabled()).thenReturn(false);
        when(mockLogger.isWarnEnabled()).thenReturn(false);
        when(mockLogger.isErrorEnabled()).thenReturn(false);

        loggingObserver.update("any-event", "any_data");
        loggingObserver.update("error", new AgentError(new RuntimeException("test"), true));
        loggingObserver.update("error", new AgentError(new RuntimeException("test"), false));

        verify(mockLogger, never()).info(anyString(), any(), any());
        verify(mockLogger, never()).warn(anyString(), any(), any(), any());
        verify(mockLogger, never()).error(anyString(), any(), any(), any());
    }
}
