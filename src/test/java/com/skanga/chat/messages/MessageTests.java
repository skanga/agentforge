package com.skanga.chat.messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skanga.chat.attachments.Attachment;
import com.skanga.chat.attachments.Image;
import com.skanga.chat.enums.AttachmentContentType;
import com.skanga.chat.enums.AttachmentType;
import com.skanga.chat.enums.MessageRole;
import com.skanga.core.Usage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void constructor_withRoleAndContent_initializesCorrectly() {
        MessageRole role = MessageRole.USER;
        String content = "Hello, world!";
        Message message = new Message(role, content);

        assertEquals(role, message.getRole());
        assertEquals(content, message.getContent());
        assertNull(message.getUsage());
        assertNotNull(message.getAttachments());
        assertTrue(message.getAttachments().isEmpty());
        assertNotNull(message.getMeta());
        assertTrue(message.getMeta().isEmpty());
    }

    @Test
    void constructor_withRoleContentAndUsage_initializesCorrectly() {
        MessageRole role = MessageRole.ASSISTANT;
        String content = "I'm here to help.";
        Usage usage = new Usage(10, 20, 30);
        Message message = new Message(role, content, usage);

        assertEquals(role, message.getRole());
        assertEquals(content, message.getContent());
        assertEquals(usage, message.getUsage());
    }

    @Test
    void defaultConstructor_initializesEmptyMessage() {
        Message message = new Message();
        assertNull(message.getRole()); // Role is not set by default constructor
        assertNull(message.getContent());
        assertNull(message.getUsage());
        assertNotNull(message.getAttachments());
        assertTrue(message.getAttachments().isEmpty());
        assertNotNull(message.getMeta());
        assertTrue(message.getMeta().isEmpty());
    }


    @Test
    void setters_updateFieldsCorrectly() {
        Message message = new Message();

        message.setRole(MessageRole.SYSTEM);
        assertEquals(MessageRole.SYSTEM, message.getRole());

        String content = "System instruction.";
        message.setContent(content);
        assertEquals(content, message.getContent());

        Usage usage = new Usage(5, 5, 10);
        message.setUsage(usage);
        assertEquals(usage, message.getUsage());

        List<Attachment> attachments = new ArrayList<>();
        attachments.add(new Image("base64data", AttachmentContentType.BASE64, "image/png"));
        message.setAttachments(attachments);
        assertEquals(attachments, message.getAttachments());
        assertEquals(1, message.getAttachments().size());

        Map<String, Object> meta = new HashMap<>();
        meta.put("key1", "value1");
        message.setMeta(meta);
        assertEquals(meta, message.getMeta());
        assertEquals("value1", message.getMeta().get("key1"));
    }

    @Test
    void addAttachment_addsToAttachmentsList() {
        Message message = new Message(MessageRole.USER, "Check this out.");
        Attachment attachment1 = new Image("img1", AttachmentContentType.BASE64, "image/jpeg");
        message.addAttachment(attachment1);
        assertEquals(1, message.getAttachments().size());
        assertTrue(message.getAttachments().contains(attachment1));

        Attachment attachment2 = new com.skanga.chat.attachments.Document("doc1", AttachmentContentType.URL, "application/pdf");
        message.addAttachment(attachment2);
        assertEquals(2, message.getAttachments().size());
        assertTrue(message.getAttachments().contains(attachment2));
    }

    @Test
    void addAttachment_nullAttachment_throwsNullPointerException() {
        Message message = new Message(MessageRole.USER, "Test");
        assertThrows(NullPointerException.class, () -> message.addAttachment(null));
    }


    @Test
    void addMetadata_addsToMetaMap() {
        Message message = new Message(MessageRole.ASSISTANT, "Response.");
        message.addMetadata("timestamp", "2023-01-01T12:00:00Z");
        assertEquals("2023-01-01T12:00:00Z", message.getMeta().get("timestamp"));

        message.addMetadata("source_confidence", 0.95);
        assertEquals(0.95, message.getMeta().get("source_confidence"));

        // Overwrite existing key
        message.addMetadata("timestamp", "2023-01-02T10:00:00Z");
        assertEquals("2023-01-02T10:00:00Z", message.getMeta().get("timestamp"));
    }

    @Test
    void addMetadata_nullKey_throwsNullPointerException() {
        Message message = new Message(MessageRole.USER, "Test");
        assertThrows(NullPointerException.class, () -> message.addMetadata(null, "value"));
    }


    @Test
    void jsonSerialization_withBasicFields_serializesCorrectly() throws Exception {
        Message message = new Message(MessageRole.USER, "Hello");
        String json = objectMapper.writeValueAsString(message);

        assertTrue(json.contains("\"role\":\"USER\""));
        assertTrue(json.contains("\"content\":\"Hello\""));
        assertFalse(json.contains("\"usage\"")); // Null fields are not included by default
        // Removed: assertFalse(json.contains("\"attachments\""));
        // Based on NON_NULL, empty attachments list should be serialized as "attachments":[]
        assertTrue(json.contains("\"attachments\":[]"));
        assertTrue(json.contains("\"meta\":{}"));
    }

    @Test
    void jsonSerialization_withAllFields_serializesCorrectly() throws Exception {
        Message message = new Message(MessageRole.ASSISTANT, "Response details");
        message.setUsage(new Usage(10, 20, 30));
        message.addAttachment(new Image("base64data", AttachmentContentType.BASE64, "image/png"));
        message.addMetadata("source", "model_v2");

        String json = objectMapper.writeValueAsString(message);
        // System.out.println("Serialized JSON for jsonSerialization_withAllFields_serializesCorrectly: " + json); // Debug line removed

        assertTrue(json.contains("\"role\":\"ASSISTANT\""));
        assertTrue(json.contains("\"content\":\"Response details\""));
        assertTrue(json.contains("\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}"));
        assertTrue(json.contains("\"attachments\":[{\"content\":\"base64data\",\"contentType\":\"BASE64\",\"mediaType\":\"image/png\",\"type\":\"IMAGE\"}]")); // Adjusted field order
        assertTrue(json.contains("\"meta\":{\"source\":\"model_v2\"}"));
    }

    @Test
    void jsonDeserialization_toMessage_deserializesCorrectly() throws Exception {
        String json = "{\"role\":\"USER\",\"content\":\"Hello from JSON\",\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":10,\"total_tokens\":15},\"attachments\":[{\"type\":\"IMAGE\",\"content\":\"base64img\",\"contentType\":\"BASE64\",\"mediaType\":\"image/jpeg\"}],\"meta\":{\"id\":\"msg123\"}}";
        Message message = objectMapper.readValue(json, Message.class);

        assertEquals(MessageRole.USER, message.getRole());
        assertEquals("Hello from JSON", message.getContent());
        assertNotNull(message.getUsage());
        assertEquals(5, message.getUsage().promptTokens());
        assertNotNull(message.getAttachments());
        assertEquals(1, message.getAttachments().size());
        Attachment attachment = message.getAttachments().get(0);
        // Note: Without polymorphic deserialization for Attachment, it will be a base Attachment.
        // To get Image.class, @JsonTypeInfo would be needed on Attachment interface/base class.
        assertEquals(AttachmentType.IMAGE, attachment.getType());
        assertEquals("base64img", attachment.getContent());
        assertNotNull(message.getMeta());
        assertEquals("msg123", message.getMeta().get("id"));
    }

    @Test
    void equalsAndHashCode_verifyContract() {
        Message msg1 = new Message(MessageRole.USER, "Hello");
        msg1.setUsage(new Usage(1,2,3));
        msg1.addMetadata("key", "value");

        Message msg2 = new Message(MessageRole.USER, "Hello");
        msg2.setUsage(new Usage(1,2,3));
        msg2.addMetadata("key", "value");

        Message msg3 = new Message(MessageRole.ASSISTANT, "Hello"); // Different role
        Message msg4 = new Message(MessageRole.USER, "Hi");      // Different content

        assertEquals(msg1, msg2);
        assertEquals(msg1.hashCode(), msg2.hashCode());

        assertNotEquals(msg1, msg3);
        assertNotEquals(msg1.hashCode(), msg3.hashCode()); // Hashcodes not guaranteed different but likely for these changes

        assertNotEquals(msg1, msg4);
    }
}
