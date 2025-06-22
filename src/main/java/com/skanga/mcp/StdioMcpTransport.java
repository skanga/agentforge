package com.skanga.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of {@link McpTransport} that communicates with an MCP server
 * process over standard input/output (stdio).
 *
 * <p>This transport starts an external command (the MCP server) and interacts with it
 * by writing JSON-RPC requests to its stdin and reading JSON-RPC responses from its stdout.
 * It also includes basic handling for the server's stderr stream.</p>
 *
 * <p><b>Process Management:</b>
 * <ul>
 *   <li>The MCP server process is started using {@link ProcessBuilder}.</li>
 *   <li>Environment variables can be passed to the server process.</li>
 *   <li>A daemon thread is started to consume the server's stderr stream to prevent
 *       the server process from blocking due to a full stderr buffer.</li>
 *   <li>The {@link #disconnect()} method handles terminating the process and cleaning up resources.</li>
 * </ul>
 * </p>
 *
 * <p><b>Communication:</b>
 * <ul>
 *   <li>Messages are expected to be JSON strings, one per line.</li>
 *   <li>An {@link ObjectMapper} (from Jackson) is used for JSON serialization/deserialization.</li>
 * </ul>
 * </p>
 *
 * <p><b>Timeout Note for {@code receive()}:</b>
 * The current implementation of {@link #receive()} uses a blocking {@code BufferedReader.readLine()}.
 * If the MCP server process hangs and does not send a response or a newline, this read operation
 * will block indefinitely. Robust timeout handling for line-based reads typically requires
 * more complex non-blocking I/O (NIO) or managing the read operation in a separate thread
 * that can be timed out. This is a known area for potential future enhancement if processes
 * are unreliable.
 * </p>
 */
public class StdioMcpTransport implements McpTransport, AutoCloseable {
    private Process process;
    private PrintWriter processStdin;
    private BufferedReader processStdout;
    private BufferedReader processStderrReader;

    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private final ObjectMapper objectMapper;

    private volatile boolean connected = false;
    private Thread stderrConsumerThread;


    /**
     * Constructs an StdioMcpTransport.
     *
     * @param command The command to execute to start the MCP server process. Must not be null.
     * @param args    A list of arguments for the command. Can be null or empty.
     * @param env     A map of environment variables to set for the MCP server process.
     *                These are merged with the current JVM's environment. Can be null or empty.
     */
    public StdioMcpTransport(String command, List<String> args, Map<String, String> env) {
        Objects.requireNonNull(command, "MCP server command cannot be null.");
        this.command = command;
        this.args = args == null ? List.of() : List.copyOf(args); // Ensure immutability
        this.env = env == null ? Map.of() : Map.copyOf(env);     // Ensure immutability
        this.objectMapper = new ObjectMapper();
    }

    public synchronized void connect() throws McpException {
        if (isConnected()) {
            throw new McpException("StdioMcpTransport is already connected.");
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        List<String> commandAndArgs = new ArrayList<>();
        commandAndArgs.add(command);
        commandAndArgs.addAll(args);
        processBuilder.command(commandAndArgs);

        Map<String, String> processEnv = processBuilder.environment();
        processEnv.putAll(this.env);

        try {
            this.process = processBuilder.start();

            // Set up streams with proper error handling
            try {
                setupStreams();
                setupStderrMonitoring();
                this.connected = true;
                System.out.println("StdioMcpTransport: Successfully connected to command: " + String.join(" ", commandAndArgs));
            } catch (Exception streamException) {
                // Clean up process if stream setup fails
                cleanupProcess();
                throw streamException;
            }

        } catch (IOException e) {
            // Ensure no partial state if process creation fails
            cleanupProcess();
            throw new McpException("Failed to start or connect to MCP process '" + command + "': " + e.getMessage(), e);
        }
    }

    private void setupStreams() throws IOException {
        this.processStdin = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
        this.processStdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.processStderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
    }

    private void setupStderrMonitoring() {
        this.stderrConsumerThread = new Thread(() -> {
            try (BufferedReader reader = this.processStderrReader) {
                String line;
                while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    System.err.println("MCP_SERVER_STDERR: " + line);
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    System.err.println("StdioMcpTransport: Error reading from MCP server stderr: " + e.getMessage());
                }
            }
        });
        this.stderrConsumerThread.setDaemon(true);
        this.stderrConsumerThread.setName("mcp-stderr-consumer-" + command);
        this.stderrConsumerThread.start();
    }

    @Override
    public synchronized void send(Map<String, Object> data) throws McpException {
        if (!isConnected()) { // isConnected() checks process.isAlive()
            throw new McpException("Cannot send data: Not connected to MCP process or process has terminated.");
        }

        try {
            String jsonRequest = objectMapper.writeValueAsString(data);
            processStdin.println(jsonRequest); // println adds the necessary newline
            // processStdin.flush(); // Not strictly needed due to autoFlush=true on PrintWriter
            // Check if process died after sending
            if (!process.isAlive()) {
                connected = false;
                throw new McpException("MCP process terminated unexpectedly during send operation");
            }

        } catch (JsonProcessingException e) {
            throw new McpException("Failed to serialize data to JSON for MCP send operation: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized Map<String, Object> receive() throws McpException {
        if (!isConnected() && !canStillReadFromStdout()) {
            // If not connected (process dead) AND no buffered data in stdout, then it's an error.
            throw new McpException("Cannot receive data: Not connected to MCP process or process has terminated with no further output.");
        }

        try {
            // Add timeout to prevent infinite blocking
            String line = readLineWithTimeout(processStdout, 30000); // 30 second timeout

            if (line == null) {
                // End of stream reached, implies process terminated or stdout closed.
                connected = false;
                throw new McpException("MCP process stdout stream ended unexpectedly (process likely terminated).");
            }

            return objectMapper.readValue(line, new TypeReference<Map<String, Object>>() {});

        } catch (JsonProcessingException e) {
            throw new McpException("Failed to read or deserialize data from MCP process stdout: " + e.getMessage(), e);
        } catch (IOException e) {
            // If an IOException occurs, check if it's because we disconnected.
            connected = false;
            throw new McpException("MCP process stream closed (due to disconnect or termination): " + e.getMessage(), e);
        }
    }

    /**
     * Helper to check if stdout might still have buffered data even if process is marked not alive.
     */
    private boolean canStillReadFromStdout() {
        try {
            // processStdout.ready() can be unreliable for determining if readLine() will succeed.
            // A more robust check might involve peeking, but that's complex with BufferedReader.
            // For now, this is a basic check. If process is dead, readLine() returning null is more definitive.
            return processStdout != null && processStdout.ready();
        } catch (IOException e) {
            return false; // Assume not readable if ready() throws
        }
    }

    public synchronized void disconnect() throws McpException {
        if (!connected && process == null) {
            return; // Already disconnected
        }

        boolean wasConnected = connected;
        connected = false;

        List<Exception> cleanupExceptions = new ArrayList<>();

        // 1. Close streams first to signal process to terminate
        closeStreams(cleanupExceptions);

        // 2. Stop stderr monitoring thread
        stopStderrThread(cleanupExceptions);

        // 3. Terminate process with escalating force
        terminateProcess(cleanupExceptions);

        // 4. Reset state
        resetState();

        // 5. Report any cleanup issues (but don't fail the disconnect)
        if (!cleanupExceptions.isEmpty()) {
            System.err.println("StdioMcpTransport: " + cleanupExceptions.size() + " issues during cleanup:");
            cleanupExceptions.forEach(e -> System.err.println("  - " + e.getMessage()));
        }

        if (wasConnected) {
            System.out.println("StdioMcpTransport: Disconnected from command: " + command);
        }
    }

    private String readLineWithTimeout(BufferedReader reader, long timeoutMs) throws IOException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (reader.ready()) {
                return reader.readLine();
            }
            try {
                Thread.sleep(10); // Small sleep to prevent busy waiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for input", e);
            }

            // Check if process died while waiting
            if (process != null && !process.isAlive()) {
                return reader.readLine(); // Try one last read
            }
        }
        throw new IOException("Timeout waiting for input from MCP process");
    }

    private void closeStreams(List<Exception> exceptions) {
        // Close stdin first to signal process
        if (processStdin != null) {
            try {
                processStdin.close();
                if (processStdin.checkError()) {
                    exceptions.add(new IOException("Error state detected on process stdin after close"));
                }
            } catch (Exception e) {
                exceptions.add(e);
            }
            processStdin = null;
        }

        // Close stdout
        if (processStdout != null) {
            try {
                processStdout.close();
            } catch (Exception e) {
                exceptions.add(e);
            }
            processStdout = null;
        }

        // Note: processStderrReader will be closed by stderr thread
    }

    private void stopStderrThread(List<Exception> exceptions) {
        if (stderrConsumerThread != null && stderrConsumerThread.isAlive()) {
            stderrConsumerThread.interrupt();
            try {
                stderrConsumerThread.join(2000); // 2 second timeout
                if (stderrConsumerThread.isAlive()) {
                    exceptions.add(new RuntimeException("Stderr consumer thread did not stop within timeout"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exceptions.add(new RuntimeException("Interrupted while waiting for stderr thread to stop"));
            }
            stderrConsumerThread = null;
        }
    }

    private void terminateProcess(List<Exception> exceptions) {
        if (process == null) {
            return;
        }

        try {
            if (process.isAlive()) {
                // Step 1: Graceful termination (give process 3 seconds)
                process.destroy();
                if (process.waitFor(3, TimeUnit.SECONDS)) {
                    return; // Process terminated gracefully
                }

                // Step 2: Force termination (give process 2 more seconds)
                System.err.println("StdioMcpTransport: Process did not terminate gracefully, forcing destroy");
                process.destroyForcibly();
                if (process.waitFor(2, TimeUnit.SECONDS)) {
                    return; // Process terminated forcefully
                }

                // Step 3: Log if process still won't die
                exceptions.add(new RuntimeException("Process did not terminate after destroyForcibly within timeout"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Last resort - force kill even if interrupted
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            exceptions.add(new RuntimeException("Interrupted while waiting for process termination"));
        } catch (Exception e) {
            exceptions.add(e);
        }
    }

    private void resetState() {
        process = null;
        processStderrReader = null;
    }

    private void cleanupProcess() {
        try {
            disconnect();
        } catch (Exception e) {
            // Emergency cleanup - don't let cleanup exceptions mask original issues
            System.err.println("StdioMcpTransport: Emergency cleanup failed: " + e.getMessage());

            // Force cleanup without exceptions
            connected = false;

            if (stderrConsumerThread != null) {
                stderrConsumerThread.interrupt();
            }

            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }

            // Reset all references
            processStdin = null;
            processStdout = null;
            processStderrReader = null;
            stderrConsumerThread = null;
            process = null;
        }
    }

    @Override
    public boolean isConnected() {
        // Process being non-null and alive is the primary indicator.
        // `connected` flag handles cases where disconnect has started but process termination is pending.
        return connected && process != null && process.isAlive();
    }

    @Override
    public void close() throws Exception {
        disconnect();
    }

    /**
     * Finalizer to attempt resource cleanup if {@link #disconnect()} was not explicitly called.
     * This is a fallback and explicit cleanup always preferred.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (connected || (process != null && process.isAlive())) {
                System.err.println("StdioMcpTransport.finalize(): Transport for command '" + command +
                        "' was not properly closed. Performing emergency cleanup.");
                cleanupProcess();
            }
        } finally {
            super.finalize();
        }
    }
}
