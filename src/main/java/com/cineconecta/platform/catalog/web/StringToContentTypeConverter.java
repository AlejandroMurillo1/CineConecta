package com.cineconecta.platform.catalog.web;

import com.cineconecta.platform.catalog.ContentType;
import org.springframework.stereotype.Component;
import org.springframework.core.convert.converter.Converter;

import java.util.Locale;

@Component
public class StringToContentTypeConverter implements Converter<String, ContentType> {

    @Override
    public ContentType convert(String source) {
        String text = source.strip();
        if (text.isEmpty()) {
            return null;
        }
        return ContentType.valueOf(text.toUpperCase(Locale.ROOT));
    }
}
