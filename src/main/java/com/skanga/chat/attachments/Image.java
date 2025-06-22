package com.skanga.chat.attachments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skanga.chat.enums.AttachmentContentType;
import com.skanga.chat.enums.AttachmentType;

/**
 * Represents an image attachment, such as a PNG, JPEG, or GIF file.
 * This class extends {@link Attachment}, automatically setting the {@link AttachmentType} to {@code IMAGE}.
 * The specific image format (e.g., "image/png") should be specified in the `mediaType` field.
 */
public class Image extends Attachment {

    /**
     * Constructs a new Image attachment.
     * The {@link AttachmentType} is automatically set to {@link AttachmentType#IMAGE}.
     *
     * @param content     The image data, typically a URL or a Base64 encoded string.
     *                    Base64 is common for directly embedding images in API requests.
     * @param contentType Specifies how the `content` should be interpreted (e.g., URL, BASE64).
     * @param mediaType   The MIME type of the image (e.g., "image/png", "image/jpeg").
     */
    @JsonCreator // Specifies this constructor for Jackson deserialization if type information is available
    public Image(
            @JsonProperty("content") String content,
            @JsonProperty("contentType") AttachmentContentType contentType,
            @JsonProperty("mediaType") String mediaType) {
        super(AttachmentType.IMAGE, content, contentType, mediaType);
    }
}
