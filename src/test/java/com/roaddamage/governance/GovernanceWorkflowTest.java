package com.roaddamage.governance;

import com.roaddamage.core.model.SeverityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verification test suite for multi-role road damage governance,
 * state machine transitions, role permission enforcement, and DLP warranty checks.
 */
class GovernanceWorkflowTest {

    private GovernanceService service;

    @BeforeEach
    void setUp() {
        service = new GovernanceService(new Random(42));
    }

    @Test
    @DisplayName("Happy Path: Full lifecycle from Citizen report to Auditor approval")
    void testFullLifecycleProgression() {
        // 1. Citizen creates ticket
        GovernanceTicket t1 = service.createTicket(
                UserRole.CITIZEN,
                12.9716, 77.5946,
                SeverityLevel.HIGH,
                List.of("photos/distress_01.jpg"),
                "Deep pothole near central junction");

        assertNotNull(t1.ticketId());
        assertEquals(TicketStatus.REPORTED, t1.status());
        assertFalse(t1.inWarrantyDlp());
        assertNull(t1.assignedContractor());

        // 2. Nodal Officer triages and assigns department and contractor
        GovernanceTicket assigned = service.assignDepartment(
                t1.ticketId(),
                UserRole.NODAL_OFFICER,
                Jurisdiction.STATE_HIGHWAY,
                "Apex Infra Roadways Ltd");

        assertEquals(TicketStatus.ASSIGNED, assigned.status());
        assertEquals(Jurisdiction.STATE_HIGHWAY, assigned.jurisdiction());
        assertEquals("Apex Infra Roadways Ltd", assigned.assignedContractor());
        assertNotNull(assigned.assignedAt());

        // 3. Site Engineer starts repair work
        GovernanceTicket inProgress = service.startWork(t1.ticketId(), UserRole.SITE_ENGINEER);
        assertEquals(TicketStatus.IN_PROGRESS, inProgress.status());

        // Site Engineer logs material request
        GovernanceTicket withMaterials = service.requestMaterials(
                t1.ticketId(),
                UserRole.SITE_ENGINEER,
                "2.5 Ton Bituminous Concrete Grade 2");
        assertEquals(1, withMaterials.materialRequests().size());
        assertEquals("2.5 Ton Bituminous Concrete Grade 2", withMaterials.materialRequests().get(0));

        // 4. Site Engineer completes work with required photographic proofs
        service.setAuditSamplingRate(1.0); // Ensure sampled for audit
        GovernanceTicket completed = service.completeWork(
                t1.ticketId(),
                UserRole.SITE_ENGINEER,
                "proofs/before_01.jpg",
                "proofs/after_01.jpg",
                List.of("100L Rapid Tack Coat"));

        assertEquals(TicketStatus.COMPLETED, completed.status());
        assertEquals("proofs/before_01.jpg", completed.beforeImagePath());
        assertEquals("proofs/after_01.jpg", completed.afterImagePath());
        assertTrue(completed.sampledForAudit());
        assertEquals(AuditApprovalStatus.PENDING, completed.auditApprovalStatus());
        assertNotNull(completed.completedAt());

        // 5. Quality Auditor verifies and authorizes invoice release
        GovernanceTicket resolved = service.auditTicket(
                t1.ticketId(),
                UserRole.QUALITY_AUDITOR,
                true,
                "Site compaction and grade verified compliant with IRC:SP:84.");

        assertEquals(TicketStatus.RESOLVED, resolved.status());
        assertEquals(AuditApprovalStatus.APPROVED, resolved.auditApprovalStatus());
        assertNotNull(resolved.resolvedAt());
        assertTrue(resolved.auditNotes().contains("IRC:SP:84"));
    }

