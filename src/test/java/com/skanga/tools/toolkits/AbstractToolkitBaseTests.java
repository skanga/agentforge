package com.skanga.tools.toolkits;

import com.skanga.tools.BaseTool;
import com.skanga.tools.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AbstractToolkitBaseTests {

    static class TestToolAlpha extends BaseTool {
        public TestToolAlpha() { super("alphaTool", "Alpha tool description"); }
    }

    static class TestToolBeta extends BaseTool {
        public TestToolBeta() { super("betaTool", "Beta tool description"); }
    }

    static class TestToolGamma extends BaseTool {
        public TestToolGamma() { super("gammaTool", "Gamma tool description"); }
    }


    static class ConcreteToolkit extends AbstractToolkitBase {
        private List<Tool> providedTools = new ArrayList<>();

        public ConcreteToolkit(List<Tool> toolsToProvide) {
            if (toolsToProvide != null) {
                this.providedTools.addAll(toolsToProvide);
            }
        }

        public ConcreteToolkit() {
            this(null);
        }

        @Override
        public List<Tool> provideTools() {
            return providedTools; // Returns the internal list
        }

        public void setToolsToProvide(List<Tool> tools){
            this.providedTools = new ArrayList<>(tools);
        }

        @Override
        public String getGuidelines() {
            return "Use these tools wisely.";
        }
    }

    private ConcreteToolkit toolkit;
    private Tool toolAlpha;
    private Tool toolBeta;
    private Tool toolGamma;

    @BeforeEach
    void setUp() {
        toolAlpha = new TestToolAlpha();
        toolBeta = new TestToolBeta();
        toolGamma = new TestToolGamma();
        toolkit = new ConcreteToolkit(Arrays.asList(toolAlpha, toolBeta, toolGamma));
    }

    @Test
    void getTools_noExclusions_returnsAllProvidedTools() {
        List<Tool> tools = toolkit.getTools();
        assertEquals(3, tools.size());
        assertTrue(tools.contains(toolAlpha));
        assertTrue(tools.contains(toolBeta));
        assertTrue(tools.contains(toolGamma));
    }

    @Test
    void getTools_withExclusions_returnsFilteredTools() {
        toolkit.exclude(List.of(TestToolBeta.class.getName()));
        List<Tool> tools = toolkit.getTools();

        assertEquals(2, tools.size());
        assertTrue(tools.contains(toolAlpha));
        assertFalse(tools.contains(toolBeta)); // Should be excluded
        assertTrue(tools.contains(toolGamma));
    }

    @Test
    void getTools_excludeMultipleTools_returnsFilteredTools() {
        toolkit.exclude(Arrays.asList(TestToolAlpha.class.getName(), TestToolGamma.class.getName()));
        List<Tool> tools = toolkit.getTools();

        assertEquals(1, tools.size());
        assertFalse(tools.contains(toolAlpha));
        assertTrue(tools.contains(toolBeta));
        assertFalse(tools.contains(toolGamma));
    }

    @Test
    void getTools_excludeNonExistentClass_noChange() {
        toolkit.exclude(List.of("com.example.NonExistentTool"));
        List<Tool> tools = toolkit.getTools();
        assertEquals(3, tools.size());
    }

    @Test
    void getTools_nullToolInProvideTools_filtersOutNull() {
        List<Tool> toolsWithNull = new ArrayList<>();
        toolsWithNull.add(toolAlpha);
        toolsWithNull.add(null); // Add a null tool
        toolsWithNull.add(toolBeta);
        toolkit.setToolsToProvide(toolsWithNull);

        List<Tool> tools = toolkit.getTools();
        assertEquals(2, tools.size(), "Should filter out null tools from the provided list.");
        assertTrue(tools.contains(toolAlpha));
        assertTrue(tools.contains(toolBeta));
    }


    @Test
    void clearExclusions_removesAllExclusions() {
        toolkit.exclude(List.of(TestToolBeta.class.getName()));
        assertEquals(2, toolkit.getTools().size()); // Verify exclusion is active

        toolkit.clearExclusions();
        assertEquals(3, toolkit.getTools().size()); // Verify all tools are back
    }

    @Test
    void getGuidelines_returnsCorrectGuidelines() {
        assertEquals("Use these tools wisely.", toolkit.getGuidelines());
    }

    @Test
    void provideTools_returnsEmptyList_getToolsReturnsEmpty() {
        ConcreteToolkit emptyToolkit = new ConcreteToolkit(Collections.emptyList());
        assertTrue(emptyToolkit.getTools().isEmpty());
    }

    @Test
    void provideTools_returnsNull_getToolsReturnsEmpty() {
        ConcreteToolkit nullProvidingToolkit = new ConcreteToolkit(null); // Constructor handles null
        assertTrue(nullProvidingToolkit.getTools().isEmpty());
    }

    @Test
    void getTools_returnedListIsUnmodifiable() {
        List<Tool> tools = toolkit.getTools();
        assertThrows(UnsupportedOperationException.class, () -> {
            tools.add(new TestToolAlpha()); // Try to modify the returned list
        });
    }
}
