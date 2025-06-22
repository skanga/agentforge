package com.skanga.chat.messages;

import com.skanga.chat.enums.MessageRole;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserMessageTests {

    @Test
    void constructor_withContent_setsRoleToUser() {
        String content = "This is a user message.";
        UserMessage userMessage = new UserMessage(content);

        assertEquals(MessageRole.USER, userMessage.getRole());
        assertEquals(content, userMessage.getContent());
    }

    @Test
    void defaultConstructor_setsRoleToUser() {
        UserMessage userMessage = new UserMessage();
        // The default constructor of UserMessage explicitly sets the role.
        assertEquals(MessageRole.USER, userMessage.getRole());
        assertNull(userMessage.getContent()); // Content would be null by default
    }

    @Test
    void inheritedMethods_workAsExpected() {
        UserMessage userMessage = new UserMessage("Initial content");

        // Test setContent (inherited but good to check in context)
        userMessage.setContent("Updated content");
        assertEquals("Updated content", userMessage.getContent());

        // Role should remain USER even if setContent is called
        assertEquals(MessageRole.USER, userMessage.getRole());

        // Test addMetadata
        userMessage.addMetadata("source", "test_case");
        assertEquals("test_case", userMessage.getMeta().get("source"));
    }
}
