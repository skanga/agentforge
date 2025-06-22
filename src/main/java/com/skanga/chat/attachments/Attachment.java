package com.skanga.chat.attachments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skanga.chat.enums.AttachmentContentType;
import com.skanga.chat.enums.AttachmentType;
import java.util.Objects;

/**
 * Represents an attachment to a {@link com.skanga.chat.messages.Message}.
 * An attachment has a general type (e.g., IMAGE, DOCUMENT), content (URL or Base64 data),
 * a content type specifying how to interpret the content, and a media type (MIME type).
 *
 * This base class is Jackson-friendly for serialization. For deserialization into
 * specific subclasses like {@link Image} or {@link Document}, Jackson's polymorphic
 * type handling (e.g., using {@code @JsonTypeInfo} and {@code @JsonSubTypes}) might be
 * needed if deserializing a list of generic Attachments. For now, subclasses use
 * {@code @JsonCreator} for direct instantiation if their type is known.
 */
// Example for polymorphic deserialization if needed later:
// @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "attachment_class_type_hint")
// @JsonSubTypes({
//    @JsonSubTypes.Type(value = Image.class, name = "Image"),
//    @JsonSubTypes.Type(value = Document.class, name = "Document")
// })
public class Attachment {
    /** The general category of the attachment (e.g., IMAGE, DOCUMENT). */
    @JsonProperty("type")
    protected final AttachmentType type;

    /**
     * The actual content of the attachment.
     * This could be a URL string or a Base64 encoded data string,
     * distinguished by the {@link #contentType}.
     */
    @JsonProperty("content")
    protected final String content;

    /**
     * Specifies how the {@link #content} field should be interpreted (e.g., URL or BASE64).
     */
    @JsonProperty("contentType")
    protected final AttachmentContentType contentType;

    /**
     * The MIME type of the attachment's data (e.g., "application/pdf", "image/jpeg").
     */
    @JsonProperty("mediaType")
    protected final String mediaType;

    /**
     * Constructs an Attachment.
     *
     * @param type        The general category of the attachment.
     * @param content     The attachment data (URL or Base64 string).
     * @param contentType How the `content` should be interpreted.
     * @param mediaType   The MIME type of the attachment data.
     */
    @JsonCreator // Helps Jackson pick this constructor
    public Attachment(
            @JsonProperty("type") AttachmentType type,
            @JsonProperty("content") String content,
            @JsonProperty("contentType") AttachmentContentType contentType,
            @JsonProperty("mediaType") String mediaType) {
        this.type = Objects.requireNonNull(type, "Attachment type cannot be null.");
        this.content = Objects.requireNonNull(content, "Attachment content cannot be null.");
        this.contentType = Objects.requireNonNull(contentType, "AttachmentContentType cannot be null.");
        this.mediaType = Objects.requireNonNull(mediaType, "Attachment mediaType cannot be null.");
    }

    /**
     * Gets the general category of this attachment.
     * @return The {@link AttachmentType}.
     */
    public AttachmentType getType() {
        return type;
    }

    /**
     * Gets the content of this attachment (e.g., URL or Base64 data).
     * @return The content string.
     */
    public String getContent() {
        return content;
    }

    /**
     * Gets how the content string should be interpreted.
     * @return The {@link AttachmentContentType}.
     */
    public AttachmentContentType getContentType() {
        return contentType;
    }

    /**
     * Gets the MIME type of this attachment's data.
     * @return The media type string (e.g., "image/png").
     */
    public String getMediaType() {
        return mediaType;
    }

    @Override
    public String toString() {
        return "Attachment{" +
                "type=" + type +
                ", contentType=" + contentType +
                ", mediaType='" + mediaType + '\'' +
                ", content_length=" + (content != null ? content.length() : "null") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attachment that = (Attachment) o;
        return type == that.type &&
               contentType == that.contentType &&
               Objects.equals(content, that.content) &&
               Objects.equals(mediaType, that.mediaType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, content, contentType, mediaType);
    }
}
