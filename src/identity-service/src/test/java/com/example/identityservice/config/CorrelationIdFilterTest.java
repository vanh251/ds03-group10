package com.example.identityservice.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    /** Runs the filter and returns the correlationId visible in the MDC while the rest of the chain executes. */
    private String runFilter(MockHttpServletRequest request) throws Exception {
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY));
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return mdcDuringChain.get();
    }

    @Test
    @DisplayName("doFilter with X-Correlation-ID puts that id in the MDC and request attribute, then clears the MDC")
    void doFilter_withHeader_propagatesIdAndClearsMdc() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);

        String mdcValue = runFilter(request);

        assertEquals(correlationId, mdcValue);
        assertEquals(correlationId, request.getAttribute(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY));
    }

    @Test
    @DisplayName("doFilter without X-Correlation-ID does not generate an id")
    void doFilter_withoutHeader_doesNotGenerateId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String mdcValue = runFilter(request);

        assertNull(mdcValue);
        assertNull(request.getAttribute(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }

    @Test
    @DisplayName("doFilter with a blank X-Correlation-ID treats it as missing")
    void doFilter_blankHeader_doesNotGenerateId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "   ");

        String mdcValue = runFilter(request);

        assertNull(mdcValue);
        assertNull(request.getAttribute(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }
}
