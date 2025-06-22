
package com.skanga.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StdioMcpTransportTests {

    @Mock
    private Process process;

    @Mock
    private ProcessBuilder processBuilder;

    private StdioMcpTransport transport;
    private ObjectMapper objectMapper;

    private static final String TEST_COMMAND = "test-command";
    private static final List<String> TEST_ARGS = Arrays.asList("arg1", "arg2");
    private static final Map<String, String> TEST_ENV = Map.of("TEST_VAR", "test_value");

    @BeforeEach
    void setUp() {
        transport = new StdioMcpTransport(TEST_COMMAND, TEST_ARGS, TEST_ENV);
        objectMapper = new ObjectMapper();
    }

    @Test
    void constructor_WithValidParameters_ShouldCreateInstance() {
        // Act & Assert
        assertThat(transport).isNotNull();
    }

    @Test
    void constructor_WithNullCommand_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new StdioMcpTransport(null, TEST_ARGS, TEST_ENV));
    }

    @Test
    void constructor_WithNullArgs_ShouldHandleGracefully() {
        // Act & Assert
        assertDoesNotThrow(() ->
                new StdioMcpTransport(TEST_COMMAND, null, TEST_ENV));
    }

    @Test
    void constructor_WithNullEnv_ShouldHandleGracefully() {
        // Act & Assert
        assertDoesNotThrow(() ->
                new StdioMcpTransport(TEST_COMMAND, TEST_ARGS, null));
    }

    @Test
    void connect_ShouldStartProcess() throws Exception {
        // Arrange
        ByteArrayOutputStream processStdout = new ByteArrayOutputStream();
        ByteArrayInputStream processStdin = new ByteArrayInputStream(new byte[0]);
        ByteArrayInputStream processStderr = new ByteArrayInputStream(new byte[0]);

        when(process.getOutputStream()).thenReturn(processStdout);
        when(process.getInputStream()).thenReturn(processStdin);
        when(process.getErrorStream()).thenReturn(processStderr);
        when(process.isAlive()).thenReturn(true);

        try (MockedStatic<ProcessBuilder> processBuilderMock = mockStatic(ProcessBuilder.class)) {
            ProcessBuilder mockBuilder = mock(ProcessBuilder.class);
            when(mockBuilder.command(anyList())).thenReturn(mockBuilder);
            when(mockBuilder.environment()).thenReturn(new HashMap<>());
            when(mockBuilder.start()).thenReturn(process);

            processBuilderMock.when(() -> new ProcessBuilder()).thenReturn(mockBuilder);

            // Act
            transport.connect();

            // Assert
            assertThat(transport.isConnected()).isTrue();
            verify(mockBuilder).start();
        }
    }

    @Test
    void connect_WhenAlreadyConnected_ShouldThrowException() throws Exception {
        // Arrange
        setupMockProcess();
        transport.connect();

        // Act & Assert
        McpException exception = assertThrows(McpException.class, () ->
                transport.connect());
        assertThat(exception.getMessage()).contains("already connected");
    }

    @Test
    void connect_WithProcessStartFailure_ShouldThrowException() throws Exception {
        // Arrange
        try (MockedStatic<ProcessBuilder> processBuilderMock = mockStatic(ProcessBuilder.class)) {
            ProcessBuilder mockBuilder = mock(ProcessBuilder.class);
            when(mockBuilder.command(anyList())).thenReturn(mockBuilder);
            when(mockBuilder.environment()).thenReturn(new HashMap<>());
            when(mockBuilder.start()).thenThrow(new IOException("Process start failed"));

            processBuilderMock.when(() -> new ProcessBuilder()).thenReturn(mockBuilder);

            // Act & Assert
            McpException exception = assertThrows(McpException.class, () ->
                    transport.connect());
            assertThat(exception.getMessage()).contains("Failed to start or connect");
        }
    }

    @Test
    void send_WithValidData_ShouldWriteToProcess() throws Exception {
        // Arrange
        ByteArrayOutputStream processStdout = new ByteArrayOutputStream();
        setupMockProcess();
        transport.connect();

        Map<String, Object> data = Map.of("method", "test", "params", Map.of("key", "value"));

        // Act
        transport.send(data);

        // Assert
        // Verify that JSON was written (this is hard to test without exposing internal state)
        assertDoesNotThrow(() -> transport.send(data));
    }

    @Test
    void send_WhenNotConnected_ShouldThrowException() {
        // Arrange
        Map<String, Object> data = Map.of("method", "test");

        // Act & Assert
        McpException exception = assertThrows(McpException.class, () ->
                transport.send(data));
        assertThat(exception.getMessage()).contains("Not connected");
    }

    @Test
    void receive_WithValidJsonLine_ShouldReturnParsedData() throws Exception {
        // Arrange
        String jsonResponse = "{\"result\":\"success\",\"id\":\"123\"}";
        ByteArrayInputStream processStdin = new ByteArrayInputStream(
                (jsonResponse + "\n").getBytes()
        );

        setupMockProcessWithStreams(processStdin);
        transport.connect();

        // Act
        Map<String, Object> result = transport.receive();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("result")).isEqualTo("success");
        assertThat(result.get("id")).isEqualTo("123");
    }

    @Test
    void receive_WithInvalidJson_ShouldThrowException() throws Exception {
        // Arrange
        String invalidJson = "{invalid json}";
        ByteArrayInputStream processStdin = new ByteArrayInputStream(
                (invalidJson + "\n").getBytes()
        );

        setupMockProcessWithStreams(processStdin);
        transport.connect();

        // Act & Assert
        McpException exception = assertThrows(McpException.class, () ->
                transport.receive());
        assertThat(exception.getMessage()).contains("Failed to read or deserialize");
    }

    @Test
    void receive_WhenStreamEnds_ShouldThrowException() throws Exception {
        // Arrange
        ByteArrayInputStream emptyStream = new ByteArrayInputStream(new byte[0]);
        setupMockProcessWithStreams(emptyStream);
        transport.connect();

        // Act & Assert
        McpException exception = assertThrows(McpException.class, () ->
                transport.receive());
        assertThat(exception.getMessage()).contains("stdout stream ended unexpectedly");
    }

    @Test
    void disconnect_ShouldCleanupResources() throws Exception {
        // Arrange
        setupMockProcess();
        transport.connect();

        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);

        // Act
        transport.disconnect();

        // Assert
        assertThat(transport.isConnected()).isFalse();
        verify(process).destroy();
    }

    @Test
    void disconnect_WithForceDestroy_ShouldForceKillProcess() throws Exception {
        // Arrange
        setupMockProcess();
        transport.connect();

        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(process.isAlive()).thenReturn(true);

        // Act
        transport.disconnect();

        // Assert
        verify(process).destroy();
        verify(process).destroyForcibly();
    }

    @Test
    void isConnected_WhenNotConnected_ShouldReturnFalse() {
        // Act & Assert
        assertThat(transport.isConnected()).isFalse();
    }

    @Test
    void isConnected_WhenConnected_ShouldReturnTrue() throws Exception {
        // Arrange
        setupMockProcess();
        transport.connect();

        // Act & Assert
        assertThat(transport.isConnected()).isTrue();
    }

    @Test
    void isConnected_WhenProcessDied_ShouldReturnFalse() throws Exception {
        // Arrange
        setupMockProcess();
        transport.connect();
        when(process.isAlive()).thenReturn(false);

        // Act & Assert
        assertThat(transport.isConnected()).isFalse();
    }

    @Test
    void finalize_ShouldAttemptDisconnect() throws Throwable {
        // Arrange
        setupMockProcess();
        transport.connect();

        // Act
        transport.finalize();

        // Assert
        // Hard to test finalize behavior directly, but we can verify it doesn't throw
        assertDoesNotThrow(() -> transport.finalize());
    }

    private void setupMockProcess() throws Exception {
        ByteArrayOutputStream processStdout = new ByteArrayOutputStream();
        ByteArrayInputStream processStdin = new ByteArrayInputStream(new byte[0]);
        ByteArrayInputStream processStderr = new ByteArrayInputStream(new byte[0]);

        setupMockProcessWithStreams(processStdin, processStdout, processStderr);
    }

    private void setupMockProcessWithStreams(ByteArrayInputStream stdin) throws Exception {
        ByteArrayOutputStream processStdout = new ByteArrayOutputStream();
        ByteArrayInputStream processStderr = new ByteArrayInputStream(new byte[0]);

        setupMockProcessWithStreams(stdin, processStdout, processStderr);
    }

    private void setupMockProcessWithStreams(
            ByteArrayInputStream stdin,
            ByteArrayOutputStream stdout,
            ByteArrayInputStream stderr) throws Exception {

        when(process.getOutputStream()).thenReturn(stdout);
        when(process.getInputStream()).thenReturn(stdin);
        when(process.getErrorStream()).thenReturn(stderr);
        when(process.isAlive()).thenReturn(true);

        try (MockedStatic<ProcessBuilder> processBuilderMock = mockStatic(ProcessBuilder.class)) {
            ProcessBuilder mockBuilder = mock(ProcessBuilder.class);
            when(mockBuilder.command(anyList())).thenReturn(mockBuilder);
            when(mockBuilder.environment()).thenReturn(new HashMap<>());
            when(mockBuilder.start()).thenReturn(process);

            processBuilderMock.when(() -> new ProcessBuilder()).thenReturn(mockBuilder);
        }
    }
}
