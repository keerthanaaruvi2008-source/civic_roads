package com.roaddamage.web;

import com.roaddamage.governance.GovernanceService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivicWebServerTest {

    private static final int TEST_PORT = 8089;
    private static CivicWebServer server;
    private static HttpClient client;

    @BeforeAll
    static void setUp() throws IOException {
        GovernanceService service = GovernanceService.getInstance();
        server = new CivicWebServer(TEST_PORT, service);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("GET / serves index.html single-page civic application")
    void testServeIndexHtml() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + TEST_PORT + "/"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("CIVIC ROADS") || resp.body().contains("html"));
    }

    @Test
    @DisplayName("GET /api/tickets returns active governance tickets")
    void testGetTickets() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + TEST_PORT + "/api/tickets"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("TKT-"));
    }

    @Test
    @DisplayName("GET /api/budget returns municipal budget ledger")
    void testGetBudget() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + TEST_PORT + "/api/budget"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("totalBudget"));
        assertTrue(resp.body().contains("500000"));
    }

    @Test
    @DisplayName("POST /api/auth/login authenticates staff credentials")
    void testAuthLogin() throws Exception {
        String json = "{\"email\":\"admin@pwd.gov\",\"password\":\"pwd123\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + TEST_PORT + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"success\":true"));
        assertTrue(resp.body().contains("NODAL_OFFICER"));
    }

    @Test
    @DisplayName("POST /api/tickets submits citizen grievance with street address")
    void testSubmitGrievance() throws Exception {
        String json = "{\"streetAddress\":\"Mount Road, Anna Salai near Thousand Lights\",\"description\":\"Deep crater\",\"severity\":\"CRITICAL\",\"latitude\":13.0550,\"longitude\":80.2550}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + TEST_PORT + "/api/tickets"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, resp.statusCode());
        assertTrue(resp.body().contains("Mount Road, Anna Salai"));
        assertTrue(resp.body().contains("CRITICAL"));
    }

    @Test
    @DisplayName("POST /api/ai/analyze evaluates uploaded distress image")
    void testAiAnalyze() throws Exception {
        String json = "{\"imagePath\":\"samples/user_uploaded_waterlogged_pothole.jpg\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + TEST_PORT + "/api/ai/analyze"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("CRITICAL"));
        assertTrue(resp.body().contains("D40_POTHOLE"));
        assertTrue(resp.body().contains("boundingBox"));
    }

    @Test
    @DisplayName("POST /api/surveyor/endorse recommends funding for completed claim")
    void testSurveyorEndorse() throws Exception {
        // Find or use TKT-1004 from demo data
        String endorseJson = "{\"ticketId\":\"TKT-1004\",\"endorse\":true,\"notes\":\"Site compaction verified 50mm\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + TEST_PORT + "/api/surveyor/endorse"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(endorseJson))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("SURVEYOR_ENDORSED"));
        assertTrue(resp.body().contains("\"surveyorEndorsed\":true"));
    }
}
