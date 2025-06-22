package com.skanga.chat.enums;

/**
 * Specifies the general type or category of an {@link com.skanga.chat.attachments.Attachment}.
 * This helps in understanding the nature of the attachment.
 */
public enum AttachmentType {
    /**
     * Represents a document attachment (e.g., PDF, TXT, DOCX).
     * The specific format might be indicated by {@link com.skanga.chat.attachments.Attachment#getMediaType()}.
     */
    DOCUMENT,

    /**
     * Represents an image attachment (e.g., PNG, JPEG, GIF).
     * The specific format is typically indicated by {@link com.skanga.chat.attachments.Attachment#getMediaType()}.
     */
    IMAGE;

    // Other types like AUDIO, VIDEO could be added if needed.
}
