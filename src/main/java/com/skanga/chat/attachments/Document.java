package com.skanga.chat.attachments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skanga.chat.enums.AttachmentContentType;
import com.skanga.chat.enums.AttachmentType;

/**
 * Represents a document attachment, such as a PDF, TXT, or other document file.
 * This class extends {@link Attachment}, automatically setting the {@link AttachmentType} to {@code DOCUMENT}.
 * The actual document type (e.g., "application/pdf") should be specified in the `mediaType` field.
 */
public class Document extends Attachment {

    /**
     * Constructs a new Document attachment.
     * The {@link AttachmentType} is automatically set to {@link AttachmentType#DOCUMENT}.
     *
     * @param content     The document data, typically a URL or Base64 encoded string.
     *                    For large documents, URL is preferred.
     * @param contentType Specifies how the `content` should be interpreted (e.g., URL, BASE64).
     * @param mediaType   The MIME type of the document (e.g., "application/pdf", "text/plain").
     */
    @JsonCreator // Specifies this constructor for Jackson deserialization if type information is available
    public Document(
            @JsonProperty("content") String content,
            @JsonProperty("contentType") AttachmentContentType contentType,
            @JsonProperty("mediaType") String mediaType) {
        super(AttachmentType.DOCUMENT, content, contentType, mediaType);
    }
}
