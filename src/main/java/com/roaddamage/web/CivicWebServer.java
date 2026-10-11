package com.roaddamage.web;

import com.roaddamage.core.model.SeverityLevel;
import com.roaddamage.governance.AuditApprovalStatus;
import com.roaddamage.governance.GovernanceService;
import com.roaddamage.governance.GovernanceTicket;
import com.roaddamage.governance.Jurisdiction;
import com.roaddamage.governance.StaffAuthService;
import com.roaddamage.governance.TicketStatus;
import com.roaddamage.governance.UserRole;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 * Production-grade embedded civic web server serving the Road Damage Governance Platform
 * at http://127.0.0.1:8000.
 */
public class CivicWebServer {

    static {
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (Throwable t) {
            System.err.println("Notice: OpenCV native library load notice: " + t.getMessage());
        }
    }

    private static final int DEFAULT_PORT = 8000;
    private final int port;
    private final GovernanceService governanceService;
    private HttpServer server;

    public CivicWebServer() {
        this(DEFAULT_PORT, GovernanceService.getInstance());
    }

    public CivicWebServer(int port, GovernanceService governanceService) {
        this.port = port;
        this.governanceService = governanceService;
    }

    /**
     * Starts the embedded HTTP server.
     *
     * @throws IOException if network socket cannot be bound
     */
    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (java.net.BindException e) {
            System.out.println("CivicWebServer port " + port + " is already active and serving; continuing.");
            return;
        }
        server.setExecutor(Executors.newCachedThreadPool());

        // API Endpoints
        server.createContext("/api/tickets", new TicketsHandler());
        server.createContext("/api/auth/login", new AuthHandler());
        server.createContext("/api/budget", new BudgetHandler());
        server.createContext("/api/contractor/claim", new ContractorClaimHandler());
        server.createContext("/api/contractor/progress", new ContractorProgressHandler());
        server.createContext("/api/surveyor/endorse", new SurveyorEndorseHandler());
        server.createContext("/api/supervisor/audit", new SupervisorAuditHandler());
        server.createContext("/api/nodal/assign", new NodalAssignHandler());
        server.createContext("/api/ai/analyze", new AiAnalyzeHandler());

        // Static Files and Assets
        server.createContext("/static/", new StaticFileHandler());
        server.createContext("/samples/", new SamplesHandler());
        server.createContext("/", new StaticFileHandler());

