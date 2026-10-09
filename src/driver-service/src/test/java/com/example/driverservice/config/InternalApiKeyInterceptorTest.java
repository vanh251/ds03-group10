package com.example.driverservice.config;

import com.example.driverservice.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class InternalApiKeyInterceptorTest {

    private final InternalApiKeyInterceptor interceptor = new InternalApiKeyInterceptor("secret-key");

    private MockHttpServletRequest requestWithKey(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/drivers");
        if (key != null) {
            request.addHeader(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, key);
        }
        return request;
    }

    @Test
    @DisplayName("preHandle with the configured key lets the request through")
    void preHandle_correctKey_returnsTrue() {
        assertTrue(interceptor.preHandle(requestWithKey("secret-key"), new MockHttpServletResponse(), new Object()));
    }

    @ParameterizedTest(name = "key = \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"wrong-key", "secret-key ", "SECRET-KEY", "secret-ke"})
    @DisplayName("preHandle with a missing or different key throws 401 INVALID_INTERNAL_API_KEY")
    void preHandle_missingOrWrongKey_throwsUnauthorized(String key) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(requestWithKey(key), new MockHttpServletResponse(), new Object()));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("INVALID_INTERNAL_API_KEY", ex.getCode());
    }

    @ParameterizedTest(name = "configured key = \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("the constructor refuses to start without a configured key")
    void constructor_blankKey_throwsIllegalState(String configuredKey) {
        assertThrows(IllegalStateException.class, () -> new InternalApiKeyInterceptor(configuredKey));
    }
}
