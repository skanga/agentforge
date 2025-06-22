package com.skanga.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ObservableSupportTests {

    private ObservableSupport observableSupport;
    private AgentObserver mockObserver1;
    private AgentObserver mockObserver2;

    @BeforeEach
    void setUp() {
        observableSupport = new ObservableSupport(this); // Pass a source object
        mockObserver1 = mock(AgentObserver.class);
        mockObserver2 = mock(AgentObserver.class);
    }

    @Test
    void addObserver_shouldAddObserver() {
        observableSupport.addObserver(mockObserver1, "*");
        assertEquals(1, observableSupport.getObserverCount());
        observableSupport.addObserver(mockObserver2, "test-event");
        assertEquals(2, observableSupport.getObserverCount());
    }

    @Test
    void removeObserver_shouldRemoveObserver() {
        observableSupport.addObserver(mockObserver1, "*");
        observableSupport.addObserver(mockObserver2, "test-event");
        assertEquals(2, observableSupport.getObserverCount());

        observableSupport.removeObserver(mockObserver1);
        assertEquals(1, observableSupport.getObserverCount());
        // Verify mockObserver2 is still there by trying to notify it
        observableSupport.notifyObservers("test-event", "data");
        verify(mockObserver2, times(1)).update(eq("test-event"), any());

        observableSupport.removeObserver(mockObserver2);
        assertEquals(0, observableSupport.getObserverCount());
    }

    @Test
    void removeObserver_nonExistentObserver_shouldDoNothing() {
        observableSupport.addObserver(mockObserver1, "*");
        assertEquals(1, observableSupport.getObserverCount());
        observableSupport.removeObserver(mockObserver2); // mockObserver2 was not added with this exact instance
        assertEquals(1, observableSupport.getObserverCount());
    }

    @Test
    void removeAllObservers_shouldRemoveAll() {
        observableSupport.addObserver(mockObserver1, "*");
        observableSupport.addObserver(mockObserver2, "test-event");
        assertEquals(2, observableSupport.getObserverCount());
        observableSupport.removeAllObservers();
        assertEquals(0, observableSupport.getObserverCount());
    }


    @Test
    void notifyObservers_allFilter_shouldNotifyObserver() {
        Object eventData = "test_data";
        observableSupport.addObserver(mockObserver1, "*");
        observableSupport.notifyObservers("any-event", eventData);
        verify(mockObserver1, times(1)).update("any-event", eventData);
    }

    @Test
    void notifyObservers_specificFilterMatch_shouldNotifyObserver() {
        Object eventData = "specific_data";
        observableSupport.addObserver(mockObserver1, "specific-event");
        observableSupport.notifyObservers("specific-event", eventData);
        verify(mockObserver1, times(1)).update("specific-event", eventData);
    }

    @Test
    void notifyObservers_specificFilterMatchCaseInsensitive_shouldNotifyObserver() {
        Object eventData = "specific_data_case";
        observableSupport.addObserver(mockObserver1, "Specific-Event-UPPER"); // Filter is stored lowercase
        observableSupport.notifyObservers("specific-event-upper", eventData); // Event type is also lowercased for matching
        verify(mockObserver1, times(1)).update("specific-event-upper", eventData);
    }


    @Test
    void notifyObservers_specificFilterNoMatch_shouldNotNotifyObserver() {
        Object eventData = "other_data";
        observableSupport.addObserver(mockObserver1, "specific-event");
        observableSupport.notifyObservers("other-event", eventData);
        verify(mockObserver1, never()).update(anyString(), any());
    }

    @Test
    void notifyObservers_multipleObservers_correctNotifications() {
        Object eventDataAll = "all_data";
        Object eventDataSpecific = "specific_data";

        observableSupport.addObserver(mockObserver1, "*");
        observableSupport.addObserver(mockObserver2, "specific-event");

        // Notify for "specific-event"
        observableSupport.notifyObservers("specific-event", eventDataSpecific);
        verify(mockObserver1, times(1)).update("specific-event", eventDataSpecific);
        verify(mockObserver2, times(1)).update("specific-event", eventDataSpecific);

        // Notify for "other-event"
        observableSupport.notifyObservers("other-event", eventDataAll);
        verify(mockObserver1, times(1)).update("other-event", eventDataAll); // mockObserver1 gets this
        verify(mockObserver2, never()).update("other-event", eventDataAll);  // mockObserver2 does not

        // Ensure counts are correct overall
        verify(mockObserver1, times(2)).update(anyString(), any()); // Called for both events
        verify(mockObserver2, times(1)).update(anyString(), any()); // Called only for "specific-event"
    }

    @Test
    void notifyObservers_nullEventFilterDefaultsToAll() {
        Object eventData = "test_data";
        observableSupport.addObserver(mockObserver1, null); // Null filter should default to "*"
        observableSupport.notifyObservers("any-event", eventData);
        verify(mockObserver1, times(1)).update("any-event", eventData);
    }

    @Test
    void notifyObservers_emptyEventFilterDefaultsToAll() {
        Object eventData = "test_data_empty_filter";
        observableSupport.addObserver(mockObserver1, "  "); // Empty/whitespace filter should default to "*"
        observableSupport.notifyObservers("any-event-for-empty", eventData);
        verify(mockObserver1, times(1)).update("any-event-for-empty", eventData);
    }


    @Test
    void notifyObservers_observerThrowsException_shouldNotStopOtherObservers() {
        Object eventData = "data_for_exception_test";
        AgentObserver faultyObserver = mock(AgentObserver.class);
        doThrow(new RuntimeException("Test Exception from Observer")).when(faultyObserver).update(anyString(), any());

        observableSupport.addObserver(faultyObserver, "*");
        observableSupport.addObserver(mockObserver1, "*"); // mockObserver1 should still be called

        // No exception should propagate out of notifyObservers
        assertDoesNotThrow(() -> observableSupport.notifyObservers("event-causing-fault", eventData));

        // Verify both were attempted
        verify(faultyObserver, times(1)).update("event-causing-fault", eventData);
        verify(mockObserver1, times(1)).update("event-causing-fault", eventData);
    }

    @Test
    void notifyObservers_nullEventType_shouldNotNotifyAndLogError() {
        // Assuming System.err is used for logging internal errors in ObservableSupport
        // This is hard to test precisely without redirecting System.err
        // For now, just ensure no observers are called and no exception is thrown.
        observableSupport.addObserver(mockObserver1, "*");
        assertDoesNotThrow(() -> observableSupport.notifyObservers(null, "data"));
        verify(mockObserver1, never()).update(anyString(), any());
    }

    @Test
    void notifyObservers_emptyEventType_shouldNotNotifyAndLogError() {
        observableSupport.addObserver(mockObserver1, "*");
        assertDoesNotThrow(() -> observableSupport.notifyObservers("  ", "data"));
        verify(mockObserver1, never()).update(anyString(), any());
    }

    @Test
    void addObserver_nullObserver_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            observableSupport.addObserver(null, "*");
        });
    }

    @Test
    void removeObserver_nullObserver_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            observableSupport.removeObserver(null);
        });
    }
}
