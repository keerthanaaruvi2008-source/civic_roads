package com.roaddamage.governance;

import com.roaddamage.core.model.SeverityLevel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Thread-safe immutable model representing a municipal road defect grievance and public works lifecycle ticket.
 *
 * @param ticketId unique grievance identifier (e.g., TKT-1001)
 * @param latitude WGS-84 geographic latitude coordinate
 * @param longitude WGS-84 geographic longitude coordinate
 * @param photoPaths unmodifiable list of initial defect photo paths
 * @param severity classified pavement damage severity tier
 * @param status current lifecycle milestone status
 * @param jurisdiction administrative road governance agency
 * @param assignedContractor primary commercial contractor assigned for repair
 * @param inWarrantyDlp flag indicating whether defect is covered by contractor Defect Liability Period (DLP)
 * @param dlpWarrantyContractor original contractor bound by warranty obligations
 * @param beforeImagePath path to high-res site proof captured before repair commencement
 * @param afterImagePath path to high-res site proof captured upon physical repair completion
 * @param materialRequests unmodifiable list of asphalt/bitumen material procurement requisition entries
 * @param auditApprovalStatus certification state from independent quality assurance
 * @param sampledForAudit flag denoting whether ticket was randomly selected for independent audit
 * @param description citizen narrative or automated distress commentary
 * @param reportedAt instant when ticket was created
 * @param assignedAt instant when nodal officer assigned agency and contractor
 * @param completedAt instant when site engineer finished physical works
 * @param resolvedAt instant when ticket was audited and formally closed
 * @param auditNotes auditor remarks or reason for rejection
 * @param streetAddress road or street location description
 * @param estimatedCost standard PWD schedule of rates cost estimate
 * @param claimedCost monetary repair claim submitted by contractor
 * @param materialItemization detailed description of materials used
 */