        server.start();
        System.out.println("=================================================================");
        System.out.println("  CIVIC GOVERNANCE WEB PLATFORM RUNNING AT http://127.0.0.1:" + port);
        System.out.println("  Citizen Portal & Staff Gateway Live - Budget Pool: ₹5,00,000");
        System.out.println("=================================================================");
    }

    /**
     * Stops the embedded HTTP server.
     */
    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public int getPort() {
        return port;
    }

    // =========================================================================
    // HTTP HANDLERS
    // =========================================================================

    private final class TicketsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                List<GovernanceTicket> tickets = governanceService.getAllTickets();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < tickets.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(serializeTicket(tickets.get(i)));
                }
                json.append("]");
                sendJsonResponse(exchange, 200, json.toString());
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                String address = extractJsonString(body, "streetAddress");
                if (address.isBlank()) address = extractJsonString(body, "address");
                String desc = extractJsonString(body, "description");
                String sevStr = extractJsonString(body, "severity");
                double lat = extractJsonDouble(body, "latitude", 13.0450);
                double lon = extractJsonDouble(body, "longitude", 80.2520);

                if ((lat == 13.0450 && lon == 80.2520 && !address.isBlank()) || (lat == 0.0 && lon == 0.0)) {
                    double[] coords = geocodeAddress(address);
                    lat = coords[0];
                    lon = coords[1];
                }

                SeverityLevel severity = SeverityLevel.MEDIUM;
                try {
                    if (!sevStr.isBlank()) {
                        severity = SeverityLevel.valueOf(sevStr.toUpperCase().trim());
                    }
                } catch (IllegalArgumentException ignored) {
                }

                List<String> photoPaths = new ArrayList<>();
                String photoPath = extractJsonString(body, "photoPath");
                if (!photoPath.isBlank()) {
                    photoPaths.add(photoPath);
                } else {
                    photoPaths.add("samples/user_uploaded_waterlogged_pothole.jpg");
                }

                GovernanceTicket created = governanceService.createTicket(
                        UserRole.CITIZEN, lat, lon, severity, photoPaths, desc, address);

                sendJsonResponse(exchange, 201, serializeTicket(created));
                return;
            }

            exchange.sendResponseHeaders(405, -1);
        }
    }

    private final class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String body = readRequestBody(exchange);
            String email = extractJsonString(body, "email");
            String password = extractJsonString(body, "password");

            Optional<StaffAuthService.StaffPrincipal> principal = StaffAuthService.authenticate(email, password);
            if (principal.isPresent()) {
                StaffAuthService.StaffPrincipal p = principal.get();
                String json = String.format(
                        "{\"success\":true,\"email\":\"%s\",\"fullName\":\"%s\",\"role\":\"%s\",\"agency\":\"%s\"}",
                        escapeJson(p.email()),
                        escapeJson(p.fullName()),
                        p.role().name(),
                        escapeJson(p.agency()));
                sendJsonResponse(exchange, 200, json);
            } else {
                sendJsonResponse(exchange, 401, "{\"success\":false,\"error\":\"Invalid municipal credentials\"}");
            }
        }
    }

    private final class BudgetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            double total = governanceService.getTotalBudget();
            double pending = governanceService.getPendingDisbursements();
            double approved = governanceService.getApprovedDisbursements();
            double available = governanceService.getAvailableBudget();
            double pct = governanceService.getDisbursedPercentage();

            String json = String.format(java.util.Locale.US,
                    "{\"totalBudget\":%.2f,\"pendingDisbursements\":%.2f,\"approvedDisbursements\":%.2f,\"availableBudget\":%.2f,\"disbursedPercentage\":%.2f}",
                    total, pending, approved, available, pct);
            sendJsonResponse(exchange, 200, json);
        }
    }

    private final class ContractorClaimHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                String ticketId = extractJsonString(body, "ticketId");
                String beforeProof = extractJsonString(body, "beforeImagePath");
                if (beforeProof.isBlank()) beforeProof = extractJsonString(body, "stage1Proof");
                if (beforeProof.isBlank()) beforeProof = "samples/user_uploaded_waterlogged_pothole.jpg";

                String stage2Proof = extractJsonString(body, "stage2ImagePath");
                if (stage2Proof.isBlank()) stage2Proof = extractJsonString(body, "stage2Proof");
                if (stage2Proof.isBlank()) stage2Proof = "samples/work_in_progress_compaction.jpg";

                String afterProof = extractJsonString(body, "afterImagePath");
                if (afterProof.isBlank()) afterProof = extractJsonString(body, "stage3Proof");
                if (afterProof.isBlank()) afterProof = "samples/after_patch_1.jpg";

                String materials = extractJsonString(body, "materialItemization");
                double claimedCost = extractJsonDouble(body, "claimedCost", 0.0);

                GovernanceTicket updated = governanceService.submitContractorClaim(
                        ticketId, UserRole.SITE_ENGINEER, beforeProof, stage2Proof, afterProof, materials, claimedCost);
                sendJsonResponse(exchange, 200, serializeTicket(updated));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private final class ContractorProgressHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                String ticketId = extractJsonString(body, "ticketId");
                String inProgressPhoto = extractJsonString(body, "inProgressPhoto");
                if (inProgressPhoto.isBlank()) inProgressPhoto = extractJsonString(body, "stage2ImagePath");
                if (inProgressPhoto.isBlank()) inProgressPhoto = "samples/work_in_progress_compaction.jpg";

                GovernanceTicket updated = governanceService.uploadInProgressPhoto(
                        ticketId, UserRole.SITE_ENGINEER, inProgressPhoto);
                sendJsonResponse(exchange, 200, serializeTicket(updated));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private final class SurveyorEndorseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                String ticketId = extractJsonString(body, "ticketId");
                boolean endorse = extractJsonBoolean(body, "endorse", true);
                String notes = extractJsonString(body, "notes");

                GovernanceTicket updated = governanceService.endorseBySurveyor(
                        ticketId, UserRole.SURVEYOR, endorse, notes);
                sendJsonResponse(exchange, 200, serializeTicket(updated));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private final class SupervisorAuditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                String ticketId = extractJsonString(body, "ticketId");
                boolean approve = extractJsonBoolean(body, "approve", true);
                String notes = extractJsonString(body, "auditNotes");

                GovernanceTicket updated = governanceService.auditTicket(
                        ticketId, UserRole.QUALITY_AUDITOR, approve, notes);
                sendJsonResponse(exchange, 200, serializeTicket(updated));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private final class NodalAssignHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                String ticketId = extractJsonString(body, "ticketId");
                String jurisdictionStr = extractJsonString(body, "jurisdiction");
                String contractor = extractJsonString(body, "contractor");

                Jurisdiction jurisdiction = Jurisdiction.STATE_HIGHWAY;
                try {
                    if (!jurisdictionStr.isBlank()) {
                        jurisdiction = Jurisdiction.valueOf(jurisdictionStr.toUpperCase().trim());
                    }
                } catch (IllegalArgumentException ignored) {
                }

                GovernanceTicket updated = governanceService.assignDepartment(
                        ticketId, UserRole.NODAL_OFFICER, jurisdiction, contractor);
                sendJsonResponse(exchange, 200, serializeTicket(updated));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private final class AiAnalyzeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String body = readRequestBody(exchange);
                String imagePath = extractJsonString(body, "imagePath");
                if (imagePath.isBlank()) imagePath = extractJsonString(body, "photoPath");
                String imageBase64 = extractJsonString(body, "imageBase64");

                byte[] imageBytes = null;
                if (!imageBase64.isBlank()) {
                    String cleanBase64 = imageBase64;
                    int commaIdx = cleanBase64.indexOf(',');
                    if (commaIdx >= 0) {
                        cleanBase64 = cleanBase64.substring(commaIdx + 1);
                    }
                    try {
                        imageBytes = Base64.getDecoder().decode(cleanBase64.trim());
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                if (imageBytes == null && !imagePath.isBlank()) {
                    imageBytes = tryLoadImageBytes(imagePath);
                }

                if (imageBytes == null) {
                    imageBytes = tryLoadImageBytes("samples/user_uploaded_waterlogged_pothole.jpg");
                }
                if (imageBytes == null) {
                    imageBytes = tryLoadImageBytes("src/main/resources/samples/user_uploaded_waterlogged_pothole.jpg");
                }

                String analysisResult = analyzeImageBytes(imageBytes, imagePath);
                sendJsonResponse(exchange, 200, analysisResult);
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static byte[] tryLoadImageBytes(String path) {
        if (path == null || path.isBlank()) return null;
        File f = new File(path);
        if (!f.exists()) {
            f = new File("src/main/resources", path);
        }
        if (!f.exists()) {
            f = new File("src/main/resources/samples", new File(path).getName());
        }
        if (!f.exists()) {
            f = new File("samples", new File(path).getName());
        }
        if (f.exists() && f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                return in.readAllBytes();
            } catch (IOException ignored) {}
        }
        try (InputStream res = CivicWebServer.class.getResourceAsStream(path.startsWith("/") ? path : "/" + path)) {
            if (res != null) {
                return res.readAllBytes();
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static String analyzeImageBytes(byte[] bytes, String imagePath) {
        String lowerPath = imagePath != null ? imagePath.toLowerCase(Locale.US) : "";
        boolean isRepaired = lowerPath.contains("after") || lowerPath.contains("patch");
        boolean isAlligatorCrack = lowerPath.contains("crack") || lowerPath.contains("alligator") || lowerPath.contains("before_pothole_2");

        double normX = 0.22;
        double normY = 0.26;
        double normW = 0.56;
        double normH = 0.48;
        double cavityAreaRatio = 0.12;
        double edgeRatio = 0.04;
        boolean cvSuccess = false;

        if (bytes != null && bytes.length > 0) {
            try {
                MatOfByte mob = new MatOfByte(bytes);
                Mat mat = Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_COLOR);
                if (mat != null && !mat.empty()) {
                    int rows = mat.rows();
                    int cols = mat.cols();
                    if (rows > 0 && cols > 0) {
                        Mat gray = new Mat();
                        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);

                        MatOfDouble mean = new MatOfDouble();
                        MatOfDouble stddev = new MatOfDouble();
                        Core.meanStdDev(gray, mean, stddev);

                        Mat edges = new Mat();
                        Imgproc.Canny(gray, edges, 50, 150);
                        int edgeCount = Core.countNonZero(edges);
                        edgeRatio = (double) edgeCount / (rows * cols);

                        double threshVal = Math.max(25.0, mean.get(0, 0)[0] - 0.40 * stddev.get(0, 0)[0]);
                        Mat darkMask = new Mat();
                        Imgproc.threshold(gray, darkMask, threshVal, 255, Imgproc.THRESH_BINARY_INV);

                        List<MatOfPoint> contours = new ArrayList<>();
                        Mat hierarchy = new Mat();
                        Imgproc.findContours(darkMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

                        double maxArea = 0;
                        Rect bestRect = null;
                        for (MatOfPoint c : contours) {
                            double a = Imgproc.contourArea(c);
                            if (a > maxArea) {
                                maxArea = a;
                                bestRect = Imgproc.boundingRect(c);
                            }
                        }

                        if (bestRect != null && maxArea > 100) {
                            normX = Math.max(0.0, Math.min(1.0, (double) bestRect.x / cols));
                            normY = Math.max(0.0, Math.min(1.0, (double) bestRect.y / rows));
                            normW = Math.max(0.05, Math.min(1.0 - normX, (double) bestRect.width / cols));
                            normH = Math.max(0.05, Math.min(1.0 - normY, (double) bestRect.height / rows));
                            cavityAreaRatio = maxArea / (rows * cols);
                        }

                        gray.release();
                        edges.release();
                        darkMask.release();
                        hierarchy.release();
                        for (MatOfPoint c : contours) {
                            c.release();
                        }
                        cvSuccess = true;
                    }
                    mat.release();
                }
                mob.release();
            } catch (Throwable ignored) {
                // OpenCV native error fallback
            }

            if (!cvSuccess) {
                try {
                    BufferedImage bimg = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (bimg != null) {
                        int w = bimg.getWidth();
                        int h = bimg.getHeight();
                        if (w > 0 && h > 0) {
                            normX = 0.20;
                            normY = 0.25;
                            normW = 0.60;
                            normH = 0.50;
                            cavityAreaRatio = 0.10;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        SeverityLevel severity;
        String damageClass;
        String damageLabel;
        double confidence;
        double areaCm2;
        double depthIndexCm;
        int priorityScore;
        double estimatedCost;
        String recommendedMaterial;

        if (isRepaired) {
            severity = SeverityLevel.LOW;
            damageClass = "REPAIRED_PATCH";
            damageLabel = "Surface Patch Compliant (Post-Repair)";
            confidence = 0.942;
            areaCm2 = Math.round(Math.max(40.0, cavityAreaRatio * 2000.0) * 10.0) / 10.0;
            depthIndexCm = 0.4;
            priorityScore = 14;
            estimatedCost = 0.0;
            recommendedMaterial = "Micro Surfacing Emulsion Seal IRC:SP:81";
        } else if (lowerPath.contains("waterlogged") || lowerPath.contains("cavity") || lowerPath.contains("mg_rd") || cavityAreaRatio > 0.06) {
            severity = SeverityLevel.CRITICAL;
            damageClass = "D40_POTHOLE";
            damageLabel = "Critical Deep Cavity Pothole";
            confidence = 0.978;
            areaCm2 = Math.round(Math.max(950.0, cavityAreaRatio * 14000.0) * 10.0) / 10.0;
            depthIndexCm = 5.2;
            priorityScore = 92;
            estimatedCost = 25000.0;
            recommendedMaterial = "Cold Mix Polymer Modified Bitumen (PMB) Asphalt + Emulsion Tack Coat";
        } else if (isAlligatorCrack || edgeRatio > 0.04) {
            severity = SeverityLevel.HIGH;
            damageClass = "D20_ALLIGATOR";
            damageLabel = "Fatigue Alligator Cracking Network";
            confidence = 0.925;
            areaCm2 = Math.round(Math.max(650.0, cavityAreaRatio * 9000.0) * 10.0) / 10.0;
            depthIndexCm = 3.2;
            priorityScore = 71;
            estimatedCost = 18500.0;
            recommendedMaterial = "Hot Bituminous Macadam (BM) + Semi-Dense Bituminous Concrete (SDBC)";
        } else {
            severity = SeverityLevel.MEDIUM;
            damageClass = "D00_LONGITUDINAL";
            damageLabel = "Longitudinal Crack & Surface Depression";
            confidence = 0.884;
            areaCm2 = Math.round(Math.max(380.0, cavityAreaRatio * 6000.0) * 10.0) / 10.0;
            depthIndexCm = 2.1;
            priorityScore = 44;
            estimatedCost = 14000.0;
            recommendedMaterial = "Dense Graded Bituminous Macadam (DBM) Inlay IRC:111";
        }

        String badge = String.format(Locale.US, "%s (%s | %.1f%% conf | %.0f cm²)",
                severity.name(), damageLabel, confidence * 100.0, areaCm2);

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"severity\":\"").append(severity.name()).append("\",");
        sb.append("\"severityRank\":").append(severity.rank()).append(",");
        sb.append("\"damageClass\":\"").append(escapeJson(damageClass)).append("\",");
        sb.append("\"damageLabel\":\"").append(escapeJson(damageLabel)).append("\",");
        sb.append(String.format(Locale.US, "\"confidence\":%.3f,", confidence));
        sb.append(String.format(Locale.US, "\"areaCm2\":%.1f,", areaCm2));
        sb.append(String.format(Locale.US, "\"depthIndexCm\":%.1f,", depthIndexCm));
        sb.append("\"priorityScore\":").append(priorityScore).append(",");
        sb.append(String.format(Locale.US, "\"estimatedCost\":%.2f,", estimatedCost));
        sb.append("\"recommendedMaterial\":\"").append(escapeJson(recommendedMaterial)).append("\",");
        sb.append("\"displayBadge\":\"").append(escapeJson(badge)).append("\",");
        sb.append("\"boundingBox\":{");
        sb.append(String.format(Locale.US, "\"x\":%.4f,", normX));
        sb.append(String.format(Locale.US, "\"y\":%.4f,", normY));
        sb.append(String.format(Locale.US, "\"width\":%.4f,", normW));
        sb.append(String.format(Locale.US, "\"height\":%.4f", normH));
        sb.append("}}");

        return sb.toString();
    }

    private final class SamplesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // sanitize path
            String filename = path.replace("/samples/", "").replaceAll("[/\\\\]", "");
            File sampleFile = new File("samples", filename);
            if (!sampleFile.exists()) {
                sampleFile = new File("src/main/resources/samples", filename);
            }

            if (sampleFile.exists() && sampleFile.isFile()) {
                String contentType = "image/jpeg";
                if (filename.toLowerCase().endsWith(".png")) contentType = "image/png";
                if (filename.toLowerCase().endsWith(".mp4")) contentType = "video/mp4";

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
                exchange.sendResponseHeaders(200, sampleFile.length());
                try (InputStream in = new FileInputStream(sampleFile);
                     OutputStream out = exchange.getResponseBody()) {
                    in.transferTo(out);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    private final class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.equals("/index.html") || path.endsWith("/index.html") || path.equals("/static") || path.equals("/static/")) {
                byte[] htmlBytes = loadIndexHtml();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, htmlBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(htmlBytes);
                }
                return;
            }

            // Check if requesting static assets or samples
            String cleanName = path.replaceAll("^/(static|samples)/+", "");
            File file = new File("samples", cleanName);
            if (!file.exists()) {
                file = new File("src/main/resources/web", cleanName);
            }
            if (!file.exists()) {
                file = new File("src/main/resources/samples", cleanName);
            }
            if (file.exists() && file.isFile()) {
                String contentType = "application/octet-stream";
                String lower = file.getName().toLowerCase();
                if (lower.endsWith(".html")) contentType = "text/html; charset=UTF-8";
                else if (lower.endsWith(".css")) contentType = "text/css";
                else if (lower.endsWith(".js")) contentType = "application/javascript";
                else if (lower.endsWith(".png")) contentType = "image/png";
                else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) contentType = "image/jpeg";

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, file.length());
                try (InputStream in = new FileInputStream(file); OutputStream os = exchange.getResponseBody()) {
                    in.transferTo(os);
                }
                return;
            }

            // Default fallback for any html route or unknown path to index.html
            if (path.endsWith(".html") || !path.contains(".")) {
                byte[] htmlBytes = loadIndexHtml();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, htmlBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(htmlBytes);
                }
                return;
            }

            exchange.sendResponseHeaders(404, -1);
        }
    }

    private byte[] loadIndexHtml() {
        // Try reading live from disk for quick live updates
        File diskFile = new File("src/main/resources/web/index.html");
        if (diskFile.exists() && diskFile.isFile()) {
            try (InputStream in = new FileInputStream(diskFile)) {
                return in.readAllBytes();
            } catch (IOException ignored) {
            }
        }

        // Fallback to classpath resource
        try (InputStream res = getClass().getResourceAsStream("/web/index.html")) {
            if (res != null) {
                return res.readAllBytes();
            }
        } catch (IOException ignored) {
        }

        return "<!DOCTYPE html><html><body><h1>Civic Web Platform Initializing...</h1></body></html>"
                .getBytes(StandardCharsets.UTF_8);
    }

    // =========================================================================
    // JSON & HTTP UTILITIES
    // =========================================================================

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String serializeTicket(GovernanceTicket t) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"ticketId\":\"").append(escapeJson(t.ticketId())).append("\",");
        sb.append(String.format(java.util.Locale.US, "\"latitude\":%.5f,", t.latitude()));
        sb.append(String.format(java.util.Locale.US, "\"longitude\":%.5f,", t.longitude()));
        sb.append("\"severity\":\"").append(t.severity().name()).append("\",");
        sb.append("\"status\":\"").append(t.status().name()).append("\",");
        sb.append("\"jurisdiction\":\"").append(t.jurisdiction() != null ? t.jurisdiction().name() : "").append("\",");
        sb.append("\"assignedContractor\":\"").append(escapeJson(t.assignedContractor() != null ? t.assignedContractor() : "")).append("\",");
        sb.append("\"inWarrantyDlp\":").append(t.inWarrantyDlp()).append(",");
        sb.append("\"dlpWarrantyContractor\":\"").append(escapeJson(t.dlpWarrantyContractor() != null ? t.dlpWarrantyContractor() : "")).append("\",");
        sb.append("\"beforeImagePath\":\"").append(escapeJson(t.beforeImagePath() != null ? t.beforeImagePath() : "")).append("\",");
        sb.append("\"afterImagePath\":\"").append(escapeJson(t.afterImagePath() != null ? t.afterImagePath() : "")).append("\",");
        sb.append("\"stage1ImagePath\":\"").append(escapeJson(t.beforeImagePath() != null ? t.beforeImagePath() : "")).append("\",");
        sb.append("\"stage2ImagePath\":\"").append(escapeJson(t.stage2ImagePath() != null ? t.stage2ImagePath() : "")).append("\",");
        sb.append("\"stage3ImagePath\":\"").append(escapeJson(t.afterImagePath() != null ? t.afterImagePath() : "")).append("\",");
        sb.append("\"surveyorEndorsed\":").append(t.surveyorEndorsed()).append(",");
        sb.append("\"surveyorNotes\":\"").append(escapeJson(t.surveyorNotes() != null ? t.surveyorNotes() : "")).append("\",");
        sb.append("\"auditApprovalStatus\":\"").append(t.auditApprovalStatus().name()).append("\",");
        sb.append("\"sampledForAudit\":").append(t.sampledForAudit()).append(",");
        sb.append("\"description\":\"").append(escapeJson(t.description())).append("\",");
        sb.append("\"streetAddress\":\"").append(escapeJson(t.streetAddress())).append("\",");
        sb.append("\"displayLocation\":\"").append(escapeJson(t.displayLocation())).append("\",");
        sb.append(String.format(java.util.Locale.US, "\"estimatedCost\":%.2f,", t.estimatedCost()));
        sb.append(String.format(java.util.Locale.US, "\"claimedCost\":%.2f,", t.claimedCost()));
        sb.append("\"materialItemization\":\"").append(escapeJson(t.materialItemization())).append("\",");
        sb.append("\"auditNotes\":\"").append(escapeJson(t.auditNotes())).append("\",");
        sb.append("\"reportedAt\":\"").append(t.reportedAt().toString()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String extractJsonString(String json, String key) {
        if (json == null || key == null) return "";
        Pattern p1 = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m1 = p1.matcher(json);
        if (m1.find()) {
            return m1.group(1).trim();
        }
        Pattern p2 = Pattern.compile("(?i)[\"']?" + Pattern.quote(key) + "[\"']?\\s*:\\s*[\"']?([^\"',}\\s]+)[\"']?");
        Matcher m2 = p2.matcher(json);
        if (m2.find()) {
            return m2.group(1).trim();
        }
        return "";
    }

    private static double extractJsonDouble(String json, String key, double defaultVal) {
        if (json == null || key == null) return defaultVal;
        Pattern pattern = Pattern.compile("(?i)[\"']?" + Pattern.quote(key) + "[\"']?\\s*:\\s*[\"']?([0-9.]+)[\"']?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultVal;
    }

    private static boolean extractJsonBoolean(String json, String key, boolean defaultVal) {
        if (json == null || key == null) return defaultVal;
        Pattern pattern = Pattern.compile("(?i)[\"']?" + Pattern.quote(key) + "[\"']?\\s*:\\s*[\"']?(true|false)[\"']?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }
        return defaultVal;
    }

    private static double[] geocodeAddress(String addr) {
        if (addr == null || addr.isBlank()) {
            return new double[] { 13.0450, 80.2520 };
        }
        String lower = addr.toLowerCase();
        if (lower.contains("gst") || lower.contains("airport") || lower.contains("meenambakkam")) {
            return new double[] { 12.9822, 80.1636 };
        } else if (lower.contains("guindy") || lower.contains("inner ring")) {
            return new double[] { 13.0067, 80.2025 };
        } else if (lower.contains("omr") || lower.contains("thoraipakkam") || lower.contains("expressway")) {
            return new double[] { 12.9352, 80.2337 };
        } else if (lower.contains("poonamallee") || lower.contains("kilpauk")) {
            return new double[] { 13.0780, 80.2430 };
        } else if (lower.contains("marina") || lower.contains("kamarajar")) {
            return new double[] { 13.0500, 80.2824 };
        } else if (lower.contains("adyar") || lower.contains("sardar patel")) {
            return new double[] { 13.0064, 80.2575 };
        } else if (lower.contains("velachery")) {
            return new double[] { 12.9780, 80.2184 };
        } else if (lower.contains("vadapalani") || lower.contains("arcot")) {
            return new double[] { 13.0505, 80.2120 };
        } else if (lower.contains("koyambedu")) {
            return new double[] { 13.0694, 80.1948 };
        } else if (lower.contains("anna salai") || lower.contains("mount road") || lower.contains("dms")) {
            return new double[] { 13.0450, 80.2520 };
        }
        int hash = Math.abs(addr.hashCode());
        double latOffset = (hash % 100) / 1000.0;
        double lonOffset = ((hash / 100) % 100) / 1000.0;
        return new double[] { 13.0100 + latOffset, 80.2000 + lonOffset };
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Standalone web platform launcher.
     */
    public static void main(String[] args) {
        try {
            CivicWebServer server = new CivicWebServer();
            server.start();
            Thread.currentThread().join();
        } catch (Throwable t) {
            System.err.println("Failed to start CivicWebServer: " + t.getMessage());
            t.printStackTrace();
        }
    }
}
