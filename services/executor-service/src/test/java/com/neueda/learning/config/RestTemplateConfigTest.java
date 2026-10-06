package com.neueda.learning.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

class RestTemplateConfigTest {

    @Test
    void interceptorAddsApiKeyHeaderWhenConfigured() throws IOException {
        RestTemplateConfig config = new RestTemplateConfig();
        ReflectionTestUtils.setField(config, "apiKey", "test-api-key");

        ClientHttpRequestInterceptor interceptor = firstInterceptor(config);

        HttpRequest request = mock(HttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse expectedResponse = mock(ClientHttpResponse.class);
        when(execution.execute(request, new byte[0])).thenReturn(expectedResponse);

        ClientHttpResponse response = interceptor.intercept(request, new byte[0], execution);

        assertEquals("test-api-key", headers.getFirst("x-api-key"));
        assertEquals(expectedResponse, response);
    }

    @Test
    void interceptorSkipsHeaderWhenApiKeyIsEmpty() throws IOException {
        RestTemplateConfig config = new RestTemplateConfig();
        ReflectionTestUtils.setField(config, "apiKey", "");

        ClientHttpRequestInterceptor interceptor = firstInterceptor(config);

        HttpRequest request = mock(HttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse expectedResponse = mock(ClientHttpResponse.class);
        when(execution.execute(request, new byte[0])).thenReturn(expectedResponse);

        interceptor.intercept(request, new byte[0], execution);

        assertFalse(headers.containsKey("x-api-key"));
    }

    private static ClientHttpRequestInterceptor firstInterceptor(RestTemplateConfig config) {
        RestTemplate restTemplate = config.restTemplate(new RestTemplateBuilder());
        return restTemplate.getInterceptors().get(0);
    }
}
