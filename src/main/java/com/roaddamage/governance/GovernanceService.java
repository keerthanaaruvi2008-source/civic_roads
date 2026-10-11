package com.roaddamage.governance;

import com.roaddamage.core.model.SeverityLevel;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Thread-safe governance service and state machine orchestrating multi-role road asset management.
 * <p>
 * Manages ticket creation, departmental triage, defect liability (DLP) warranty tracking,
 * contractor site progression, statistical quality audits, SLA monitoring, and payment authorizations.
 */
public class GovernanceService {

    public static final double TOTAL_MUNICIPAL_BUDGET = 500_000.0; // ₹5,00,000 Total Pool
    private static final double DEFAULT_DLP_RADIUS_METERS = 100.0;
    private static final int DEFAULT_DLP_WARRANTY_DAYS = 365;
    private static final double DEFAULT_AUDIT_SAMPLING_RATE = 0.30;

    private static final GovernanceService SHARED_INSTANCE = new GovernanceService();
    static {
        SHARED_INSTANCE.seedDemoData();
    }

    /**
     * Retrieves the globally shared, thread-safe GovernanceService instance.
     *
     * @return shared governance service
     */
    public static GovernanceService getInstance() {
        return SHARED_INSTANCE;
    }

    private final Map<String, GovernanceTicket> ticketStore = new ConcurrentHashMap<>();
    private final List<Consumer<GovernanceTicket>> changeListeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger ticketCounter = new AtomicInteger(1000);
    private final Random random;

    private volatile double dlpRadiusMeters = DEFAULT_DLP_RADIUS_METERS;
    private volatile int dlpWarrantyDays = DEFAULT_DLP_WARRANTY_DAYS;
    private volatile double auditSamplingRate = DEFAULT_AUDIT_SAMPLING_RATE;

    /**
     * Initializes service with default pseudorandom generator.
     */
    public GovernanceService() {
        this(new Random());
    }

