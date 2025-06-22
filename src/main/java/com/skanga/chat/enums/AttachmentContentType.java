package com.skanga.chat.enums;

/**
 * Specifies the content type of an {@link com.skanga.chat.attachments.Attachment}.
 * This helps in interpreting the `content` field of an attachment.
 */
public enum AttachmentContentType {
    /**
     * Indicates the attachment content is a URL pointing to the actual data.
     */
    URL,

    /**
     * Indicates the attachment content is a Base64 encoded string of the data.
     * This is commonly used for embedding images directly in requests.
     */
    BASE64;
}
