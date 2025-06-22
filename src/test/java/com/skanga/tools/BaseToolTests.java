
package com.skanga.tools;

import com.skanga.tools.exceptions.MissingToolParameterException;
import com.skanga.tools.exceptions.ToolCallableException;
import com.skanga.tools.properties.PropertyType;
import com.skanga.tools.properties.ToolProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BaseToolTests {

    @Mock
    private ToolCallable toolCallable;

    private BaseTool tool;

    @BeforeEach
    void setUp() {
        tool = new BaseTool("test_tool", "A test tool for unit testing");
    }

    @Test
    void constructor_WithValidParameters_ShouldCreateTool() {
        // Act & Assert
        assertThat(tool).isNotNull();
        assertThat(tool.getName()).isEqualTo("test_tool");
        assertThat(tool.getDescription()).isEqualTo("A test tool for unit testing");
        assertThat(tool.getParameters()).isEmpty();
        assertThat(tool.getInputs()).isEmpty();
    }

    @Test
    void constructor_WithNullName_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new BaseTool(null, "Description"));
    }

    @Test
    void constructor_WithEmptyName_ShouldThrowException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                new BaseTool("", "Description"));
    }

    @Test
    void constructor_WithWhitespaceName_ShouldThrowException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                new BaseTool("   ", "Description"));
    }

    @Test
    void constructor_WithNullDescription_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new BaseTool("test_tool", null));
    }

    @Test
    void addParameter_WithValidProperty_ShouldAddParameter() {
        // Arrange
        ToolProperty property = mock(ToolProperty.class);
        when(property.getName()).thenReturn("test_param");
        when(property.isRequired()).thenReturn(true);

        // Act
        BaseTool result = tool.addParameter(property);

        // Assert
        assertThat(result).isSameAs(tool);
        assertThat(tool.getParameters()).contains(property);
        assertThat(tool.getRequiredParameters()).contains("test_param");
    }

    @Test
    void addParameter_WithNullProperty_ShouldThrowException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                tool.addParameter((ToolProperty) null));
    }

    @Test
    void addParameter_WithSimpleParameters_ShouldCreateProperty() {
        // Act
        BaseTool result = tool.addParameter("param1", PropertyType.STRING, "Description", true);

        // Assert
        assertThat(result).isSameAs(tool);
        assertThat(tool.getParameters()).hasSize(1);
        assertThat(tool.getParameters().get(0).getName()).isEqualTo("param1");
        assertThat(tool.getParameters().get(0).getPropertyType()).isEqualTo(PropertyType.STRING);
        assertThat(tool.getRequiredParameters()).contains("param1");
    }

    @Test
    void setCallable_WithValidCallable_ShouldSetCallable() {
        // Act
        tool.setCallable(toolCallable);

        // Assert
        assertThat(tool.getCallable()).isSameAs(toolCallable);
    }

    @Test
    void setInputs_WithValidInputs_ShouldSetInputs() {
        // Arrange
        Map<String, Object> inputs = Map.of(
                "param1", "value1",
                "param2", 42
        );

        // Act
        Tool result = tool.setInputs(inputs);

        // Assert
        assertThat(result).isSameAs(tool);
        assertThat(tool.getInputs()).isEqualTo(inputs);
        assertThat(tool.getInputs()).isNotSameAs(inputs); // Should be a copy
    }

    @Test
    void setInputs_WithNullInputs_ShouldSetEmptyMap() {
        // Act
        tool.setInputs(null);

        // Assert
        assertThat(tool.getInputs()).isEmpty();
    }

    @Test
    void setCallId_WithValidId_ShouldSetCallId() {
        // Arrange
        String callId = "call-123";

        // Act
        Tool result = tool.setCallId(callId);

        // Assert
        assertThat(result).isSameAs(tool);
        assertThat(tool.getCallId()).isEqualTo(callId);
    }

    @Test
    void executeCallable_WithValidSetup_ShouldExecuteAndSetResult() throws Exception {
        // Arrange
        tool.addParameter("param1", PropertyType.STRING, "Test param", true);
        tool.setInputs(Map.of("param1", "test_value"));
        tool.setCallable(toolCallable);

        ToolExecutionResult expectedResult = new ToolExecutionResult("Execution successful");
        when(toolCallable.execute(any(ToolExecutionInput.class))).thenReturn(expectedResult);

        // Act
        tool.executeCallable();

        // Assert
        assertThat(tool.getResult()).isEqualTo("Execution successful");
        verify(toolCallable).execute(argThat(input ->
                "test_value".equals(input.getArgument("param1"))));
    }

    @Test
    void executeCallable_WithoutCallable_ShouldThrowException() {
        // Act & Assert
        ToolCallableException exception = assertThrows(ToolCallableException.class, () ->
                tool.executeCallable());
        assertThat(exception.getMessage()).contains("No callable has been set");
    }

    @Test
    void executeCallable_WithCallableException_ShouldWrapException() throws Exception {
        // Arrange
        tool.setCallable(toolCallable);
        when(toolCallable.execute(any())).thenThrow(new RuntimeException("Callable failed"));

        // Act & Assert
        ToolCallableException exception = assertThrows(ToolCallableException.class, () ->
                tool.executeCallable());
        assertThat(exception.getMessage()).contains("Error executing tool 'test_tool'");
        assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void getJsonSchema_WithoutParameters_ShouldReturnBasicSchema() {
        // Act
        Map<String, Object> schema = tool.getJsonSchema();

        // Assert
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(schema).doesNotContainKey("properties");
        assertThat(schema).doesNotContainKey("required");
    }

    @Test
    void getJsonSchema_WithParameters_ShouldReturnFullSchema() {
        // Arrange
        tool.addParameter("param1", PropertyType.STRING, "String parameter", true);
        tool.addParameter("param2", PropertyType.INTEGER, "Integer parameter", false);

        // Act
        Map<String, Object> schema = tool.getJsonSchema();

        // Assert
        assertThat(schema.get("type")).isEqualTo("object");

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).hasSize(2);
        assertThat(properties).containsKey("param1");
        assertThat(properties).containsKey("param2");

        List<String> required = (List<String>) schema.get("required");
        assertThat(required).containsExactly("param1");
    }

    @Test
    void toJsonSerializableMap_ShouldReturnSerializableMap() {
        // Arrange
        tool.addParameter("test_param", PropertyType.STRING, "Test parameter", true);

        // Act
        Map<String, Object> map = tool.toJsonSerializableMap();

        // Assert
        assertThat(map.get("name")).isEqualTo("test_tool");
        assertThat(map.get("description")).isEqualTo("A test tool for unit testing");
        assertThat(map).containsKey("parameters_schema");
    }

    @Test
    void getRequiredParameters_WithMixedParameters_ShouldReturnOnlyRequired() {
        // Arrange
        tool.addParameter("required1", PropertyType.STRING, "Required param 1", true);
        tool.addParameter("optional1", PropertyType.STRING, "Optional param 1", false);
        tool.addParameter("required2", PropertyType.INTEGER, "Required param 2", true);

        // Act
        List<String> requiredParams = tool.getRequiredParameters();

        // Assert
        assertThat(requiredParams).containsExactlyInAnyOrder("required1", "required2");
    }

    @Test
    void getParameters_ShouldReturnUnmodifiableList() {
        // Arrange
        tool.addParameter("param1", PropertyType.STRING, "Test param", false);

        // Act
        List<ToolProperty> parameters = tool.getParameters();

        // Assert
        assertThrows(UnsupportedOperationException.class, () ->
                parameters.add(mock(ToolProperty.class)));
    }

    @Test
    void getRequiredParameters_returnsCorrectly() {
        tool.addParameter("p1", PropertyType.STRING, "d1", true);
        tool.addParameter("p2", PropertyType.INTEGER, "d2", false);
        tool.addParameter("p3", PropertyType.BOOLEAN, "d3", true);

        List<String> required = tool.getRequiredParameters();
        assertEquals(2, required.size());
        assertTrue(required.contains("p1"));
        assertTrue(required.contains("p3"));
        assertFalse(required.contains("p2"));
    }

    @Test
    void getInputs_ShouldReturnUnmodifiableMap() {
        // Arrange
        tool.setInputs(Map.of("key", "value"));

        // Act
        Map<String, Object> inputs = tool.getInputs();

        // Assert
        assertThrows(UnsupportedOperationException.class, () ->
                inputs.put("newKey", "newValue"));
    }


    @Test
    void executeCallable_missingRequiredParameter_throwsMissingToolParameterException() {
        tool.addParameter("reqParam", PropertyType.STRING, "Required", true);
        tool.setCallable(toolCallable);
        // Inputs map is empty, "reqParam" is missing

        MissingToolParameterException ex = assertThrows(MissingToolParameterException.class, () -> tool.executeCallable());
        assertEquals("reqParam", ex.getParameterName());
        assertEquals(tool.getName(), ex.getToolName());
    }

    @Test
    void executeCallable_callableThrowsException_wrapsInToolCallableExceptionAndClearsResult() throws Exception {
        tool.setCallable(toolCallable);
        tool.setInputs(Collections.emptyMap()); // Assume no required params for this test

        // Simulate result from a previous successful call
        tool.result = "previous_result";

        RuntimeException cause = new RuntimeException("Callable failed!");
        when(toolCallable.execute(any(ToolExecutionInput.class))).thenThrow(cause);

        ToolCallableException ex = assertThrows(ToolCallableException.class, () -> tool.executeCallable());
        assertEquals(cause, ex.getCause());
        assertTrue(ex.getMessage().contains("Error executing tool 'testTool'"));
        assertNull(tool.getResult(), "Result should be cleared on execution failure.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getJsonSchema_returnsCorrectSchemaForParameters() {
        tool.addParameter("name", PropertyType.STRING, "The name", true);
        tool.addParameter("count", PropertyType.INTEGER, "A number", false);

        Map<String, Object> schema = tool.getJsonSchema();
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertEquals(2, properties.size());

        Map<String, Object> namePropSchema = (Map<String, Object>) properties.get("name");
        assertEquals("string", namePropSchema.get("type"));
        assertEquals("The name", namePropSchema.get("description"));

        Map<String, Object> countPropSchema = (Map<String, Object>) properties.get("count");
        assertEquals("integer", countPropSchema.get("type"));

        assertTrue(schema.containsKey("required"));
        List<String> requiredList = (List<String>) schema.get("required");
        assertEquals(1, requiredList.size());
        assertTrue(requiredList.contains("name"));
    }

    @Test
    void toJsonSerializableMap_returnsCorrectMap() {
        tool.addParameter("param", PropertyType.STRING, "A param", true);
        Map<String, Object> map = tool.toJsonSerializableMap();

        assertEquals("testTool", map.get("name"));
        assertEquals("A tool for testing.", map.get("description"));
        assertTrue(map.containsKey("parameters_schema"));

        @SuppressWarnings("unchecked")
        Map<String, Object> paramsSchema = (Map<String, Object>) map.get("parameters_schema");
        assertEquals("object", paramsSchema.get("type"));
        assertTrue(paramsSchema.containsKey("properties"));
    }
}