    /**
     * Initializes service with a provided random source for deterministic testing.
     *
     * @param random random instance
     */
    public GovernanceService(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Registers a listener triggered on any ticket creation or state transition.
     *
     * @param listener ticket update consumer
     */
    public void addTicketChangeListener(Consumer<GovernanceTicket> listener) {
        changeListeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /**
     * Removes a registered ticket change listener.
     *
     * @param listener listener to remove
     */
    public void removeTicketChangeListener(Consumer<GovernanceTicket> listener) {
        changeListeners.remove(listener);
    }

    private void notifyListeners(GovernanceTicket ticket) {
        for (Consumer<GovernanceTicket> listener : changeListeners) {
            try {
                listener.accept(ticket);
            } catch (Exception ignored) {
                // Prevent listener errors from aborting state transition
            }
        }
    }

    // =========================================================================
    // MUNICIPAL BUDGET LEDGER
    // =========================================================================

    /**
     * Total municipal allocation pool (₹5,00,000).
     */
    public double getTotalBudget() {
        return TOTAL_MUNICIPAL_BUDGET;
    }

    /**
     * Pending contractor claims awaiting supervisor audit sign-off (reserved from available).
     */
    public double getPendingDisbursements() {
        return ticketStore.values().stream()
                .filter(t -> (t.status() == TicketStatus.COMPLETED &&
                        (t.auditApprovalStatus() == AuditApprovalStatus.PENDING ||
                         t.auditApprovalStatus() == AuditApprovalStatus.PENDING_SURVEYOR_ENDORSEMENT ||
                         t.auditApprovalStatus() == AuditApprovalStatus.SURVEYOR_ENDORSED))
                        || (t.claimedCost() > 0 &&
                        (t.auditApprovalStatus() == AuditApprovalStatus.PENDING ||
                         t.auditApprovalStatus() == AuditApprovalStatus.PENDING_SURVEYOR_ENDORSEMENT ||
                         t.auditApprovalStatus() == AuditApprovalStatus.SURVEYOR_ENDORSED)))
                .mapToDouble(t -> t.claimedCost() > 0 ? t.claimedCost() : t.estimatedCost())
                .sum();
    }

    /**
     * Cumulative approved and disbursed contractor funds.
     */
    public double getApprovedDisbursements() {
        return ticketStore.values().stream()
                .filter(t -> t.status() == TicketStatus.RESOLVED && t.auditApprovalStatus() == AuditApprovalStatus.APPROVED)
                .mapToDouble(t -> t.claimedCost() > 0 ? t.claimedCost() : t.estimatedCost())
                .sum();
    }

    /**
     * Current uncommitted available balance in municipal ledger.
     */
    public double getAvailableBudget() {
        double avail = TOTAL_MUNICIPAL_BUDGET - getApprovedDisbursements() - getPendingDisbursements();
        return Math.max(0.0, avail);
    }

    /**
     * Percentage of municipal budget pool disbursed to date.
     */
    public double getDisbursedPercentage() {
        return (getApprovedDisbursements() / TOTAL_MUNICIPAL_BUDGET) * 100.0;
    }

    // =========================================================================
    // STATE MACHINE TRANSITIONS
    // =========================================================================

    /**
     * Creates a new public grievance ticket submitted by a citizen.
     */
    public GovernanceTicket createTicket(
            UserRole actorRole,
            double latitude,
            double longitude,
            SeverityLevel severity,
            List<String> photoPaths,
            String description) {
        return createTicket(actorRole, latitude, longitude, severity, photoPaths, description, "");
    }

    /**
     * Creates a new public grievance ticket with street address / landmark.
     */
    public GovernanceTicket createTicket(
            UserRole actorRole,
            double latitude,
            double longitude,
            SeverityLevel severity,
            List<String> photoPaths,
            String description,
            String streetAddress) {

        enforceRole(actorRole, List.of(UserRole.CITIZEN, UserRole.NODAL_OFFICER), "create ticket");

        String ticketId = "TKT-" + ticketCounter.incrementAndGet();

        // Check Defect Liability Period (DLP) against resolved tickets in vicinity
        Optional<GovernanceTicket> priorWarrantyTicket = checkDefectLiabilityWarranty(latitude, longitude);
        boolean inWarranty = priorWarrantyTicket.isPresent();
        String warrantyContractor = priorWarrantyTicket.map(GovernanceTicket::assignedContractor).orElse(null);

        GovernanceTicket ticket = GovernanceTicket.builder(ticketId, latitude, longitude, severity)
                .photoPaths(photoPaths)
                .description(description)
                .streetAddress(streetAddress)
                .status(TicketStatus.REPORTED)
                .inWarrantyDlp(inWarranty)
                .dlpWarrantyContractor(warrantyContractor)
                .reportedAt(Instant.now())
                .build();

        ticketStore.put(ticketId, ticket);
        notifyListeners(ticket);
        return ticket;
    }

    /**
     * Nodal Officer assigns administrative jurisdiction and repair contractor.
     *
     * @param ticketId grievance identifier
     * @param actorRole executing role (must be NODAL_OFFICER)
     * @param jurisdiction administrative road authority
     * @param contractor designated contracting firm
     * @return updated ticket
     */
    public synchronized GovernanceTicket assignDepartment(
            String ticketId,
            UserRole actorRole,
            Jurisdiction jurisdiction,
            String contractor) {

        enforceRole(actorRole, List.of(UserRole.NODAL_OFFICER), "assign department");
        GovernanceTicket existing = getRequiredTicket(ticketId);

        if (existing.status() != TicketStatus.REPORTED) {
            throw new GovernanceException(String.format(
                    "Ticket %s cannot be assigned from status %s (must be in REPORTED state)",
                    ticketId, existing.status()));
        }

        Objects.requireNonNull(jurisdiction, "jurisdiction must not be null");
        if (contractor == null || contractor.isBlank()) {
            throw new GovernanceException("Assigned contractor must not be blank");
        }

        GovernanceTicket updated = existing.toBuilder()
                .jurisdiction(jurisdiction)
                .assignedContractor(contractor)
                .status(TicketStatus.ASSIGNED)
                .assignedAt(Instant.now())
                .build();

        ticketStore.put(ticketId, updated);
        notifyListeners(updated);
        return updated;
    }

    /**
     * Site Engineer starts physical repair on an assigned work order.
     *
     * @param ticketId grievance identifier
     * @param actorRole executing role (must be SITE_ENGINEER)
     * @return updated ticket in IN_PROGRESS state
     */
    public synchronized GovernanceTicket startWork(String ticketId, UserRole actorRole) {
        enforceRole(actorRole, List.of(UserRole.SITE_ENGINEER), "start work order");
        GovernanceTicket existing = getRequiredTicket(ticketId);

        if (existing.status() != TicketStatus.ASSIGNED) {
            throw new GovernanceException(String.format(
                    "Ticket %s cannot transition to IN_PROGRESS from %s (must be ASSIGNED)",
                    ticketId, existing.status()));
        }

        GovernanceTicket updated = existing.toBuilder()
                .status(TicketStatus.IN_PROGRESS)
                .build();

        ticketStore.put(ticketId, updated);
        notifyListeners(updated);
        return updated;
    }

    /**
     * Site Engineer uploads an in-progress milestone photo documenting active repair/cold mix work,
     * transitioning the ticket from ASSIGNED to IN_PROGRESS.
     *
     * @param ticketId grievance identifier
     * @param actorRole executing role (must be SITE_ENGINEER)
     * @param inProgressPhoto path to in-progress photo proof
     * @return updated ticket in IN_PROGRESS state
     */
    public synchronized GovernanceTicket uploadInProgressPhoto(
            String ticketId,
            UserRole actorRole,
            String inProgressPhoto) {

        enforceRole(actorRole, List.of(UserRole.SITE_ENGINEER), "upload in-progress photo");
        GovernanceTicket existing = getRequiredTicket(ticketId);

        if (existing.status() != TicketStatus.ASSIGNED && existing.status() != TicketStatus.IN_PROGRESS) {
            throw new GovernanceException(String.format(
                    "Ticket %s cannot record in-progress milestone in status %s (must be ASSIGNED or IN_PROGRESS)",
                    ticketId, existing.status()));
        }

        if (inProgressPhoto == null || inProgressPhoto.isBlank()) {
            throw new GovernanceException("In-progress image proof is mandatory");
        }

        GovernanceTicket updated = existing.toBuilder()
                .stage2ImagePath(inProgressPhoto.trim())
                .status(TicketStatus.IN_PROGRESS)
                .build();

        ticketStore.put(ticketId, updated);
        notifyListeners(updated);
        return updated;
    }

    /**
     * Site Engineer submits a procurement material request (e.g., Cold Mix Asphalt, Bitumen Emulsion).
     *
     * @param ticketId grievance identifier
     * @param actorRole executing role (must be SITE_ENGINEER)
     * @param materialItem description and quantity of material
     * @return updated ticket
     */
    public synchronized GovernanceTicket requestMaterials(
            String ticketId,
            UserRole actorRole,
            String materialItem) {

        enforceRole(actorRole, List.of(UserRole.SITE_ENGINEER), "request materials");
        GovernanceTicket existing = getRequiredTicket(ticketId);

        if (existing.status() != TicketStatus.ASSIGNED && existing.status() != TicketStatus.IN_PROGRESS) {
            throw new GovernanceException(String.format(
                    "Materials cannot be requested for ticket %s in status %s",
                    ticketId, existing.status()));
        }

        if (materialItem == null || materialItem.isBlank()) {
            throw new GovernanceException("Material item must not be blank");
        }

        GovernanceTicket updated = existing.toBuilder()
                .addMaterialRequest(materialItem.trim())
                .build();

        ticketStore.put(ticketId, updated);
        notifyListeners(updated);
        return updated;
    }

    /**
     * Site Engineer submits before/after photographic proofs and completes the work order.
     * Random quality audit sampling is evaluated at this transition.
     *
     * @param ticketId grievance identifier
     * @param actorRole executing role (must be SITE_ENGINEER)
     * @param beforeImagePath path to photo before repair
     * @param afterImagePath path to photo after repair completion
     * @param additionalMaterials optional material items to record
     * @return updated ticket in COMPLETED state
     */
    public synchronized GovernanceTicket completeWork(
            String ticketId,
            UserRole actorRole,
            String beforeImagePath,
            String stage2ImagePath,
            String afterImagePath,
            List<String> additionalMaterials) {

        enforceRole(actorRole, List.of(UserRole.SITE_ENGINEER), "complete work order");
        GovernanceTicket existing = getRequiredTicket(ticketId);

        if (existing.status() != TicketStatus.IN_PROGRESS) {
            throw new GovernanceException(String.format(
                    "Ticket %s cannot transition to COMPLETED from %s (must be IN_PROGRESS)",
                    ticketId, existing.status()));
        }

        if (beforeImagePath == null || beforeImagePath.isBlank()) {
            throw new GovernanceException("Before-repair image proof is mandatory to complete work order");
        }
        if (afterImagePath == null || afterImagePath.isBlank()) {
            throw new GovernanceException("After-repair image proof is mandatory to complete work order");
        }

        // Evaluate statistical random sampling
        boolean sampled = random.nextDouble() < auditSamplingRate;
        AuditApprovalStatus auditStatus = sampled ? AuditApprovalStatus.PENDING : AuditApprovalStatus.NOT_REQUIRED;

        GovernanceTicket.Builder builder = existing.toBuilder()
                .beforeImagePath(beforeImagePath.trim())
                .stage2ImagePath(stage2ImagePath != null && !stage2ImagePath.isBlank() ? stage2ImagePath.trim() : "samples/work_in_progress_compaction.jpg")
                .afterImagePath(afterImagePath.trim())
                .status(TicketStatus.COMPLETED)
                .completedAt(Instant.now())
                .sampledForAudit(sampled)
                .auditApprovalStatus(auditStatus);

        if (additionalMaterials != null) {
            for (String item : additionalMaterials) {
                builder.addMaterialRequest(item);
            }
        }

        GovernanceTicket updated = builder.build();
        ticketStore.put(ticketId, updated);
        notifyListeners(updated);
        return updated;
    }

    public synchronized GovernanceTicket completeWork(
            String ticketId,
            UserRole actorRole,
            String beforeImagePath,
            String afterImagePath,
            List<String> additionalMaterials) {
        return completeWork(ticketId, actorRole, beforeImagePath, "samples/work_in_progress_compaction.jpg", afterImagePath, additionalMaterials);
    }

    /**
     * Site Contractor submits material itemization, claimed repair cost, and Before/After photo proofs.
     * Transitions ticket state to COMPLETED with PENDING audit approval.
     *
     * @param ticketId grievance identifier
     * @param actorRole executing role (must be SITE_ENGINEER)
     * @param beforeImagePath path to photo before repair
     * @param afterImagePath path to photo after repair completion
     * @param materialItemization detailed asphalt/bitumen requisition
     * @param claimedCost monetary repair claim in INR
     * @return updated ticket in COMPLETED state pending audit
     */
    public synchronized GovernanceTicket submitContractorClaim(
            String ticketId,
            UserRole actorRole,
            String beforeImagePath,
            String stage2ImagePath,
            String afterImagePath,
            String materialItemization,
            double claimedCost) {

        enforceRole(actorRole, List.of(UserRole.SITE_ENGINEER), "submit contractor claim");
        GovernanceTicket existing = getRequiredTicket(ticketId);

        if (existing.status() != TicketStatus.IN_PROGRESS && existing.status() != TicketStatus.ASSIGNED) {
            throw new GovernanceException(String.format(
                    "Ticket %s cannot submit claim in status %s (must be IN_PROGRESS or ASSIGNED)",
                    ticketId, existing.status()));
        }

        if (beforeImagePath == null || beforeImagePath.isBlank()) {
            throw new GovernanceException("Stage 1 (Before repair) image proof is mandatory");
        }
        if (stage2ImagePath == null || stage2ImagePath.isBlank()) {
            stage2ImagePath = "samples/work_in_progress_compaction.jpg";
        }
        if (afterImagePath == null || afterImagePath.isBlank()) {
            throw new GovernanceException("Stage 3 (After completion) image proof is mandatory");
        }

        double finalClaim = claimedCost > 0.0 ? claimedCost : existing.estimatedCost();

        GovernanceTicket.Builder builder = existing.toBuilder()
                .beforeImagePath(beforeImagePath.trim())
                .stage2ImagePath(stage2ImagePath.trim())
                .afterImagePath(afterImagePath.trim())
                .materialItemization(materialItemization != null ? materialItemization.trim() : "")
                .claimedCost(finalClaim)
                .status(TicketStatus.COMPLETED)
                .sampledForAudit(true)
                .surveyorEndorsed(false)
                .auditApprovalStatus(AuditApprovalStatus.PENDING_SURVEYOR_ENDORSEMENT)
                .completedAt(Instant.now());

        if (materialItemization != null && !materialItemization.isBlank()) {
            builder.addMaterialRequest(materialItemization.trim());
        }

        GovernanceTicket updated = builder.build();
        ticketStore.put(ticketId, updated);
        notifyListeners(updated);
        return updated;
    }

    public synchronized GovernanceTicket submitContractorClaim(
            String ticketId,
            UserRole actorRole,
            String beforeImagePath,
            String afterImagePath,
            String materialItemization,
            double claimedCost) {
        return submitContractorClaim(ticketId, actorRole, beforeImagePath, "samples/work_in_progress_compaction.jpg", afterImagePath, materialItemization, claimedCost);
    }

    /**
     * Field Surveyor inspects physical site execution and material itemization,
     * and either endorses funding to the Supervisor or rejects the claim back for rework.
     *
     * @param ticketId grievance identifier
     * @param actorRole executing role (SURVEYOR or NODAL_OFFICER)
     * @param endorse true to endorse and forward to Supervisor; false to reject
     * @param surveyorNotes verification observations
     * @return updated ticket
     */
    public synchronized GovernanceTicket endorseBySurveyor(
            String ticketId,
            UserRole actorRole,
            boolean endorse,
            String surveyorNotes) {

        enforceRole(actorRole, List.of(UserRole.SURVEYOR, UserRole.NODAL_OFFICER), "endorse contractor funding");
        GovernanceTicket existing = getRequiredTicket(ticketId);

        if (existing.status() != TicketStatus.COMPLETED) {
            throw new GovernanceException(String.format(
                    "Ticket %s cannot be endorsed in status %s (must be COMPLETED)",
                    ticketId, existing.status()));
        }

        GovernanceTicket.Builder builder = existing.toBuilder()
                .surveyorNotes(surveyorNotes != null ? surveyorNotes.trim() : "");

        if (endorse) {
            builder.surveyorEndorsed(true)
                    .auditApprovalStatus(AuditApprovalStatus.SURVEYOR_ENDORSED);
        } else {
            builder.surveyorEndorsed(false)
                    .status(TicketStatus.IN_PROGRESS)
                    .auditApprovalStatus(AuditApprovalStatus.REJECTED);
        }

        GovernanceTicket updated = builder.build();
        ticketStore.put(ticketId, updated);
        notifyListeners(updated);
        return updated;
    }

    /**
     * Quality Auditor (Supervisor) audits a completed work order and authorizes or rejects invoice sign-off.
     *
     * @param ticketId grievance identifier
     * @param actorRole executing role (must be QUALITY_AUDITOR)
     * @param approve true to approve payment and resolve ticket; false to reject and demand rework
     * @param auditNotes auditor feedback or rejection rationale
     * @return updated ticket
     */
    public synchronized GovernanceTicket auditTicket(
            String ticketId,
            UserRole actorRole,
            boolean approve,
            String auditNotes) {

        enforceRole(actorRole, List.of(UserRole.QUALITY_AUDITOR), "audit ticket sign-off");
        GovernanceTicket existing = getRequiredTicket(ticketId);

        if (existing.status() != TicketStatus.COMPLETED) {
            throw new GovernanceException(String.format(
                    "Ticket %s cannot be audited in status %s (must be COMPLETED)",
                    ticketId, existing.status()));
        }

        GovernanceTicket.Builder builder = existing.toBuilder()
                .auditNotes(auditNotes != null ? auditNotes.trim() : "");

        if (approve) {
            builder.status(TicketStatus.RESOLVED)
                    .surveyorEndorsed(true)
                    .auditApprovalStatus(AuditApprovalStatus.APPROVED)
                    .resolvedAt(Instant.now());
        } else {
            // Reject back to IN_PROGRESS for contractor rectification
            builder.status(TicketStatus.IN_PROGRESS)
                    .surveyorEndorsed(false)
                    .auditApprovalStatus(AuditApprovalStatus.REJECTED);
        }

        GovernanceTicket updated = builder.build();
        ticketStore.put(ticketId, updated);
        notifyListeners(updated);
        return updated;
    }

    /**
     * Closes an unsampled completed ticket directly into RESOLVED state by Nodal Officer or Quality Auditor.
     *
     * @param ticketId grievance identifier
     * @param actorRole executing role (NODAL_OFFICER or QUALITY_AUDITOR)
     * @return updated ticket in RESOLVED state
     */
    public synchronized GovernanceTicket resolveUnsampledTicket(String ticketId, UserRole actorRole) {
        enforceRole(actorRole, List.of(UserRole.NODAL_OFFICER, UserRole.QUALITY_AUDITOR), "resolve unsampled ticket");
        GovernanceTicket existing = getRequiredTicket(ticketId);

        if (existing.status() != TicketStatus.COMPLETED) {
            throw new GovernanceException(String.format(
                    "Ticket %s cannot be resolved from status %s (must be COMPLETED)",
                    ticketId, existing.status()));
        }

        GovernanceTicket updated = existing.toBuilder()
                .status(TicketStatus.RESOLVED)
                .resolvedAt(Instant.now())
                .auditApprovalStatus(existing.sampledForAudit() ? AuditApprovalStatus.APPROVED : AuditApprovalStatus.NOT_REQUIRED)
                .build();

        ticketStore.put(ticketId, updated);
        notifyListeners(updated);
        return updated;
    }

    // =========================================================================
    // DEFECT LIABILITY PERIOD (DLP) & GEOSPATIAL LOGIC
    // =========================================================================

    /**
     * Checks if a new distress coordinate falls within the defect liability period of a previously resolved repair.
     *
     * @param lat defect latitude
     * @param lon defect longitude
     * @return prior resolved ticket within warranty window and radius, if any
     */
    public Optional<GovernanceTicket> checkDefectLiabilityWarranty(double lat, double lon) {
        Instant cutoff = Instant.now().minus(dlpWarrantyDays, ChronoUnit.DAYS);

        return ticketStore.values().stream()
                .filter(t -> t.status() == TicketStatus.RESOLVED)
                .filter(t -> t.resolvedAt() != null && t.resolvedAt().isAfter(cutoff))
                .filter(t -> haversineDistanceMeters(lat, lon, t.latitude(), t.longitude()) <= dlpRadiusMeters)
                .findFirst();
    }

    /**
     * Calculates great-circle distance between two GPS coordinates using the Haversine formula.
     *
     * @param lat1 latitude 1
     * @param lon1 longitude 1
     * @param lat2 latitude 2
     * @param lon2 longitude 2
     * @return distance in meters
     */
    public static double haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return r * c;
    }

    // =========================================================================
    // METRICS, SLA & MTTR
    // =========================================================================

    /**
     * Computes Mean Time To Resolution (MTTR) in decimal hours across all resolved tickets.
     *
     * @return average turnaround in hours, or 0.0 if no tickets resolved
     */
    public double getMeanTimeToResolutionHours() {
        List<GovernanceTicket> resolved = ticketStore.values().stream()
                .filter(t -> t.status() == TicketStatus.RESOLVED && t.resolvedAt() != null && t.reportedAt() != null)
                .toList();

        if (resolved.isEmpty()) {
            return 0.0;
        }

        long totalSeconds = 0;
        for (GovernanceTicket t : resolved) {
            totalSeconds += Duration.between(t.reportedAt(), t.resolvedAt()).getSeconds();
        }

        return (double) totalSeconds / (resolved.size() * 3600.0);
    }

    /**
     * Resolves statutory SLA duration limit in hours for a given severity tier.
     *
     * @param severity distress severity
     * @return target turnaround in hours
     */
    public static long getTargetSlaHours(SeverityLevel severity) {
        return switch (severity) {
            case CRITICAL -> 24L;
            case HIGH -> 48L;
            case MEDIUM -> 168L; // 7 days
            case LOW -> 336L;    // 14 days
        };
    }

    /**
     * Calculates SLA compliance percentage across all resolved tickets.
     *
     * @return percentage [0.0, 100.0] of resolved tickets completed within SLA deadline
     */
    public double getSlaCompliancePercentage() {
        List<GovernanceTicket> resolved = ticketStore.values().stream()
                .filter(t -> t.status() == TicketStatus.RESOLVED && t.resolvedAt() != null && t.reportedAt() != null)
                .toList();

        if (resolved.isEmpty()) {
            return 100.0;
        }

        long compliantCount = 0;
        for (GovernanceTicket t : resolved) {
            long actualHours = Duration.between(t.reportedAt(), t.resolvedAt()).toHours();
            long allowedHours = getTargetSlaHours(t.severity());
            if (actualHours <= allowedHours) {
                compliantCount++;
            }
        }

        return (double) compliantCount / resolved.size() * 100.0;
    }

    // =========================================================================
    // QUERIES & HELPERS
    // =========================================================================

    public GovernanceTicket getRequiredTicket(String ticketId) {
        GovernanceTicket ticket = ticketStore.get(ticketId);
        if (ticket == null) {
            throw new GovernanceException("Ticket not found: " + ticketId);
        }
        return ticket;
    }

    public Optional<GovernanceTicket> findTicket(String ticketId) {
        return Optional.ofNullable(ticketStore.get(ticketId));
    }

    public List<GovernanceTicket> getAllTickets() {
        List<GovernanceTicket> list = new ArrayList<>(ticketStore.values());
        list.sort(Comparator.comparing(GovernanceTicket::reportedAt).reversed());
        return Collections.unmodifiableList(list);
    }

    public List<GovernanceTicket> getTicketsByStatus(TicketStatus status) {
        return ticketStore.values().stream()
                .filter(t -> t.status() == status)
                .sorted(Comparator.comparing(GovernanceTicket::reportedAt).reversed())
                .toList();
    }

    public List<GovernanceTicket> getPendingSurveyorTickets() {
        return ticketStore.values().stream()
                .filter(t -> t.status() == TicketStatus.COMPLETED &&
                        t.auditApprovalStatus() == AuditApprovalStatus.PENDING_SURVEYOR_ENDORSEMENT)
                .sorted(Comparator.comparing(GovernanceTicket::completedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<GovernanceTicket> getAuditPendingTickets() {
        return ticketStore.values().stream()
                .filter(t -> t.status() == TicketStatus.COMPLETED &&
                        (t.auditApprovalStatus() == AuditApprovalStatus.SURVEYOR_ENDORSED ||
                         t.auditApprovalStatus() == AuditApprovalStatus.PENDING))
                .sorted(Comparator.comparing(GovernanceTicket::completedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<GovernanceTicket> getSiteEngineerWorkOrders(String contractor) {
        return ticketStore.values().stream()
                .filter(t -> t.status() == TicketStatus.ASSIGNED || t.status() == TicketStatus.IN_PROGRESS)
                .filter(t -> contractor == null || contractor.isBlank() || contractor.equalsIgnoreCase(t.assignedContractor()))
                .sorted(Comparator.comparing(GovernanceTicket::assignedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public void addTicketDirect(GovernanceTicket ticket) {
        Objects.requireNonNull(ticket, "ticket must not be null");
        ticketStore.put(ticket.ticketId(), ticket);
        notifyListeners(ticket);
    }

    public void clear() {
        ticketStore.clear();
    }

    public int totalTicketCount() {
        return ticketStore.size();
    }

    // =========================================================================
    // CONFIGURATION ACCESSORS
    // =========================================================================

    public double getAuditSamplingRate() {
        return auditSamplingRate;
    }

    public void setAuditSamplingRate(double auditSamplingRate) {
        if (auditSamplingRate < 0.0 || auditSamplingRate > 1.0) {
            throw new IllegalArgumentException("auditSamplingRate must be in [0.0, 1.0]: " + auditSamplingRate);
        }
        this.auditSamplingRate = auditSamplingRate;
    }

    public double getDlpRadiusMeters() {
        return dlpRadiusMeters;
    }

    public void setDlpRadiusMeters(double dlpRadiusMeters) {
        if (dlpRadiusMeters <= 0.0) {
            throw new IllegalArgumentException("dlpRadiusMeters must be positive: " + dlpRadiusMeters);
        }
        this.dlpRadiusMeters = dlpRadiusMeters;
    }

    public int getDlpWarrantyDays() {
        return dlpWarrantyDays;
    }

    public void setDlpWarrantyDays(int dlpWarrantyDays) {
        if (dlpWarrantyDays <= 0) {
            throw new IllegalArgumentException("dlpWarrantyDays must be positive: " + dlpWarrantyDays);
        }
        this.dlpWarrantyDays = dlpWarrantyDays;
    }

    private void enforceRole(UserRole actorRole, List<UserRole> permitted, String actionName) {
        if (actorRole == null || !permitted.contains(actorRole)) {
            throw new GovernanceException(String.format(
                    "Access Denied: Role %s is not permitted to %s (requires one of %s)",
                    actorRole, actionName, permitted));
        }
    }

    /**
     * Seeds initial demonstration records illustrating diverse workflow stages and DLP warranty scenarios.
     */
    public void seedDemoData() {
        Instant now = Instant.now();

        // 1. Previously resolved ticket (to trigger DLP for nearby defects) - Green Pin (Repaired/Approved)
        GovernanceTicket resolvedPast = GovernanceTicket.builder("TKT-0990", 13.0450, 80.2520, SeverityLevel.HIGH)
                .streetAddress("Mount Road, Anna Salai (near DMS), Chennai")
                .jurisdiction(Jurisdiction.STATE_HIGHWAY)
                .assignedContractor("Apex Infra Roadways Ltd")
                .status(TicketStatus.RESOLVED)
                .reportedAt(now.minus(45, ChronoUnit.DAYS))
                .assignedAt(now.minus(44, ChronoUnit.DAYS))
                .completedAt(now.minus(43, ChronoUnit.DAYS))
                .resolvedAt(now.minus(42, ChronoUnit.DAYS))
                .auditApprovalStatus(AuditApprovalStatus.APPROVED)
                .estimatedCost(18500.0)
                .claimedCost(18500.0)
                .materialItemization("2.5 Tonnes Cold Mix Asphalt + Bitumen Emulsion Tack Coat")
                .beforeImagePath("samples/before_pothole_1.jpg")
                .afterImagePath("samples/after_patch_1.jpg")
                .description("Major edge crack and asphalt depression near Junction 4.")
                .auditNotes("Verified high-density asphalt overlay. Meets IRC:SP:84 specifications.")
                .build();
        ticketStore.put(resolvedPast.ticketId(), resolvedPast);

        // 2. DLP Flagged ticket near the resolved past ticket! - Red Pin (Critical Pothole)
        GovernanceTicket dlpTicket = GovernanceTicket.builder("TKT-1001", 13.0455, 80.2525, SeverityLevel.CRITICAL)
                .streetAddress("Mount Road, Anna Salai (near DMS Metro), Chennai")
                .jurisdiction(Jurisdiction.STATE_HIGHWAY)
                .assignedContractor("Apex Infra Roadways Ltd")
                .inWarrantyDlp(true)
                .dlpWarrantyContractor("Apex Infra Roadways Ltd")
                .status(TicketStatus.REPORTED)
                .estimatedCost(25000.0)
                .beforeImagePath("samples/user_uploaded_waterlogged_pothole.jpg")
                .reportedAt(now.minus(4, ChronoUnit.HOURS))
                .description("Recurring severe pothole at same junction. Re-opened under Defect Liability Period warranty.")
                .build();
        ticketStore.put(dlpTicket.ticketId(), dlpTicket);

        // 3. Regular assigned ticket for Urban Local Body - Amber Pin (Assigned)
        GovernanceTicket assignedTkt = GovernanceTicket.builder("TKT-1002", 13.0067, 80.2025, SeverityLevel.MEDIUM)
                .streetAddress("Inner Ring Road, Guindy, Chennai")
                .jurisdiction(Jurisdiction.URBAN_LOCAL_BODY)
                .assignedContractor("Metro Paving & Bitumen Corp")
                .status(TicketStatus.ASSIGNED)
                .estimatedCost(14000.0)
                .beforeImagePath("samples/before_pothole_1.jpg")
                .reportedAt(now.minus(18, ChronoUnit.HOURS))
                .assignedAt(now.minus(12, ChronoUnit.HOURS))
                .description("Alligator fatigue cracking on 80ft main road corridor.")
                .build();
        ticketStore.put(assignedTkt.ticketId(), assignedTkt);

        // 4. In-progress ticket with materials requested - Amber Pin (In Progress)
        GovernanceTicket inProgressTkt = GovernanceTicket.builder("TKT-1003", 12.9822, 80.1636, SeverityLevel.HIGH)
                .streetAddress("GST Road near Airport Flyover, Meenambakkam, Chennai")
                .jurisdiction(Jurisdiction.NHAI)
                .assignedContractor("Apex Infra Roadways Ltd")
                .status(TicketStatus.IN_PROGRESS)
                .estimatedCost(18500.0)
                .reportedAt(now.minus(36, ChronoUnit.HOURS))
                .assignedAt(now.minus(30, ChronoUnit.HOURS))
                .beforeImagePath("samples/before_pothole_1.jpg")
                .materialItemization("2.5 Ton Hot Mix Bituminous Concrete (Grade 1) + 200 Ltr Tack Coat RS-1")
                .addMaterialRequest("2.5 Ton Hot Mix Bituminous Concrete (Grade 1)")
                .addMaterialRequest("200 Ltr Rapid-Setting Tack Coat Emulsion RS-1")
                .description("Transverse cracking and deep rutting on lane 2.")
                .build();
        ticketStore.put(inProgressTkt.ticketId(), inProgressTkt);

        // 5. Completed ticket pending Field Surveyor Endorsement (3-Stage Proofs)
        GovernanceTicket completedSampledTkt = GovernanceTicket.builder("TKT-1004", 12.9352, 80.2337, SeverityLevel.HIGH)
                .streetAddress("OMR IT Expressway near Thoraipakkam Toll, Chennai")
                .jurisdiction(Jurisdiction.NHAI)
                .assignedContractor("Apex Infra Roadways Ltd")
                .status(TicketStatus.COMPLETED)
                .sampledForAudit(true)
                .surveyorEndorsed(false)
                .auditApprovalStatus(AuditApprovalStatus.PENDING_SURVEYOR_ENDORSEMENT)
                .estimatedCost(18500.0)
                .claimedCost(19200.0)
                .materialItemization("2.5 Tonnes Cold Mix Asphalt + Bitumen Emulsion Tack Coat")
                .reportedAt(now.minus(28, ChronoUnit.HOURS))
                .assignedAt(now.minus(24, ChronoUnit.HOURS))
                .completedAt(now.minus(3, ChronoUnit.HOURS))
                .beforeImagePath("samples/user_uploaded_waterlogged_pothole.jpg")
                .stage2ImagePath("samples/work_in_progress_compaction.jpg")
                .afterImagePath("samples/after_patch_1.jpg")
                .addMaterialRequest("2.5 Tonnes Cold Mix Asphalt + Bitumen Emulsion Tack Coat")
                .description("Deep waterlogged pothole cavity causing vehicular deflection.")
                .build();
        ticketStore.put(completedSampledTkt.ticketId(), completedSampledTkt);

        // 6. Previously Surveyor-Endorsed ticket awaiting final Supervisor Disbursal
        GovernanceTicket surveyorEndorsedTkt = GovernanceTicket.builder("TKT-1006", 13.0450, 80.2420, SeverityLevel.HIGH)
                .streetAddress("Anna Salai Corridor near DMS (Surveyor Verified), Chennai")
                .jurisdiction(Jurisdiction.STATE_HIGHWAY)
                .assignedContractor("Apex Infra Roadways Ltd")
                .status(TicketStatus.COMPLETED)
                .sampledForAudit(true)
                .surveyorEndorsed(true)
                .surveyorNotes("Site compaction depth verified at 5.2cm. Grade meets IRC:SP:84. Recommended for budget release.")
                .auditApprovalStatus(AuditApprovalStatus.SURVEYOR_ENDORSED)
                .estimatedCost(18500.0)
                .claimedCost(18500.0)
                .materialItemization("2.5 Tonnes Hot Mix Asphalt + Tack Coat Emulsion")
                .reportedAt(now.minus(30, ChronoUnit.HOURS))
                .assignedAt(now.minus(26, ChronoUnit.HOURS))
                .completedAt(now.minus(5, ChronoUnit.HOURS))
                .beforeImagePath("samples/before_pothole_1.jpg")
                .stage2ImagePath("samples/work_in_progress_compaction.jpg")
                .afterImagePath("samples/after_patch_1.jpg")
                .description("Longitudinal surface distress rectified with full-depth hot mix inlay.")
                .build();
        ticketStore.put(surveyorEndorsedTkt.ticketId(), surveyorEndorsedTkt);

        // 7. Additional critical ticket reported on arterial road - Red Pin
        GovernanceTicket reportedTkt2 = GovernanceTicket.builder("TKT-1005", 13.0780, 80.2430, SeverityLevel.CRITICAL)
                .streetAddress("Poonamallee High Road near Kilpauk Medical, Chennai")
                .jurisdiction(Jurisdiction.STATE_HIGHWAY)
                .status(TicketStatus.REPORTED)
                .estimatedCost(25000.0)
                .beforeImagePath("samples/annotated_pothole_basin.jpg")
                .reportedAt(now.minus(2, ChronoUnit.HOURS))
                .description("Severe waterlogged cavity in right carriageway risking two-wheeler accidents.")
                .build();
        ticketStore.put(reportedTkt2.ticketId(), reportedTkt2);
    }
}