    @Test
    @DisplayName("Role permission enforcement prevents unauthorized actions")
    void testRolePermissionEnforcement() {
        GovernanceTicket ticket = service.createTicket(
                UserRole.CITIZEN,
                12.9716, 77.5946,
                SeverityLevel.MEDIUM,
                List.of("photo.jpg"),
                "Edge cracking");

        // Citizen cannot assign department
        GovernanceException ex1 = assertThrows(GovernanceException.class, () ->
                service.assignDepartment(ticket.ticketId(), UserRole.CITIZEN, Jurisdiction.NHAI, "Contractor X"));
        assertTrue(ex1.getMessage().contains("Access Denied"));

        // Site Engineer cannot assign department
        assertThrows(GovernanceException.class, () ->
                service.assignDepartment(ticket.ticketId(), UserRole.SITE_ENGINEER, Jurisdiction.NHAI, "Contractor X"));

        // Assign properly by Nodal Officer
        service.assignDepartment(ticket.ticketId(), UserRole.NODAL_OFFICER, Jurisdiction.NHAI, "Contractor X");

        // Citizen cannot start work
        assertThrows(GovernanceException.class, () ->
                service.startWork(ticket.ticketId(), UserRole.CITIZEN));

        // Nodal Officer cannot complete work (only site engineer)
        assertThrows(GovernanceException.class, () ->
                service.completeWork(ticket.ticketId(), UserRole.NODAL_OFFICER, "b.jpg", "a.jpg", null));

        // Start work by Site Engineer
        service.startWork(ticket.ticketId(), UserRole.SITE_ENGINEER);
        service.completeWork(ticket.ticketId(), UserRole.SITE_ENGINEER, "b.jpg", "a.jpg", null);

        // Site Engineer cannot audit or approve payments
        assertThrows(GovernanceException.class, () ->
                service.auditTicket(ticket.ticketId(), UserRole.SITE_ENGINEER, true, "Self approval"));
    }

    @Test
    @DisplayName("State machine transitions enforce strict lifecycle sequence")
    void testIllegalStateTransitions() {
        GovernanceTicket ticket = service.createTicket(
                UserRole.CITIZEN,
                12.9716, 77.5946,
                SeverityLevel.LOW,
                List.of("photo.jpg"),
                "Minor pothole");

        // Cannot start work from REPORTED (must be ASSIGNED)
        GovernanceException ex1 = assertThrows(GovernanceException.class, () ->
                service.startWork(ticket.ticketId(), UserRole.SITE_ENGINEER));
        assertTrue(ex1.getMessage().contains("cannot transition to IN_PROGRESS"));

        // Cannot complete work from REPORTED
        assertThrows(GovernanceException.class, () ->
                service.completeWork(ticket.ticketId(), UserRole.SITE_ENGINEER, "b.jpg", "a.jpg", null));

        // Cannot audit ticket from REPORTED
        assertThrows(GovernanceException.class, () ->
                service.auditTicket(ticket.ticketId(), UserRole.QUALITY_AUDITOR, true, "Too early"));

        // Move to ASSIGNED
        service.assignDepartment(ticket.ticketId(), UserRole.NODAL_OFFICER, Jurisdiction.URBAN_LOCAL_BODY, "Corp Crew");

        // Move to IN_PROGRESS
        service.startWork(ticket.ticketId(), UserRole.SITE_ENGINEER);

        // Cannot complete work without mandatory before/after proofs
        assertThrows(GovernanceException.class, () ->
                service.completeWork(ticket.ticketId(), UserRole.SITE_ENGINEER, "", "a.jpg", null));
        assertThrows(GovernanceException.class, () ->
                service.completeWork(ticket.ticketId(), UserRole.SITE_ENGINEER, "b.jpg", null, null));
    }

