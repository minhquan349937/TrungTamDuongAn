package com.example.TTGDHNDuongAn.service;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class GeminiSummaryClientTests {
    HttpServer server;
    AtomicInteger calls=new AtomicInteger();
    AtomicReference<String> request=new AtomicReference<>();
    int code=200;
    long delay;
    String body="{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"Nhận xét giả lập\"}]}}]}";
    final AiSummaryDataService.Context context=new AiSummaryDataService.Context("ACTIVE",List.of());
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            calls.incrementAndGet();request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            assertEquals("test-key",exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            assertEquals("/gemini-3.5-flash-lite:generateContent",exchange.getRequestURI().getPath());
            try { Thread.sleep(delay); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        });server.start();
    }
    @AfterEach void close() { server.stop(0); }
    GeminiSummaryClient client(boolean enabled,Duration timeout) {
        return new GeminiSummaryClient("test-key","gemini-3.5-flash-lite",enabled,timeout,HttpClient.newHttpClient(),
            URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"));
    }
    @ParameterizedTest @CsvSource(value={"''","Tập trung"})
    void successfulRequestSupportsBlankAndShortKeywords(String keywords) {
        assertEquals("Nhận xét giả lập",client(true,Duration.ofSeconds(2)).generate(context,keywords));
        var json=new ObjectMapper().readTree(request.get());
        assertTrue(json.path("systemInstruction").toString().contains("NOT_STARTED"));
        assertTrue(json.path("contents").toString().contains("teacherObservations"));
        assertFalse(request.get().contains("test-key"));assertEquals(1,calls.get());
    }
    @ParameterizedTest @CsvSource({"404,502","429,429","503,503","401,502"})
    void upstreamErrorsAreSafeAndNeverRetried(int upstream,int expected) {
        code=upstream;body="PRIVATE-PROVIDER-DATA";
        var ex=assertThrows(ResponseStatusException.class,()->client(true,Duration.ofSeconds(2)).generate(context,""));
        assertEquals(expected,ex.getStatusCode().value());assertFalse(ex.toString().contains(body));assertNull(ex.getCause());assertEquals(1,calls.get());
    }
    @Test void timeoutAndDisabledConfiguration() {
        assertEquals(503,assertThrows(ResponseStatusException.class,()->client(false,Duration.ofSeconds(1)).generate(context,"")).getStatusCode().value());
        assertEquals(0,calls.get());delay=200;
        assertEquals(504,assertThrows(ResponseStatusException.class,()->client(true,Duration.ofMillis(50)).generate(context,"")).getStatusCode().value());
    }
    @Test void malformedBlockedAndTruncatedResponsesAreRejected() {
        for(String response:List.of("invalid-json","{}","{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}")) {
            body=response;
            assertEquals(502,assertThrows(ResponseStatusException.class,()->client(true,Duration.ofSeconds(2)).generate(context,"")).getStatusCode().value());
        }
    }
    @Test void quotasEnforceUserAndGlobalLimitsAndResetNextHour() {
        var quota=new AiSummaryQuota(1,2);quota.acquire("a");
        assertEquals(429,assertThrows(ResponseStatusException.class,()->quota.acquire("a")).getStatusCode().value());
        quota.acquire("b");assertThrows(ResponseStatusException.class,()->quota.acquire("c"));
        var instant=new AtomicReference<>(Instant.parse("2026-11-10T00:00:00Z"));
        Clock clock=new Clock() {
            public ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(ZoneId zone){return this;}
            public Instant instant(){return instant.get();}
        };
        var resetting=new AiSummaryQuota(1,1,clock);resetting.acquire("a");instant.set(instant.get().plusSeconds(3600));resetting.acquire("a");
    }
}
