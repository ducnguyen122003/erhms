package com.erhms.admin.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;

public record AdminAccountStatusInput(
        @NotNull(message = "Chọn trạng thái hoạt động của tài khoản")
        @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean accountEnabled) {

    public static final class StrictBooleanDeserializer extends JsonDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_TRUE)) return true;
            if (parser.hasToken(JsonToken.VALUE_FALSE)) return false;
            return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
        }
    }
}