    @Test
    @DisplayName("Defect Liability Period (DLP) automatically flags recurring defect within warranty geofence")
    void testDefectLiabilityWarrantyCheck() {
        // Step 1: Create, assign, complete and resolve initial repair
        GovernanceTicket initial = service.createTicket(
                UserRole.CITIZEN,
                12.97160, 77.59460,
                SeverityLevel.HIGH,
                List.of("before.jpg"),
                "Original structural pothole");

        service.assignDepartment(initial.ticketId(), UserRole.NODAL_OFFICER, Jurisdiction.STATE_HIGHWAY, "Roadbuilders Corp");
        service.startWork(initial.ticketId(), UserRole.SITE_ENGINEER);
        service.completeWork(initial.ticketId(), UserRole.SITE_ENGINEER, "b.jpg", "a.jpg", null);
        service.auditTicket(initial.ticketId(), UserRole.QUALITY_AUDITOR, true, "Work certified");

        // Step 2: New report submitted within 30 meters of resolved repair (within DLP geofence radius)
        GovernanceTicket recurringNear = service.createTicket(
                UserRole.CITIZEN,
                12.97175, 77.59470, // ~20 meters away
                SeverityLevel.CRITICAL,
                List.of("reopened.jpg"),
                "Distress returned at same spot");

        assertTrue(recurringNear.inWarrantyDlp(), "Ticket within geofence and warranty window must be flagged as inWarrantyDlp");
        assertEquals("Roadbuilders Corp", recurringNear.dlpWarrantyContractor(),
                "DLP contractor must match original contractor who executed the repair");

        // Step 3: Report submitted far away (e.g. 5 km away) should NOT trigger DLP warranty
        GovernanceTicket farAway = service.createTicket(
                UserRole.CITIZEN,
                13.05000, 77.65000,
                SeverityLevel.HIGH,
                List.of("other.jpg"),
                "Unrelated highway distress");

        assertFalse(farAway.inWarrantyDlp());
        assertNull(farAway.dlpWarrantyContractor());
    }

    @Test
    @DisplayName("Quality Auditor rejection returns ticket to IN_PROGRESS for contractor rework")
    void testQualityAuditorRejectionAndRectification() {
        GovernanceTicket ticket = service.createTicket(
                UserRole.CITIZEN, 12.9716, 77.5946, SeverityLevel.HIGH, List.of("p.jpg"), "Distress");
        service.assignDepartment(ticket.ticketId(), UserRole.NODAL_OFFICER, Jurisdiction.NHAI, "Contractor Y");
        service.startWork(ticket.ticketId(), UserRole.SITE_ENGINEER);
        service.setAuditSamplingRate(1.0);
        service.completeWork(ticket.ticketId(), UserRole.SITE_ENGINEER, "b.jpg", "a.jpg", null);

        // Auditor rejects due to poor compaction
        GovernanceTicket rejected = service.auditTicket(
                ticket.ticketId(),
                UserRole.QUALITY_AUDITOR,
                false,
                "Inadequate shoulder compaction and cold joint visible.");

        assertEquals(TicketStatus.IN_PROGRESS, rejected.status(), "Rejected ticket must return to IN_PROGRESS");
        assertEquals(AuditApprovalStatus.REJECTED, rejected.auditApprovalStatus());
        assertTrue(rejected.auditNotes().contains("Inadequate shoulder compaction"));

        // Site engineer rectifies, uploads improved after photo and resubmits
        GovernanceTicket resubmitted = service.completeWork(
                ticket.ticketId(),
                UserRole.SITE_ENGINEER,
                "b.jpg",
                "a_improved.jpg",
                List.of("Extra leveling coat"));

        assertEquals(TicketStatus.COMPLETED, resubmitted.status());

        // Auditor approves resubmitted work
        GovernanceTicket approved = service.auditTicket(
                ticket.ticketId(),
                UserRole.QUALITY_AUDITOR,
                true,
                "Rectification verified. Compaction within tolerances.");

        assertEquals(TicketStatus.RESOLVED, approved.status());
        assertEquals(AuditApprovalStatus.APPROVED, approved.auditApprovalStatus());
    }

    @Test
    @DisplayName("Unsampled ticket can be resolved directly by Nodal Officer or Auditor")
    void testUnsampledTicketResolution() {
        service.setAuditSamplingRate(0.0); // Never sampled for audit

        GovernanceTicket ticket = service.createTicket(
                UserRole.CITIZEN, 12.9716, 77.5946, SeverityLevel.LOW, List.of("p.jpg"), "Cosmetic crack");
        service.assignDepartment(ticket.ticketId(), UserRole.NODAL_OFFICER, Jurisdiction.URBAN_LOCAL_BODY, "Contractor Z");
        service.startWork(ticket.ticketId(), UserRole.SITE_ENGINEER);
        GovernanceTicket completed = service.completeWork(ticket.ticketId(), UserRole.SITE_ENGINEER, "b.jpg", "a.jpg", null);

        assertFalse(completed.sampledForAudit());
        assertEquals(AuditApprovalStatus.NOT_REQUIRED, completed.auditApprovalStatus());

        // Nodal Officer resolves unsampled ticket
        GovernanceTicket resolved = service.resolveUnsampledTicket(ticket.ticketId(), UserRole.NODAL_OFFICER);
        assertEquals(TicketStatus.RESOLVED, resolved.status());
    }