public record GovernanceTicket(
        String ticketId,
        double latitude,
        double longitude,
        List<String> photoPaths,
        SeverityLevel severity,
        TicketStatus status,
        Jurisdiction jurisdiction,
        String assignedContractor,
        boolean inWarrantyDlp,
        String dlpWarrantyContractor,
        String beforeImagePath,
        String stage2ImagePath,
        String afterImagePath,
        List<String> materialRequests,
        AuditApprovalStatus auditApprovalStatus,
        boolean sampledForAudit,
        boolean surveyorEndorsed,
        String surveyorNotes,
        String description,
        Instant reportedAt,
        Instant assignedAt,
        Instant completedAt,
        Instant resolvedAt,
        String auditNotes,
        String streetAddress,
        double estimatedCost,
        double claimedCost,
        String materialItemization) {

    /**
     * Compact constructor enforcing immutability and defensive copies.
     */
    public GovernanceTicket {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(auditApprovalStatus, "auditApprovalStatus must not be null");
        Objects.requireNonNull(reportedAt, "reportedAt must not be null");

        photoPaths = photoPaths == null ? List.of() : List.copyOf(photoPaths);
        stage2ImagePath = stage2ImagePath == null ? "" : stage2ImagePath;
        surveyorNotes = surveyorNotes == null ? "" : surveyorNotes;
        materialRequests = materialRequests == null ? List.of() : List.copyOf(materialRequests);
        description = description == null ? "" : description;
        auditNotes = auditNotes == null ? "" : auditNotes;
        streetAddress = streetAddress == null ? "" : streetAddress;
        materialItemization = materialItemization == null ? "" : materialItemization;
        if (estimatedCost <= 0.0) {
            estimatedCost = switch (severity) {
                case CRITICAL -> 25000.0;
                case HIGH -> 18500.0;
                case MEDIUM -> 14000.0;
                case LOW -> 8500.0;
            };
        }
    }

    public String stage1ImagePath() {
        return beforeImagePath;
    }

    public String stage3ImagePath() {
        return afterImagePath;
    }

    /**
     * Formats GPS coordinates as a readable string.
     *
     * @return comma-separated latitude and longitude
     */
    public String gpsCoordinatesString() {
        return String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude);
    }

    /**
     * Returns location display string (street address if available, else coordinates).
     *
     * @return human-readable address or landmark
     */
    public String displayLocation() {
        if (streetAddress != null && !streetAddress.isBlank()) {
            return streetAddress;
        }
        return gpsCoordinatesString();
    }

    /**
     * Creates a builder initialized with the current ticket state.
     *
     * @return populated builder
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Creates a fresh builder for ticket instantiation.
     *
     * @param ticketId unique ticket id
     * @param latitude defect latitude
     * @param longitude defect longitude
     * @param severity defect severity
     * @return new builder instance
     */
    public static Builder builder(String ticketId, double latitude, double longitude, SeverityLevel severity) {
        return new Builder(ticketId, latitude, longitude, severity);
    }

    /**
     * Fluent builder for {@link GovernanceTicket}.
     */
    public static final class Builder {
        private String ticketId;
        private double latitude;
        private double longitude;
        private List<String> photoPaths = new ArrayList<>();
        private SeverityLevel severity;
        private TicketStatus status = TicketStatus.REPORTED;
        private Jurisdiction jurisdiction;
        private String assignedContractor;
        private boolean inWarrantyDlp = false;
        private String dlpWarrantyContractor;
        private String beforeImagePath;
        private String stage2ImagePath = "";
        private String afterImagePath;
        private List<String> materialRequests = new ArrayList<>();
        private AuditApprovalStatus auditApprovalStatus = AuditApprovalStatus.NOT_REQUIRED;
        private boolean sampledForAudit = false;
        private boolean surveyorEndorsed = false;
        private String surveyorNotes = "";
        private String description = "";
        private Instant reportedAt = Instant.now();
        private Instant assignedAt;
        private Instant completedAt;
        private Instant resolvedAt;
        private String auditNotes = "";
        private String streetAddress = "";
        private double estimatedCost = 0.0;
        private double claimedCost = 0.0;
        private String materialItemization = "";

        private Builder(String ticketId, double latitude, double longitude, SeverityLevel severity) {
            this.ticketId = ticketId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.severity = severity;
        }

        private Builder(GovernanceTicket ticket) {
            this.ticketId = ticket.ticketId();
            this.latitude = ticket.latitude();
            this.longitude = ticket.longitude();
            this.photoPaths = new ArrayList<>(ticket.photoPaths());
            this.severity = ticket.severity();
            this.status = ticket.status();
            this.jurisdiction = ticket.jurisdiction();
            this.assignedContractor = ticket.assignedContractor();
            this.inWarrantyDlp = ticket.inWarrantyDlp();
            this.dlpWarrantyContractor = ticket.dlpWarrantyContractor();
            this.beforeImagePath = ticket.beforeImagePath();
            this.stage2ImagePath = ticket.stage2ImagePath();
            this.afterImagePath = ticket.afterImagePath();
            this.materialRequests = new ArrayList<>(ticket.materialRequests());
            this.auditApprovalStatus = ticket.auditApprovalStatus();
            this.sampledForAudit = ticket.sampledForAudit();
            this.surveyorEndorsed = ticket.surveyorEndorsed();
            this.surveyorNotes = ticket.surveyorNotes();
            this.description = ticket.description();
            this.reportedAt = ticket.reportedAt();
            this.assignedAt = ticket.assignedAt();
            this.completedAt = ticket.completedAt();
            this.resolvedAt = ticket.resolvedAt();
            this.auditNotes = ticket.auditNotes();
            this.streetAddress = ticket.streetAddress();
            this.estimatedCost = ticket.estimatedCost();
            this.claimedCost = ticket.claimedCost();
            this.materialItemization = ticket.materialItemization();
        }

        public Builder ticketId(String ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public Builder coordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
            return this;
        }

        public Builder photoPaths(List<String> paths) {
            this.photoPaths = paths == null ? new ArrayList<>() : new ArrayList<>(paths);
            return this;
        }

        public Builder addPhotoPath(String path) {
            if (path != null && !path.isBlank()) {
                this.photoPaths.add(path);
            }
            return this;
        }

        public Builder severity(SeverityLevel severity) {
            this.severity = severity;
            return this;
        }

        public Builder status(TicketStatus status) {
            this.status = status;
            return this;
        }

        public Builder jurisdiction(Jurisdiction jurisdiction) {
            this.jurisdiction = jurisdiction;
            return this;
        }

        public Builder assignedContractor(String assignedContractor) {
            this.assignedContractor = assignedContractor;
            return this;
        }

        public Builder inWarrantyDlp(boolean inWarrantyDlp) {
            this.inWarrantyDlp = inWarrantyDlp;
            return this;
        }

        public Builder dlpWarrantyContractor(String dlpWarrantyContractor) {
            this.dlpWarrantyContractor = dlpWarrantyContractor;
            return this;
        }

        public Builder beforeImagePath(String beforeImagePath) {
            this.beforeImagePath = beforeImagePath;
            return this;
        }

        public Builder stage2ImagePath(String stage2ImagePath) {
            this.stage2ImagePath = stage2ImagePath != null ? stage2ImagePath : "";
            return this;
        }

        public Builder afterImagePath(String afterImagePath) {
            this.afterImagePath = afterImagePath;
            return this;
        }

        public Builder surveyorEndorsed(boolean surveyorEndorsed) {
            this.surveyorEndorsed = surveyorEndorsed;
            return this;
        }

        public Builder surveyorNotes(String surveyorNotes) {
            this.surveyorNotes = surveyorNotes != null ? surveyorNotes : "";
            return this;
        }

        public Builder materialRequests(List<String> materialRequests) {
            this.materialRequests = materialRequests == null ? new ArrayList<>() : new ArrayList<>(materialRequests);
            return this;
        }

        public Builder addMaterialRequest(String request) {
            if (request != null && !request.isBlank()) {
                this.materialRequests.add(request);
            }
            return this;
        }

        public Builder auditApprovalStatus(AuditApprovalStatus auditApprovalStatus) {
            this.auditApprovalStatus = auditApprovalStatus;
            return this;
        }

        public Builder sampledForAudit(boolean sampledForAudit) {
            this.sampledForAudit = sampledForAudit;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder reportedAt(Instant reportedAt) {
            this.reportedAt = reportedAt;
            return this;
        }

        public Builder assignedAt(Instant assignedAt) {
            this.assignedAt = assignedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder resolvedAt(Instant resolvedAt) {
            this.resolvedAt = resolvedAt;
            return this;
        }

        public Builder auditNotes(String auditNotes) {
            this.auditNotes = auditNotes;
            return this;
        }

        public Builder streetAddress(String streetAddress) {
            this.streetAddress = streetAddress != null ? streetAddress : "";
            return this;
        }

        public Builder estimatedCost(double estimatedCost) {
            this.estimatedCost = estimatedCost;
            return this;
        }

        public Builder claimedCost(double claimedCost) {
            this.claimedCost = claimedCost;
            return this;
        }

        public Builder materialItemization(String materialItemization) {
            this.materialItemization = materialItemization != null ? materialItemization : "";
            return this;
        }

        public GovernanceTicket build() {
            double finalEst = estimatedCost > 0.0 ? estimatedCost : switch (severity) {
                case CRITICAL -> 25000.0;
                case HIGH -> 18500.0;
                case MEDIUM -> 14000.0;
                case LOW -> 8500.0;
            };
            return new GovernanceTicket(
                    ticketId,
                    latitude,
                    longitude,
                    Collections.unmodifiableList(photoPaths),
                    severity,
                    status,
                    jurisdiction,
                    assignedContractor,
                    inWarrantyDlp,
                    dlpWarrantyContractor,
                    beforeImagePath,
                    stage2ImagePath,
                    afterImagePath,
                    Collections.unmodifiableList(materialRequests),
                    auditApprovalStatus,
                    sampledForAudit,
                    surveyorEndorsed,
                    surveyorNotes,
                    description,
                    reportedAt,
                    assignedAt,
                    completedAt,
                    resolvedAt,
                    auditNotes,
                    streetAddress,
                    finalEst,
                    claimedCost,
                    materialItemization);
        }
    }
}
