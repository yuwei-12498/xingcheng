package com.citytrip.config;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.nio.charset.StandardCharsets;

@RestControllerAdvice
public class JsonUtf8ResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (selectedContentType != null && isJsonLike(selectedContentType)) {
            response.getHeaders().setContentType(
                    new MediaType(selectedContentType.getType(), selectedContentType.getSubtype(), StandardCharsets.UTF_8)
            );
        }
        return body;
    }

    private boolean isJsonLike(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        if (MediaType.APPLICATION_JSON.includes(mediaType)) {
            return true;
        }
        String subtype = mediaType.getSubtype();
        return subtype != null && subtype.endsWith("+json");
    }
}