    @Test
    @DisplayName("MTTR and SLA metrics calculate correctly across tickets")
    void testMetricsAndSlaCalculation() {
        Instant now = Instant.now();

        // Resolved in 12 hours (within 24h CRITICAL SLA)
        GovernanceTicket t1 = GovernanceTicket.builder("TKT-M1", 12.0, 77.0, SeverityLevel.CRITICAL)
                .status(TicketStatus.RESOLVED)
                .reportedAt(now.minus(12, ChronoUnit.HOURS))
                .resolvedAt(now)
                .build();

        // Resolved in 72 hours (exceeds 48h HIGH SLA)
        GovernanceTicket t2 = GovernanceTicket.builder("TKT-M2", 12.1, 77.1, SeverityLevel.HIGH)
                .status(TicketStatus.RESOLVED)
                .reportedAt(now.minus(72, ChronoUnit.HOURS))
                .resolvedAt(now)
                .build();

        service.addTicketDirect(t1);
        service.addTicketDirect(t2);

        // MTTR = (12 + 72) / 2 = 42.0 hours
        double mttr = service.getMeanTimeToResolutionHours();
        assertEquals(42.0, mttr, 0.1);

        // SLA Compliance: 1 out of 2 compliant = 50.0%
        double sla = service.getSlaCompliancePercentage();
        assertEquals(50.0, sla, 0.1);
    }

    @Test
    @DisplayName("Contractor Requisition and Live Municipal Budget Reflection")
    void testContractorClaimAndSupervisorAuditBudget() {
        GovernanceTicket t = service.createTicket(
                UserRole.CITIZEN,
                13.0450, 80.2520,
                SeverityLevel.HIGH,
                List.of("samples/before_pothole_1.jpg"),
                "Severe pothole on Mount Road",
                "Mount Road, Anna Salai near DMS Metro, Chennai");

        assertEquals(18500.0, t.estimatedCost(), 0.01);
        assertEquals(0.0, t.claimedCost(), 0.01);
        assertEquals("Mount Road, Anna Salai near DMS Metro, Chennai", t.streetAddress());

        service.assignDepartment(t.ticketId(), UserRole.NODAL_OFFICER, Jurisdiction.STATE_HIGHWAY, "Apex Infra Roadways Ltd");
        service.startWork(t.ticketId(), UserRole.SITE_ENGINEER);

        double initialAvailable = service.getAvailableBudget();

        // Contractor submits requisition and claim
        GovernanceTicket claimed = service.submitContractorClaim(
                t.ticketId(),
                UserRole.SITE_ENGINEER,
                "samples/before_pothole_1.jpg",
                "samples/after_patch_1.jpg",
                "2.5 Tonnes Cold Mix Asphalt + Bitumen Emulsion Tack Coat",
                19200.0);

        assertEquals(TicketStatus.COMPLETED, claimed.status());
        assertEquals(AuditApprovalStatus.PENDING_SURVEYOR_ENDORSEMENT, claimed.auditApprovalStatus());
        assertEquals(19200.0, claimed.claimedCost(), 0.01);
        assertEquals("2.5 Tonnes Cold Mix Asphalt + Bitumen Emulsion Tack Coat", claimed.materialItemization());

        // Check budget reflection: ₹19,200 is in pending and reserved
        assertTrue(service.getPendingDisbursements() >= 19200.0);
        assertEquals(GovernanceService.TOTAL_MUNICIPAL_BUDGET - service.getApprovedDisbursements() - service.getPendingDisbursements(),
                service.getAvailableBudget(), 0.01);

        // Surveyor endorses funding requisition to Supervisor
        GovernanceTicket endorsed = service.endorseBySurveyor(
                t.ticketId(),
                UserRole.SURVEYOR,
                true,
                "Material compaction depth verified. Recommended for funding.");

        assertEquals(AuditApprovalStatus.SURVEYOR_ENDORSED, endorsed.auditApprovalStatus());
        assertTrue(endorsed.surveyorEndorsed());

        // Supervisor approves: moves from Pending to Approved
        double pendingBefore = service.getPendingDisbursements();
        double approvedBefore = service.getApprovedDisbursements();

        GovernanceTicket approved = service.auditTicket(
                t.ticketId(),
                UserRole.QUALITY_AUDITOR,
                true,
                "Workmanship approved under IRC:SP:84 specifications.");

        assertEquals(TicketStatus.RESOLVED, approved.status());
        assertEquals(AuditApprovalStatus.APPROVED, approved.auditApprovalStatus());
        assertEquals(pendingBefore - 19200.0, service.getPendingDisbursements(), 0.01);
        assertEquals(approvedBefore + 19200.0, service.getApprovedDisbursements(), 0.01);
        assertTrue(service.getDisbursedPercentage() > 0.0);
    }

    @Test
    @DisplayName("Supervisor Rejection returns claimed funds to Available balance")
    void testSupervisorRejectionReturnsFunds() {
        GovernanceTicket t = service.createTicket(
                UserRole.CITIZEN,
                13.0067, 80.2025,
                SeverityLevel.MEDIUM,
                List.of("samples/before_pothole_1.jpg"),
                "Depression",
                "Inner Ring Road, Guindy");

        service.assignDepartment(t.ticketId(), UserRole.NODAL_OFFICER, Jurisdiction.URBAN_LOCAL_BODY, "Metro Paving Corp");
        service.startWork(t.ticketId(), UserRole.SITE_ENGINEER);
        service.submitContractorClaim(
                t.ticketId(),
                UserRole.SITE_ENGINEER,
                "samples/before_pothole_1.jpg",
                "samples/after_patch_1.jpg",
                "1.5 Tonnes Cold Mix",
                14000.0);

        // Endorsed by surveyor
        service.endorseBySurveyor(t.ticketId(), UserRole.SURVEYOR, true, "Recommended");

        double availableBefore = service.getAvailableBudget();

        // Reject ticket: routes back to IN_PROGRESS
        GovernanceTicket rejected = service.auditTicket(
                t.ticketId(),
                UserRole.QUALITY_AUDITOR,
                false,
                "Improper compaction. Rework required.");

        assertEquals(TicketStatus.IN_PROGRESS, rejected.status());
        assertEquals(AuditApprovalStatus.REJECTED, rejected.auditApprovalStatus());

        // Funds returned to available balance
        assertEquals(availableBefore + 14000.0, service.getAvailableBudget(), 0.01);
    }

    @Test
    @DisplayName("Surveyor Direct Rejection routes back to Contractor IN_PROGRESS")
    void testSurveyorRejectionFlow() {
        GovernanceTicket t = service.createTicket(
                UserRole.CITIZEN,
                13.0450, 80.2520,
                SeverityLevel.HIGH,
                List.of("samples/before_pothole_1.jpg"),
                "Edge depression",
                "Anna Salai");

        service.assignDepartment(t.ticketId(), UserRole.NODAL_OFFICER, Jurisdiction.STATE_HIGHWAY, "Apex Infra Roadways Ltd");
        service.startWork(t.ticketId(), UserRole.SITE_ENGINEER);
        service.submitContractorClaim(
                t.ticketId(),
                UserRole.SITE_ENGINEER,
                "samples/before_pothole_1.jpg",
                "samples/after_patch_1.jpg",
                "1.0 Tonnes Cold Mix",
                12000.0);

        // Field surveyor inspects and rejects due to incomplete compaction
        GovernanceTicket rejected = service.endorseBySurveyor(
                t.ticketId(),
                UserRole.SURVEYOR,
                false,
                "Bitumen layer thickness insufficient. Please relay overlay.");

        assertEquals(TicketStatus.IN_PROGRESS, rejected.status());
        assertEquals(AuditApprovalStatus.REJECTED, rejected.auditApprovalStatus());
        assertFalse(rejected.surveyorEndorsed());
    }
}

