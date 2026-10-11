package com.roaddamage.ui;

import com.roaddamage.config.AppConfig;
import com.roaddamage.core.model.BoundingBox;
import com.roaddamage.core.model.DamageAssessment;
import com.roaddamage.core.model.Detection;
import com.roaddamage.core.model.SeverityLevel;
import com.roaddamage.export.CsvReportExporter;
import com.roaddamage.export.DamageRow;
import com.roaddamage.governance.GovernanceService;
import com.roaddamage.governance.GovernanceTicket;
import com.roaddamage.governance.Jurisdiction;
import com.roaddamage.governance.StaffAuthService;
import com.roaddamage.governance.StaffAuthService.StaffPrincipal;
import com.roaddamage.governance.TicketStatus;
import com.roaddamage.governance.UserRole;
import com.roaddamage.ingest.FrameSource;
import com.roaddamage.pipeline.FrameResult;
import com.roaddamage.pipeline.InspectionPipeline;
import com.roaddamage.pipeline.PipelineConfig;
import com.roaddamage.pipeline.PipelineMetrics;
import java.io.File;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Main JavaFX UI controller orchestrating video inspection, real-time bounding box visualization,
 * analytical damage reporting, role-based access control, and civic governance portals.
 */
public class MainController {

    private static final Logger LOGGER = System.getLogger(MainController.class.getName());

    @FunctionalInterface
    public interface PipelineFactory {
        InspectionPipeline create(PipelineConfig config,
                                  Consumer<FrameResult> sink,
                                  Consumer<FrameSnapshot> frameSink) throws Exception;
    }

    private static PipelineFactory pipelineFactory = InspectionPipeline::new;

    public static void setPipelineFactory(PipelineFactory factory) {
        pipelineFactory = Objects.requireNonNull(factory, "factory must not be null");
    }

    public static void resetPipelineFactory() {
        pipelineFactory = InspectionPipeline::new;
    }

    // Top Header & Access Controls
    @FXML private Label authUserLabel;
    @FXML private Button authActionBtn;
    @FXML private Button themeToggleBtn;

    // Tabs
    @FXML private TabPane roleTabPane;
    @FXML private Tab citizenTab;
    @FXML private Tab nodalTab;
    @FXML private Tab siteTab;
    @FXML private Tab auditorTab;
    @FXML private Tab inspectionTab;

    // Inspection View Controls (Baseline preserved)
    @FXML private Canvas originalView;
    @FXML private AnnotatedCanvas annotatedCanvas;
    @FXML private TableView<DamageRow> logTable;
    @FXML private TableColumn<DamageRow, Long> colFrame;
    @FXML private TableColumn<DamageRow, Long> colTimestamp;
    @FXML private TableColumn<DamageRow, String> colClass;
    @FXML private TableColumn<DamageRow, Float> colConfidence;
    @FXML private TableColumn<DamageRow, Integer> colScore;
    @FXML private TableColumn<DamageRow, SeverityLevel> colLevel;
    @FXML private TableColumn<DamageRow, String> colExplanation;

    @FXML private ListView<String> alertList;
    @FXML private Label statusLabel;
    @FXML private Button openButton;
    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private Button exportButton;
    @FXML private ProgressBar progress;

    // Consumer-Grade Citizen Portal Controls
    @FXML private StackPane citizenUploadZone;
    @FXML private VBox citizenUploadPromptBox;
    @FXML private VBox citizenPreviewContainer;
    @FXML private ComboBox<String> citizenAreaBox;
    @FXML private TextField citizenAddressField;
    @FXML private Button citizenCurrentLocationBtn;
    @FXML private Label citizenGpsStatusLabel;
    @FXML private Label citizenTicketCountBadge;
    @FXML private TextField citizenSearchField;
    @FXML private Button citizenFilterAllBtn;
    @FXML private Button citizenFilterRepairBtn;
    @FXML private Button citizenFilterFixedBtn;
    @FXML private ListView<GovernanceTicket> citizenTicketList;

    // Citizen Portal (Preserved & Backward Compatible)
    @FXML private ComboBox<String> citizenGpsPresetBox;
    @FXML private TextField citizenLatField;
    @FXML private TextField citizenLonField;
    @FXML private TextField citizenPhotoField;
    @FXML private Button citizenPhotoBrowseBtn;
    @FXML private ComboBox<SeverityLevel> citizenSeverityBox;
    @FXML private TextField citizenDescField;
    @FXML private Button citizenSubmitBtn;
    @FXML private ImageView citizenImagePreview;
    @FXML private Label citizenSeverityBadge;
    @FXML private Label citizenTurnaroundLabel;

    @FXML private TableView<GovernanceTicket> citizenTicketTable;
    @FXML private TableColumn<GovernanceTicket, String> citizenColId;
    @FXML private TableColumn<GovernanceTicket, SeverityLevel> citizenColSeverity;
    @FXML private TableColumn<GovernanceTicket, TicketStatus> citizenColStatus;
    @FXML private TableColumn<GovernanceTicket, String> citizenColDlp;
    @FXML private TableColumn<GovernanceTicket, String> citizenColDesc;
    @FXML private Label citizenTimelineLabel;

    // Nodal Officer Portal
    @FXML private Label nodalSummaryLabel;
    @FXML private TableView<GovernanceTicket> nodalTable;
    @FXML private TableColumn<GovernanceTicket, String> nodalColId;
    @FXML private TableColumn<GovernanceTicket, String> nodalColGps;
    @FXML private TableColumn<GovernanceTicket, SeverityLevel> nodalColSeverity;
    @FXML private TableColumn<GovernanceTicket, TicketStatus> nodalColStatus;
    @FXML private TableColumn<GovernanceTicket, String> nodalColDlp;
    @FXML private Label nodalSelectedTicketLabel;
    @FXML private Label nodalDlpWarningLabel;
    @FXML private ComboBox<Jurisdiction> nodalJurisdictionBox;
    @FXML private TextField nodalContractorField;
    @FXML private Button nodalAssignBtn;

    // Site Engineer Portal
    @FXML private TableView<GovernanceTicket> siteTable;
    @FXML private TableColumn<GovernanceTicket, String> siteColId;
    @FXML private TableColumn<GovernanceTicket, Jurisdiction> siteColJurisdiction;
    @FXML private TableColumn<GovernanceTicket, String> siteColContractor;
    @FXML private TableColumn<GovernanceTicket, TicketStatus> siteColStatus;
    @FXML private Label siteSelectedTicketLabel;
    @FXML private Button siteStartWorkBtn;
    @FXML private ImageView siteBeforeImageView;
    @FXML private ImageView siteAfterImageView;
    @FXML private TextField siteMaterialField;
    @FXML private Button siteAddMaterialBtn;
    @FXML private ListView<String> siteMaterialsList;
    @FXML private TextField siteBeforeImgField;
    @FXML private Button siteBeforeBrowseBtn;
    @FXML private TextField siteAfterImgField;
    @FXML private Button siteAfterBrowseBtn;
    @FXML private Button siteCompleteWorkBtn;

    // Quality Auditor Portal
    @FXML private Label auditorMttrLabel;
    @FXML private Label auditorSlaLabel;
    @FXML private Label auditorDlpCountLabel;
    @FXML private Label auditorSamplingRateLabel;
    @FXML private TableView<GovernanceTicket> auditorTable;
    @FXML private TableColumn<GovernanceTicket, String> auditorColId;
    @FXML private TableColumn<GovernanceTicket, String> auditorColContractor;
    @FXML private TableColumn<GovernanceTicket, SeverityLevel> auditorColSeverity;
    @FXML private TableColumn<GovernanceTicket, String> auditorColAuditStatus;
    @FXML private Label auditorSelectedTicketLabel;
    @FXML private ImageView auditorBeforeImageView;
    @FXML private ImageView auditorAfterImageView;
    @FXML private Label auditorBeforeProofLabel;
    @FXML private Label auditorAfterProofLabel;
    @FXML private ListView<String> auditorMaterialsList;
    @FXML private TextField auditorNotesField;
    @FXML private Button auditorApproveBtn;
    @FXML private Button auditorRejectBtn;

    // Internal State & Models
    private final ObservableList<DamageRow> damageRows = FXCollections.observableArrayList();
    private final ObservableList<String> alertItems = FXCollections.observableArrayList();
    private final AtomicReference<FrameSnapshot> pendingSnapshot = new AtomicReference<>();

    private final GovernanceService governanceService = GovernanceService.getInstance();
    private final ObservableList<GovernanceTicket> citizenTicketItems = FXCollections.observableArrayList();
    private final ObservableList<GovernanceTicket> nodalTicketItems = FXCollections.observableArrayList();
    private final ObservableList<GovernanceTicket> siteTicketItems = FXCollections.observableArrayList();
    private final ObservableList<GovernanceTicket> auditorTicketItems = FXCollections.observableArrayList();
    private final ObservableList<String> siteMaterialsItems = FXCollections.observableArrayList();
    private final ObservableList<String> auditorMaterialsItems = FXCollections.observableArrayList();

    private StaffPrincipal currentPrincipal = null;
    private boolean isDarkMode = true;

    private FilteredList<GovernanceTicket> citizenFilteredList;
    private String currentTicketFilter = "ALL";
    private double currentCitizenLat = 13.00670;
    private double currentCitizenLon = 80.20250;

    private Path selectedPath;
    private volatile InspectionPipeline activePipeline;
    private AnimationTimer uiPacer;
    private Timeline metricsTimeline;
    private ExecutorService executor;

    @FXML
    public void initialize() {
        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r, "RoadDamage-Worker");
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newCachedThreadPool(daemonFactory);

        initializeInspectionTable();
        initializeGovernancePortals();
        applyAccessControl(); // Enforce Citizen Portal default and lock staff tabs

        // Rate-limited 30 FPS UI Snapshot Pacer for OpenCV video stream
        uiPacer = new AnimationTimer() {
            private long lastPulseNanos = 0L;

            @Override
            public void handle(long now) {
                if (now - lastPulseNanos >= 33_333_333L) {
                    FrameSnapshot snap = pendingSnapshot.getAndSet(null);
                    if (snap != null) {
                        renderSnapshot(snap);
                    }
                    lastPulseNanos = now;
                }
            }
        };
        uiPacer.start();
    }

    private void initializeInspectionTable() {
        if (colFrame != null) {
            colFrame.setCellValueFactory(data -> new SimpleLongProperty(data.getValue().frameIndex()).asObject());
        }
        if (colTimestamp != null) {
            colTimestamp.setCellValueFactory(data -> new SimpleLongProperty(data.getValue().timestampMillis()).asObject());
        }
        if (colClass != null) {
            colClass.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().damageClassLabel()));
        }
        if (colConfidence != null) {
            colConfidence.setCellValueFactory(data -> new SimpleFloatProperty(data.getValue().confidence()).asObject());
            colConfidence.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(Float item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : String.format(Locale.US, "%.1f%%", item * 100.0f));
                }
            });
        }
        if (colScore != null) {
            colScore.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().priorityScore()).asObject());
        }
        if (colLevel != null) {
            colLevel.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().severityLevel()));
        }
        if (colExplanation != null) {
            colExplanation.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().explanation()));
        }

        if (logTable != null) {
            logTable.setItems(damageRows);
            logTable.setRowFactory(tv -> new TableRow<>() {
                @Override
                protected void updateItem(DamageRow item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("severity-low", "severity-medium", "severity-high", "severity-critical");
                    if (!empty && item != null && item.severityLevel() != null) {
                        switch (item.severityLevel()) {
                            case LOW -> getStyleClass().add("severity-low");
                            case MEDIUM -> getStyleClass().add("severity-medium");
                            case HIGH -> getStyleClass().add("severity-high");
                            case CRITICAL -> getStyleClass().add("severity-critical");
                        }
                    }
                }
            });
        }

        if (alertList != null) {
            alertList.setItems(alertItems);
        }

        if (startButton != null) startButton.setDisable(true);
        if (stopButton != null) stopButton.setDisable(true);
        if (progress != null) progress.setProgress(0.0);
        if (statusLabel != null) statusLabel.setText("Ready. Please open an image or video file.");
    }

    private void initializeGovernancePortals() {
        // 1. Consumer-Grade Citizen Portal Setup
        if (citizenAreaBox != null) {
            citizenAreaBox.getItems().setAll(
                    "Chennai - Guindy / Mount Road Corridor",
                    "Bengaluru - MG Road Central",
                    "Bengaluru - Indiranagar 100ft Rd",
                    "Chennai - OMR IT Expressway",
                    "NHAI NH-48 Arterial Corridor"
            );
            citizenAreaBox.setValue("Chennai - Guindy / Mount Road Corridor");
        }
        if (citizenAddressField != null) {
            citizenAddressField.setText("Mount Road, near Signal, Guindy, Chennai");
        }
        if (citizenGpsStatusLabel != null) {
            citizenGpsStatusLabel.setText("📍 GPS Ready (Auto-Detect)");
        }

        // Setup Drag & Drop on Upload Zone
        if (citizenUploadZone != null) {
            citizenUploadZone.setOnDragOver(event -> {
                if (event.getGestureSource() != citizenUploadZone && event.getDragboard().hasFiles()) {
                    List<File> files = event.getDragboard().getFiles();
                    if (!files.isEmpty() && isImageFile(files.get(0))) {
                        event.acceptTransferModes(TransferMode.COPY);
                    }
                }
                event.consume();
            });

            citizenUploadZone.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasFiles()) {
                    List<File> files = db.getFiles();
                    if (!files.isEmpty() && isImageFile(files.get(0))) {
                        setCitizenSelectedPhoto(files.get(0).getAbsolutePath());
                        success = true;
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });
        }

        // Set default photographic evidence
        setCitizenSelectedPhoto("samples/defect_pothole_mg_rd.jpg");

        if (citizenGpsPresetBox != null) {
            citizenGpsPresetBox.getItems().setAll(
                    "MG Road Corridor [Near Guarantee Zone] (12.97162, 77.59459)",
                    "Outer Ring Road Arterial [NHAI] (13.08270, 80.27070)",
                    "Indiranagar 100ft Main Rd [ULB] (12.93520, 77.62450)"
            );
        }
        if (citizenSeverityBox != null) {
            citizenSeverityBox.getItems().setAll(SeverityLevel.values());
            citizenSeverityBox.setValue(SeverityLevel.HIGH);
            updateCitizenSeverityVisuals(SeverityLevel.HIGH);
        }
        if (citizenLatField != null) citizenLatField.setText("13.00670");
        if (citizenLonField != null) citizenLonField.setText("80.20250");

        // Card List for Citizen Grievance Tracker
        citizenFilteredList = new FilteredList<>(citizenTicketItems, p -> true);
        if (citizenTicketList != null) {
            citizenTicketList.setItems(citizenFilteredList);
            citizenTicketList.setCellFactory(lv -> new CitizenTicketCardCell());
            citizenTicketList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    handleCitizenTicketSelected(newVal);
                }
            });
        }

        if (citizenSearchField != null) {
            citizenSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyCitizenFilter());
        }

        if (citizenTicketTable != null) {
            citizenTicketTable.setItems(citizenTicketItems);
            if (citizenColId != null) citizenColId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().ticketId()));
            if (citizenColSeverity != null) citizenColSeverity.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().severity()));
            if (citizenColStatus != null) citizenColStatus.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().status()));
            if (citizenColDlp != null) citizenColDlp.setCellValueFactory(d ->
                    new SimpleStringProperty(d.getValue().inWarrantyDlp() ? "YES (Under Guarantee)" : "Standard"));
            if (citizenColDesc != null) citizenColDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().description()));

            citizenTicketTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    handleCitizenTicketSelected(newVal);
                }
            });
        }

        // 2. Nodal Officer Portal Setup
        if (nodalJurisdictionBox != null) {
            nodalJurisdictionBox.getItems().setAll(Jurisdiction.values());
            nodalJurisdictionBox.setValue(Jurisdiction.STATE_HIGHWAY);
        }
        if (nodalContractorField != null) {
            nodalContractorField.setText("Apex Infra Roadways Ltd");
        }
        if (nodalTable != null) {
            nodalTable.setItems(nodalTicketItems);
            if (nodalColId != null) nodalColId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().ticketId()));
            if (nodalColGps != null) nodalColGps.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().gpsCoordinatesString()));
            if (nodalColSeverity != null) nodalColSeverity.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().severity()));
            if (nodalColStatus != null) nodalColStatus.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().status()));
            if (nodalColDlp != null) nodalColDlp.setCellValueFactory(d ->
                    new SimpleStringProperty(d.getValue().inWarrantyDlp() ? "GUARANTEED: " + d.getValue().dlpWarrantyContractor() : "Standard"));

            nodalTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    if (nodalSelectedTicketLabel != null) {
                        nodalSelectedTicketLabel.setText(String.format("Issue %s at [%s] - Severity: %s - %s",
                                newVal.ticketId(), newVal.gpsCoordinatesString(), newVal.severity(), newVal.description()));
                    }
                    if (nodalDlpWarningLabel != null) {
                        if (newVal.inWarrantyDlp()) {
                            nodalDlpWarningLabel.setText("CONTRACTOR GUARANTEE ACTIVE: Recurring defect within 100m of prior repair! Bound Contractor: "
                                    + newVal.dlpWarrantyContractor());
                            nodalDlpWarningLabel.getStyleClass().setAll("dlp-badge-warning");
                            if (nodalContractorField != null && newVal.dlpWarrantyContractor() != null) {
                                nodalContractorField.setText(newVal.dlpWarrantyContractor());
                            }
                        } else {
                            nodalDlpWarningLabel.setText("Contractor Guarantee Status: Standard public works allocation (No active warranty).");
                            nodalDlpWarningLabel.getStyleClass().setAll("dlp-badge-clear");
                        }
                    }
                }
            });
        }

        // 3. Site Engineer Portal Setup
        if (siteMaterialsList != null) {
            siteMaterialsList.setItems(siteMaterialsItems);
        }
        if (siteTable != null) {
            siteTable.setItems(siteTicketItems);
            if (siteColId != null) siteColId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().ticketId()));
            if (siteColJurisdiction != null) siteColJurisdiction.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().jurisdiction()));
            if (siteColContractor != null) siteColContractor.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().assignedContractor()));
            if (siteColStatus != null) siteColStatus.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().status()));

            siteTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    if (siteSelectedTicketLabel != null) {
                        siteSelectedTicketLabel.setText(String.format("Work Order %s | Status: %s | Contractor: %s",
                                newVal.ticketId(), newVal.status(), newVal.assignedContractor()));
                    }
                    siteMaterialsItems.setAll(newVal.materialRequests());
                    String bPath = newVal.beforeImagePath() != null ? newVal.beforeImagePath() : "samples/before_pothole_1.jpg";
                    String aPath = newVal.afterImagePath() != null ? newVal.afterImagePath() : "samples/after_patch_1.jpg";

                    if (siteBeforeImgField != null) siteBeforeImgField.setText(bPath);
                    if (siteAfterImgField != null) siteAfterImgField.setText(aPath);

                    ImageCache.loadAsync(bPath, 190, 120, img -> {
                        if (siteBeforeImageView != null) siteBeforeImageView.setImage(img);
                    });
                    ImageCache.loadAsync(aPath, 190, 120, img -> {
                        if (siteAfterImageView != null) siteAfterImageView.setImage(img);
                    });
                }
            });
        }

        // 4. Quality Auditor Portal Setup
        if (auditorMaterialsList != null) {
            auditorMaterialsList.setItems(auditorMaterialsItems);
        }
        if (auditorTable != null) {
            auditorTable.setItems(auditorTicketItems);
            if (auditorColId != null) auditorColId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().ticketId()));
            if (auditorColContractor != null) auditorColContractor.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().assignedContractor()));
            if (auditorColSeverity != null) auditorColSeverity.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().severity()));
            if (auditorColAuditStatus != null) auditorColAuditStatus.setCellValueFactory(d ->
                    new SimpleStringProperty(d.getValue().auditApprovalStatus().label()));

            auditorTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    if (auditorSelectedTicketLabel != null) {
                        auditorSelectedTicketLabel.setText(String.format("Auditing Ticket %s (%s) - Executed by: %s",
                                newVal.ticketId(), newVal.severity(), newVal.assignedContractor()));
                    }
                    String bPath = newVal.beforeImagePath() != null ? newVal.beforeImagePath() : "samples/before_pothole_1.jpg";
                    String aPath = newVal.afterImagePath() != null ? newVal.afterImagePath() : "samples/after_patch_1.jpg";

                    if (auditorBeforeProofLabel != null) auditorBeforeProofLabel.setText(bPath);
                    if (auditorAfterProofLabel != null) auditorAfterProofLabel.setText(aPath);

                    ImageCache.loadAsync(bPath, 190, 120, img -> {
                        if (auditorBeforeImageView != null) auditorBeforeImageView.setImage(img);
                    });
                    ImageCache.loadAsync(aPath, 190, 120, img -> {
                        if (auditorAfterImageView != null) auditorAfterImageView.setImage(img);
                    });

                    auditorMaterialsItems.setAll(newVal.materialRequests());
                }
            });
        }

        // 5. Seed initial demonstration data
        governanceService.seedDemoData();
        refreshGovernanceViews();
        governanceService.addTicketChangeListener(ticket -> Platform.runLater(this::refreshGovernanceViews));
    }

    /**
     * Refreshes all governance portal lists and auditor KPI cards.
     */
    public void refreshGovernanceViews() {
        List<GovernanceTicket> all = governanceService.getAllTickets();

        citizenTicketItems.setAll(all);
        applyCitizenFilter();
        nodalTicketItems.setAll(all);

        if (nodalSummaryLabel != null) {
            long reportedCount = all.stream().filter(t -> t.status() == TicketStatus.REPORTED).count();
            long dlpCount = all.stream().filter(GovernanceTicket::inWarrantyDlp).count();
            nodalSummaryLabel.setText(String.format("Pending Assignment: %d | Under Free Repair Guarantee: %d", reportedCount, dlpCount));
        }

        siteTicketItems.setAll(governanceService.getSiteEngineerWorkOrders(null));

        List<GovernanceTicket> completed = all.stream()
                .filter(t -> t.status() == TicketStatus.COMPLETED || t.status() == TicketStatus.RESOLVED)
                .toList();
        auditorTicketItems.setAll(completed);

        if (auditorMttrLabel != null) {
            auditorMttrLabel.setText(String.format(Locale.US, "%.1f hrs", governanceService.getMeanTimeToResolutionHours()));
        }
        if (auditorSlaLabel != null) {
            auditorSlaLabel.setText(String.format(Locale.US, "%.1f%%", governanceService.getSlaCompliancePercentage()));
        }
        if (auditorDlpCountLabel != null) {
            long dlpTotal = all.stream().filter(GovernanceTicket::inWarrantyDlp).count();
            auditorDlpCountLabel.setText(dlpTotal + " Cases");
        }
        if (auditorSamplingRateLabel != null) {
            auditorSamplingRateLabel.setText(String.format(Locale.US, "%.0f%% (Randomized)", governanceService.getAuditSamplingRate() * 100.0));
        }
    }

    // =========================================================================
    // ACCESS CONTROL & LOGIN / LOGOUT
    // =========================================================================

    /**
     * Enforces default Citizen Home portal and disables/locks unauthorized tabs.
     */
    public void applyAccessControl() {
        if (currentPrincipal == null) {
            // Citizen mode: Citizen tab enabled and active; all staff tabs locked
            if (authUserLabel != null) authUserLabel.setText("Public (Citizen Mode)");
            if (authActionBtn != null) {
                authActionBtn.setText("Staff Sign In");
                authActionBtn.getStyleClass().removeAll("danger-button");
                if (!authActionBtn.getStyleClass().contains("primary-button")) {
                    authActionBtn.getStyleClass().add("primary-button");
                }
            }

            if (citizenTab != null) citizenTab.setDisable(false);
            if (nodalTab != null) nodalTab.setDisable(true);
            if (siteTab != null) siteTab.setDisable(true);
            if (auditorTab != null) auditorTab.setDisable(true);
            if (inspectionTab != null) inspectionTab.setDisable(true);

            if (roleTabPane != null && citizenTab != null) {
                roleTabPane.getSelectionModel().select(citizenTab);
            }
        } else {
            // Authenticated staff user
            if (authUserLabel != null) {
                authUserLabel.setText(String.format("Staff: %s (%s)", currentPrincipal.fullName(), currentPrincipal.email()));
            }
            if (authActionBtn != null) {
                authActionBtn.setText("Sign Out");
                authActionBtn.getStyleClass().removeAll("primary-button");
                if (!authActionBtn.getStyleClass().contains("danger-button")) {
                    authActionBtn.getStyleClass().add("danger-button");
                }
            }

            if (citizenTab != null) citizenTab.setDisable(false);

            // Enable matching role tab
            if (nodalTab != null) nodalTab.setDisable(currentPrincipal.role() != UserRole.NODAL_OFFICER);
            if (siteTab != null) siteTab.setDisable(currentPrincipal.role() != UserRole.SITE_ENGINEER);
            if (auditorTab != null) auditorTab.setDisable(currentPrincipal.role() != UserRole.QUALITY_AUDITOR);
            if (inspectionTab != null) inspectionTab.setDisable(!currentPrincipal.canAccessInspection());

            if (roleTabPane != null) {
                switch (currentPrincipal.role()) {
                    case NODAL_OFFICER -> roleTabPane.getSelectionModel().select(nodalTab);
                    case SITE_ENGINEER -> roleTabPane.getSelectionModel().select(siteTab);
                    case QUALITY_AUDITOR -> roleTabPane.getSelectionModel().select(auditorTab);
                    default -> {
                        if (currentPrincipal.canAccessInspection() && inspectionTab != null) {
                            roleTabPane.getSelectionModel().select(inspectionTab);
                        } else if (citizenTab != null) {
                            roleTabPane.getSelectionModel().select(citizenTab);
                        }
                    }
                }
            }
        }
    }

    @FXML
    public void onAuthAction() {
        if (currentPrincipal == null) {
            showLoginDialog();
        } else {
            signOut();
        }
    }

    public void signOut() {
        this.currentPrincipal = null;
        applyAccessControl();
        if (statusLabel != null) {
            statusLabel.setText("Signed out. Switched to Citizen Portal.");
        }
    }

    /**
     * Authenticates programmatically (used by login dialog and automated tests).
     *
     * @param email user email
     * @param password secret password
     * @return true if authentication succeeded
     */
    public boolean login(String email, String password) {
        Optional<StaffPrincipal> principalOpt = StaffAuthService.authenticate(email, password);
        if (principalOpt.isPresent()) {
            this.currentPrincipal = principalOpt.get();
            applyAccessControl();
            if (statusLabel != null) {
                statusLabel.setText("Signed in as " + currentPrincipal.fullName() + " (" + currentPrincipal.agency() + ")");
            }
            return true;
        }
        return false;
    }

    private void showLoginDialog() {
        Dialog<StaffPrincipal> dialog = new Dialog<>();
        dialog.setTitle("Municipal & Highway Staff Login");
        dialog.setHeaderText("Sign in with your role-based credentials to access staff operations.\n(Predefined accounts: admin@pwd.gov, site@contractor.in, audit@nhai.gov, ai@inspector.gov)");

        ButtonType signInButtonType = new ButtonType("Sign In", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(signInButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        ComboBox<String> emailCombo = new ComboBox<>();
        emailCombo.setEditable(true);
        emailCombo.getItems().addAll(
                "admin@pwd.gov (Nodal Officer - PWD)",
                "site@contractor.in (Site Engineer - Apex Infra)",
                "audit@nhai.gov (Quality Auditor - NHAI)",
                "ai@inspector.gov (AI Specialist - Inspection Wing)"
        );
        emailCombo.setValue("admin@pwd.gov");

        PasswordField passwordField = new PasswordField();
        passwordField.setText("pwd123");
        passwordField.setPromptText("Password");

        emailCombo.setOnAction(e -> {
            String val = emailCombo.getValue();
            if (val != null) {
                if (val.contains("admin@pwd.gov")) passwordField.setText("pwd123");
                else if (val.contains("site@contractor.in")) passwordField.setText("site123");
                else if (val.contains("audit@nhai.gov")) passwordField.setText("audit123");
                else if (val.contains("ai@inspector.gov")) passwordField.setText("ai123");
            }
        });

        grid.add(new Label("Staff Email / Account:"), 0, 0);
        grid.add(emailCombo, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == signInButtonType) {
                String inputEmail = emailCombo.getValue();
                if (inputEmail != null && inputEmail.contains(" ")) {
                    inputEmail = inputEmail.split(" ")[0].trim();
                }
                Optional<StaffPrincipal> res = StaffAuthService.authenticate(inputEmail, passwordField.getText());
                return res.orElse(null);
            }
            return null;
        });

        Optional<StaffPrincipal> result = dialog.showAndWait();
        if (result.isPresent()) {
            this.currentPrincipal = result.get();
            applyAccessControl();
            if (statusLabel != null) {
                statusLabel.setText("Signed in as " + currentPrincipal.fullName() + " (" + currentPrincipal.agency() + ")");
            }
        } else if (dialog.getResult() == null && !dialog.getDialogPane().getButtonTypes().isEmpty()) {
            // User entered invalid credentials
            showErrorAlert("Authentication Failed", "Invalid email or password. Please use one of the predefined accounts (pwd123, site123, audit123, ai123).");
        }
    }

    // =========================================================================
    // THEMING (LIGHT & DARK MODE)
    // =========================================================================

    @FXML
    public void onToggleTheme() {
        this.isDarkMode = !this.isDarkMode;
        applyTheme();
    }

    private void applyTheme() {
        Window window = themeToggleBtn != null && themeToggleBtn.getScene() != null ?
                themeToggleBtn.getScene().getWindow() : null;
        Scene scene = window != null ? window.getScene() : null;

        if (scene != null) {
            scene.getStylesheets().clear();
            String cssPath = isDarkMode ? "/css/app.css" : "/css/app-light.css";
            URL cssUrl = getClass().getResource(cssPath);
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        }

        if (themeToggleBtn != null) {
            themeToggleBtn.setText(isDarkMode ? "☀️ Light Mode" : "🌙 Dark Mode");
        }
    }

    // =========================================================================
    // CITIZEN VISUAL DEFECT PREVIEW & SEVERITY INSPECTOR
    // =========================================================================

    @FXML
    public void onCitizenSeverityChanged() {
        if (citizenSeverityBox == null) return;
        SeverityLevel sev = citizenSeverityBox.getValue();
        if (sev != null) {
            updateCitizenSeverityVisuals(sev);
        }
    }

    private void updateCitizenSeverityVisuals(SeverityLevel sev) {
        if (citizenSeverityBadge == null || citizenTurnaroundLabel == null) return;

        citizenSeverityBadge.getStyleClass().removeAll("badge-low", "badge-medium", "badge-high", "badge-critical");

        switch (sev) {
            case LOW -> {
                citizenSeverityBadge.setText("LOW SEVERITY");
                citizenSeverityBadge.getStyleClass().add("badge-low");
                citizenTurnaroundLabel.setText("Estimated Repair Turnaround: ~14 Days (Routine Maintenance)");
            }
            case MEDIUM -> {
                citizenSeverityBadge.setText("MEDIUM SEVERITY");
                citizenSeverityBadge.getStyleClass().add("badge-medium");
                citizenTurnaroundLabel.setText("Estimated Repair Turnaround: ~7 Days (Scheduled Intervention)");
            }
            case HIGH -> {
                citizenSeverityBadge.setText("HIGH SEVERITY");
                citizenSeverityBadge.getStyleClass().add("badge-high");
                citizenTurnaroundLabel.setText("Estimated Repair Turnaround: ~48 Hours (High Priority Repair)");
            }
            case CRITICAL -> {
                citizenSeverityBadge.setText("CRITICAL HAZARD");
                citizenSeverityBadge.getStyleClass().add("badge-critical");
                citizenTurnaroundLabel.setText("Estimated Repair Turnaround: ~24 Hours (Emergency Hazard Response)");
            }
        }
    }

    @FXML
    public void onCitizenAreaSelected() {
        if (citizenAreaBox == null) return;
        String area = citizenAreaBox.getValue();
        if (area == null) return;

        if (area.contains("Guindy") || area.contains("Chennai")) {
            currentCitizenLat = 13.00670;
            currentCitizenLon = 80.20250;
            if (citizenAddressField != null) citizenAddressField.setText("Mount Road, near Signal, Guindy, Chennai");
            if (citizenGpsStatusLabel != null) citizenGpsStatusLabel.setText("📍 Geocoded: 13.0067°N, 80.2025°E (State Highway)");
        } else if (area.contains("MG Road")) {
            currentCitizenLat = 12.97162;
            currentCitizenLon = 77.59459;
            if (citizenAddressField != null) citizenAddressField.setText("MG Road, near Trinity Metro Junction, Bengaluru");
            if (citizenGpsStatusLabel != null) citizenGpsStatusLabel.setText("📍 Geocoded: 12.9716°N, 77.5946°E (Urban Corridor)");
        } else if (area.contains("Indiranagar")) {
            currentCitizenLat = 12.93520;
            currentCitizenLon = 77.62450;
            if (citizenAddressField != null) citizenAddressField.setText("100ft Main Road, near 12th Cross, Indiranagar, Bengaluru");
            if (citizenGpsStatusLabel != null) citizenGpsStatusLabel.setText("📍 Geocoded: 12.9352°N, 77.6245°E (ULB Arterial)");
        } else if (area.contains("OMR")) {
            currentCitizenLat = 12.91500;
            currentCitizenLon = 80.22900;
            if (citizenAddressField != null) citizenAddressField.setText("Old Mahabalipuram Road (OMR), Perungudi, Chennai");
            if (citizenGpsStatusLabel != null) citizenGpsStatusLabel.setText("📍 Geocoded: 12.9150°N, 80.2290°E (IT Expressway)");
        } else if (area.contains("NH-48") || area.contains("NHAI")) {
            currentCitizenLat = 13.08270;
            currentCitizenLon = 80.27070;
            if (citizenAddressField != null) citizenAddressField.setText("NH-48 Arterial Expressway, Km 32.4 Corridor");
            if (citizenGpsStatusLabel != null) citizenGpsStatusLabel.setText("📍 Geocoded: 13.0827°N, 80.2707°E (NHAI National Highway)");
        }

        if (citizenLatField != null) citizenLatField.setText(String.format(Locale.US, "%.5f", currentCitizenLat));
        if (citizenLonField != null) citizenLonField.setText(String.format(Locale.US, "%.5f", currentCitizenLon));
    }

    @FXML
    public void onCitizenUseCurrentLocation() {
        this.currentCitizenLat = 13.00670;
        this.currentCitizenLon = 80.20250;
        if (citizenLatField != null) citizenLatField.setText("13.00670");
        if (citizenLonField != null) citizenLonField.setText("80.20250");
        if (citizenAddressField != null) {
            citizenAddressField.setText("Mount Road, near Kathipara Flyover, Guindy, Chennai");
        }
        if (citizenAreaBox != null) {
            citizenAreaBox.setValue("Chennai - Guindy / Mount Road Corridor");
        }
        if (citizenGpsStatusLabel != null) {
            citizenGpsStatusLabel.setText("📍 GPS Acquired: 13.0067°N, 80.2025°E (Accuracy ±3m, Guindy Corridor)");
            citizenGpsStatusLabel.getStyleClass().removeAll("gps-badge", "gps-badge-active");
            citizenGpsStatusLabel.getStyleClass().add("gps-badge-active");
        }
        if (statusLabel != null) {
            statusLabel.setText("Acquired GPS coordinates for Mount Road, Guindy, Chennai.");
        }
    }

    @FXML
    public void onCitizenFilterAll() {
        this.currentTicketFilter = "ALL";
        updateFilterButtonStyles(citizenFilterAllBtn);
        applyCitizenFilter();
    }

    @FXML
    public void onCitizenFilterRepair() {
        this.currentTicketFilter = "REPAIR";
        updateFilterButtonStyles(citizenFilterRepairBtn);
        applyCitizenFilter();
    }

    @FXML
    public void onCitizenFilterFixed() {
        this.currentTicketFilter = "FIXED";
        updateFilterButtonStyles(citizenFilterFixedBtn);
        applyCitizenFilter();
    }

    private void updateFilterButtonStyles(Button activeBtn) {
        Button[] buttons = {citizenFilterAllBtn, citizenFilterRepairBtn, citizenFilterFixedBtn};
        for (Button btn : buttons) {
            if (btn != null) {
                btn.getStyleClass().removeAll("filter-pill-active", "filter-pill");
                if (btn == activeBtn) {
                    btn.getStyleClass().add("filter-pill-active");
                } else {
                    btn.getStyleClass().add("filter-pill");
                }
            }
        }
    }

    private void applyCitizenFilter() {
        if (citizenFilteredList == null) return;
        String query = citizenSearchField != null && citizenSearchField.getText() != null ?
                citizenSearchField.getText().toLowerCase().trim() : "";

        citizenFilteredList.setPredicate(ticket -> {
            if (ticket == null) return false;

            boolean matchesStatus = switch (currentTicketFilter) {
                case "REPAIR" -> ticket.status() == TicketStatus.ASSIGNED || ticket.status() == TicketStatus.IN_PROGRESS;
                case "FIXED" -> ticket.status() == TicketStatus.COMPLETED || ticket.status() == TicketStatus.RESOLVED;
                default -> true;
            };
            if (!matchesStatus) return false;

            if (query.isEmpty()) return true;
            String id = ticket.ticketId().toLowerCase();
            String desc = ticket.description().toLowerCase();
            String contractor = ticket.assignedContractor() != null ? ticket.assignedContractor().toLowerCase() : "";
            return id.contains(query) || desc.contains(query) || contractor.contains(query);
        });

        if (citizenTicketCountBadge != null) {
            long activeCount = citizenTicketItems.stream()
                    .filter(t -> t.status() != TicketStatus.RESOLVED)
                    .count();
            citizenTicketCountBadge.setText(activeCount + " Active");
        }
    }

    private boolean isImageFile(File f) {
        if (f == null) return false;
        String name = f.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
    }

    public void setCitizenSelectedPhoto(String path) {
        if (path == null || path.isBlank()) return;
        if (citizenPhotoField != null) {
            citizenPhotoField.setText(path);
        }
        if (citizenUploadPromptBox != null && citizenPreviewContainer != null) {
            citizenUploadPromptBox.setVisible(false);
            citizenUploadPromptBox.setManaged(false);
            citizenPreviewContainer.setVisible(true);
            citizenPreviewContainer.setManaged(true);
        }
        ImageCache.loadAsync(path, 350, 175, img -> {
            if (citizenImagePreview != null) {
                citizenImagePreview.setImage(img);
            }
        });
    }

    private void handleCitizenTicketSelected(GovernanceTicket newVal) {
        if (newVal == null) return;
        if (citizenTimelineLabel != null) {
            citizenTimelineLabel.setText(String.format(
                    "Ticket %s | Status: %s | Severity: %s | Agency: %s | Contractor Warranty: %s | Notes: %s",
                    newVal.ticketId(),
                    newVal.status(),
                    newVal.severity(),
                    newVal.jurisdiction() != null ? newVal.jurisdiction().agencyName() : "Awaiting Triage",
                    newVal.inWarrantyDlp() ? "Covered by " + newVal.dlpWarrantyContractor() : "Standard allocation",
                    newVal.description()));
        }
        if (citizenSeverityBox != null) {
            citizenSeverityBox.setValue(newVal.severity());
        }
        updateCitizenSeverityVisuals(newVal.severity());

        String photoPath = newVal.photoPaths().isEmpty() ? "samples/defect_pothole_mg_rd.jpg" : newVal.photoPaths().get(0);
        setCitizenSelectedPhoto(photoPath);
    }

    @FXML
    public void onCitizenPresetSelected() {
        if (citizenGpsPresetBox == null) return;
        String val = citizenGpsPresetBox.getValue();
        if (val == null) return;

        String photoPath;
        if (val.contains("12.97162")) {
            currentCitizenLat = 12.97162;
            currentCitizenLon = 77.59459;
            if (citizenAddressField != null) citizenAddressField.setText("MG Road, near Signal, Bengaluru");
            if (citizenDescField != null) citizenDescField.setText("Recurring asphalt depression near MG Road junction.");
            photoPath = "samples/defect_pothole_mg_rd.jpg";
            if (citizenSeverityBox != null) citizenSeverityBox.setValue(SeverityLevel.CRITICAL);
            updateCitizenSeverityVisuals(SeverityLevel.CRITICAL);
        } else if (val.contains("13.08270")) {
            currentCitizenLat = 13.08270;
            currentCitizenLon = 80.27070;
            if (citizenAddressField != null) citizenAddressField.setText("NH-48 Arterial Corridor, Km 32.4");
            if (citizenDescField != null) citizenDescField.setText("Heavy alligator cracking on NHAI corridor lane 1.");
            photoPath = "samples/before_pothole_2.jpg";
            if (citizenSeverityBox != null) citizenSeverityBox.setValue(SeverityLevel.HIGH);
            updateCitizenSeverityVisuals(SeverityLevel.HIGH);
        } else {
            currentCitizenLat = 12.93520;
            currentCitizenLon = 77.62450;
            if (citizenAddressField != null) citizenAddressField.setText("100ft Main Road, Indiranagar, Bengaluru");
            if (citizenDescField != null) citizenDescField.setText("Surface raveling and potholes along Indiranagar main street.");
            photoPath = "samples/before_pothole_1.jpg";
            if (citizenSeverityBox != null) citizenSeverityBox.setValue(SeverityLevel.MEDIUM);
            updateCitizenSeverityVisuals(SeverityLevel.MEDIUM);
        }

        if (citizenLatField != null) citizenLatField.setText(String.format(Locale.US, "%.5f", currentCitizenLat));
        if (citizenLonField != null) citizenLonField.setText(String.format(Locale.US, "%.5f", currentCitizenLon));
        setCitizenSelectedPhoto(photoPath);
    }

    @FXML
    public void onCitizenBrowsePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Road Distress Evidence Photo");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files (*.jpg, *.jpeg, *.png)", "*.jpg", "*.jpeg", "*.png"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );
        Window window = citizenUploadZone != null && citizenUploadZone.getScene() != null ?
                citizenUploadZone.getScene().getWindow() :
                (citizenPhotoBrowseBtn != null && citizenPhotoBrowseBtn.getScene() != null ?
                        citizenPhotoBrowseBtn.getScene().getWindow() : null);
        File file = chooser.showOpenDialog(window);
        if (file != null) {
            setCitizenSelectedPhoto(file.getAbsolutePath());
        }
    }

    @FXML
    public void onCitizenSubmit() {
        try {
            double lat = currentCitizenLat;
            double lon = currentCitizenLon;
            if (citizenLatField != null && !citizenLatField.getText().isBlank()) {
                try {
                    lat = Double.parseDouble(citizenLatField.getText().trim());
                } catch (NumberFormatException ignored) {}
            }
            if (citizenLonField != null && !citizenLonField.getText().isBlank()) {
                try {
                    lon = Double.parseDouble(citizenLonField.getText().trim());
                } catch (NumberFormatException ignored) {}
            }

            String address = citizenAddressField != null ? citizenAddressField.getText().trim() : "";
            String desc = citizenDescField != null ? citizenDescField.getText().trim() : "";
            if (address.isBlank() && citizenAreaBox != null && citizenAreaBox.getValue() != null) {
                address = citizenAreaBox.getValue();
            }
            if (address.isBlank()) {
                address = "Mount Road, Guindy, Chennai";
            }

            String photo = (citizenPhotoField != null && !citizenPhotoField.getText().isBlank()) ?
                    citizenPhotoField.getText().trim() : "samples/defect_pothole_mg_rd.jpg";

            SeverityLevel sev = citizenSeverityBox != null && citizenSeverityBox.getValue() != null ?
                    citizenSeverityBox.getValue() : SeverityLevel.HIGH;

            String combinedDesc = desc.isBlank() ? "[" + address + "] Road surface distress reported by citizen." :
                    "[" + address + "] " + desc;

            GovernanceTicket ticket = governanceService.createTicket(
                    UserRole.CITIZEN, lat, lon, sev, List.of(photo), combinedDesc);

            refreshGovernanceViews();

            if (citizenTicketList != null) {
                citizenTicketList.getSelectionModel().select(ticket);
                citizenTicketList.scrollTo(ticket);
            }
            if (citizenTicketTable != null) {
                citizenTicketTable.getSelectionModel().select(ticket);
            }

            if (statusLabel != null) {
                statusLabel.setText(String.format("Issue %s registered. %s",
                        ticket.ticketId(),
                        ticket.inWarrantyDlp() ? "[CONTRACTOR FREE REPAIR GUARANTEE APPLIED]" : "Queued for department assignment."));
            }

            showSuccessAlert("Report Submitted Successfully",
                    String.format("Your road issue report #%s has been successfully registered.\n\n" +
                            "• Location: %s\n" +
                            "• Severity: %s\n" +
                            "• Warranty Coverage: %s\n\n" +
                            "You can monitor live progress on the right panel.",
                            ticket.ticketId(), address, ticket.severity(),
                            ticket.inWarrantyDlp() ? "Contractor Free Repair Guarantee (" + ticket.dlpWarrantyContractor() + ")" : "Standard Municipal Repair"));

        } catch (Exception e) {
            showErrorAlert("Submission Error", e.getMessage());
        }
    }

    private void showSuccessAlert(String title, String message) {
        LOGGER.log(Level.INFO, "{0}: {1}", title, message);
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message != null ? message : "Action completed successfully");
            alert.showAndWait();
        });
    }

    private String parseAddress(GovernanceTicket ticket) {
        String desc = ticket.description();
        if (desc != null && desc.startsWith("[") && desc.contains("]")) {
            return desc.substring(1, desc.indexOf("]")).trim();
        }
        if (Math.abs(ticket.latitude() - 12.97162) < 0.001) {
            return "MG Road Corridor, near Signal, Bengaluru";
        }
        if (Math.abs(ticket.latitude() - 13.08270) < 0.001) {
            return "NH-48 Arterial Expressway, Km 32.4";
        }
        if (Math.abs(ticket.latitude() - 12.93520) < 0.001) {
            return "100ft Main Road, Indiranagar, Bengaluru";
        }
        if (Math.abs(ticket.latitude() - 13.00670) < 0.001) {
            return "Mount Road, near Kathipara, Guindy, Chennai";
        }
        return String.format(Locale.US, "Road at %.4f°N, %.4f°E", ticket.latitude(), ticket.longitude());
    }

    private String parseDescriptionOnly(String rawDesc) {
        if (rawDesc == null || rawDesc.isBlank()) return "Road distress reported.";
        if (rawDesc.startsWith("[") && rawDesc.contains("]")) {
            String remaining = rawDesc.substring(rawDesc.indexOf("]") + 1).trim();
            return remaining.isEmpty() ? "Road surface distress reported by citizen." : remaining;
        }
        return rawDesc;
    }

    private String formatTicketTime(Instant instant) {
        if (instant == null) return "Recent";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault());
        return fmt.format(instant);
    }

    private HBox build4StepProgressTracker(TicketStatus status) {
        HBox tracker = new HBox(6);
        tracker.setAlignment(Pos.CENTER_LEFT);
        tracker.getStyleClass().add("tracker-container");

        int activeStep = switch (status) {
            case REPORTED -> 0;
            case ASSIGNED -> 1;
            case IN_PROGRESS -> 2;
            case COMPLETED, RESOLVED -> 3;
        };

        String[] stepNames = {"1. Reported", "2. Assigned", "3. Under Repair", "4. Fixed & Verified"};

        for (int i = 0; i < 4; i++) {
            boolean isDone = i <= activeStep;
            boolean isCurrent = i == activeStep;

            HBox stepNode = new HBox(4);
            stepNode.setAlignment(Pos.CENTER_LEFT);

            Label circle = new Label(isDone ? "✓" : String.valueOf(i + 1));
            circle.getStyleClass().add(isDone ? (isCurrent ? "tracker-circle-current" : "tracker-circle-done") : "tracker-circle-pending");

            Label nameLabel = new Label(stepNames[i]);
            nameLabel.getStyleClass().add(isDone ? "tracker-text-done" : "tracker-text-pending");

            stepNode.getChildren().addAll(circle, nameLabel);
            tracker.getChildren().add(stepNode);

            if (i < 3) {
                Region line = new Region();
                line.getStyleClass().add(i < activeStep ? "tracker-line-done" : "tracker-line-pending");
                HBox.setHgrow(line, Priority.ALWAYS);
                tracker.getChildren().add(line);
            }
        }

        return tracker;
    }

    private final class CitizenTicketCardCell extends ListCell<GovernanceTicket> {

        @Override
        protected void updateItem(GovernanceTicket ticket, boolean empty) {
            super.updateItem(ticket, empty);
            if (empty || ticket == null) {
                setText(null);
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
                return;
            }

            VBox card = new VBox(8);
            card.getStyleClass().add("citizen-ticket-card");

            // Row 1: Header (ID, Status Badge, Free Repair Guarantee badge, spacer, Date)
            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);

            Label idLabel = new Label("#" + ticket.ticketId());
            idLabel.getStyleClass().add("ticket-id-tag");

            Label statusBadge = new Label();
            statusBadge.getStyleClass().add("card-status-badge");
            switch (ticket.status()) {
                case REPORTED -> {
                    statusBadge.setText("UNDER REVIEW");
                    statusBadge.getStyleClass().add("status-reported");
                }
                case ASSIGNED -> {
                    statusBadge.setText("REPAIR SCHEDULED");
                    statusBadge.getStyleClass().add("status-assigned");
                }
                case IN_PROGRESS -> {
                    statusBadge.setText("REPAIR IN PROGRESS");
                    statusBadge.getStyleClass().add("status-progress");
                }
                case COMPLETED -> {
                    statusBadge.setText("REPAIRED (AUDIT PENDING)");
                    statusBadge.getStyleClass().add("status-completed");
                }
                case RESOLVED -> {
                    statusBadge.setText("FIXED & VERIFIED");
                    statusBadge.getStyleClass().add("status-resolved");
                }
            }

            header.getChildren().addAll(idLabel, statusBadge);

            if (ticket.inWarrantyDlp()) {
                Label dlpBadge = new Label("🛡️ Free Repair Guarantee");
                dlpBadge.getStyleClass().add("card-dlp-badge");
                header.getChildren().add(dlpBadge);
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            header.getChildren().add(spacer);

            Label dateLabel = new Label(formatTicketTime(ticket.reportedAt()));
            dateLabel.getStyleClass().add("card-date-label");
            header.getChildren().add(dateLabel);

            // Row 2: Content (Thumbnail + Details)
            HBox content = new HBox(12);
            content.setAlignment(Pos.CENTER_LEFT);

            StackPane thumbFrame = new StackPane();
            thumbFrame.getStyleClass().add("card-thumb-frame");
            thumbFrame.setPrefSize(72, 72);
            thumbFrame.setMinSize(72, 72);
            thumbFrame.setMaxSize(72, 72);

            ImageView thumbView = new ImageView();
            thumbView.setFitWidth(72);
            thumbView.setFitHeight(72);
            thumbView.setPreserveRatio(false);
            Rectangle clip = new Rectangle(72, 72);
            clip.setArcWidth(10);
            clip.setArcHeight(10);
            thumbView.setClip(clip);
            thumbFrame.getChildren().add(thumbView);

            String photoPath = ticket.photoPaths().isEmpty() ? "samples/defect_pothole_mg_rd.jpg" : ticket.photoPaths().get(0);
            ImageCache.loadAsync(photoPath, 72, 72, thumbView::setImage);

            VBox details = new VBox(4);
            HBox.setHgrow(details, Priority.ALWAYS);

            String rawDesc = ticket.description();
            String address = parseAddress(ticket);
            String cleanDesc = parseDescriptionOnly(rawDesc);

            Label addrLabel = new Label("📍 " + address);
            addrLabel.getStyleClass().add("card-address-label");

            Label descLabel = new Label(cleanDesc);
            descLabel.getStyleClass().add("card-desc-label");
            descLabel.setWrapText(true);

            HBox metaRow = new HBox(10);
            metaRow.setAlignment(Pos.CENTER_LEFT);

            Label sevLabel = new Label("Severity: " + ticket.severity());
            sevLabel.getStyleClass().add("card-meta-label");

            String agencyStr = ticket.jurisdiction() != null ? ticket.jurisdiction().agencyName() : "Civic Authority";
            String contractorStr = ticket.assignedContractor() != null ? " | " + ticket.assignedContractor() : "";
            Label agencyLabel = new Label("🏛️ " + agencyStr + contractorStr);
            agencyLabel.getStyleClass().add("card-meta-label");

            metaRow.getChildren().addAll(sevLabel, agencyLabel);
            details.getChildren().addAll(addrLabel, descLabel, metaRow);
            content.getChildren().addAll(thumbFrame, details);

            // Row 3: 4-Step Visual Progress Tracker Bar
            HBox progressTracker = build4StepProgressTracker(ticket.status());

            card.getChildren().addAll(header, content, progressTracker);
            setGraphic(card);
            setStyle("-fx-background-color: transparent; -fx-padding: 4px 6px;");
        }
    }

    // =========================================================================
    // NODAL OFFICER ACTIONS
    // =========================================================================

    @FXML
    public void onNodalAssign() {
        GovernanceTicket selected = nodalTable != null ? nodalTable.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            showErrorAlert("Assignment Error", "Please select an issue from the queue table.");
            return;
        }

        try {
            Jurisdiction jur = nodalJurisdictionBox.getValue() != null ? nodalJurisdictionBox.getValue() : Jurisdiction.STATE_HIGHWAY;
            String contractor = nodalContractorField.getText().trim();

            GovernanceTicket updated = governanceService.assignDepartment(
                    selected.ticketId(), UserRole.NODAL_OFFICER, jur, contractor);

            if (statusLabel != null) {
                statusLabel.setText(String.format("Work order issued: %s assigned to %s (%s)",
                        updated.ticketId(), updated.assignedContractor(), updated.jurisdiction().agencyName()));
            }
        } catch (Exception e) {
            showErrorAlert("Assignment Error", e.getMessage());
        }
    }

    // =========================================================================
    // SITE ENGINEER ACTIONS
    // =========================================================================

    @FXML
    public void onSiteStartWork() {
        GovernanceTicket selected = siteTable != null ? siteTable.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            showErrorAlert("Site Error", "Please select an assigned work order from the table.");
            return;
        }

        try {
            GovernanceTicket updated = governanceService.startWork(selected.ticketId(), UserRole.SITE_ENGINEER);
            if (statusLabel != null) {
                statusLabel.setText("Work started on " + updated.ticketId() + " (Status: IN_PROGRESS)");
            }
        } catch (Exception e) {
            showErrorAlert("Start Work Error", e.getMessage());
        }
    }

    @FXML
    public void onSiteAddMaterial() {
        GovernanceTicket selected = siteTable != null ? siteTable.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            showErrorAlert("Site Error", "Please select an active work order from the table.");
            return;
        }
        String mat = siteMaterialField != null ? siteMaterialField.getText().trim() : "";
        if (mat.isBlank()) {
            showErrorAlert("Material Error", "Please enter material item description (e.g. 2 Ton Cold Mix).");
            return;
        }

        try {
            governanceService.requestMaterials(selected.ticketId(), UserRole.SITE_ENGINEER, mat);
            if (siteMaterialField != null) siteMaterialField.clear();
            if (statusLabel != null) {
                statusLabel.setText("Procurement requisition logged for " + selected.ticketId());
            }
        } catch (Exception e) {
            showErrorAlert("Material Request Error", e.getMessage());
        }
    }

    @FXML
    public void onSiteBrowseBefore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Before-Repair Site Photo");
        Window window = siteBeforeBrowseBtn != null && siteBeforeBrowseBtn.getScene() != null ?
                siteBeforeBrowseBtn.getScene().getWindow() : null;
        File file = chooser.showOpenDialog(window);
        if (file != null) {
            String path = file.getAbsolutePath();
            if (siteBeforeImgField != null) siteBeforeImgField.setText(path);
            ImageCache.loadAsync(path, 190, 120, img -> {
                if (siteBeforeImageView != null) siteBeforeImageView.setImage(img);
            });
        }
    }

    @FXML
    public void onSiteBrowseAfter() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select After-Repair Site Photo");
        Window window = siteAfterBrowseBtn != null && siteAfterBrowseBtn.getScene() != null ?
                siteAfterBrowseBtn.getScene().getWindow() : null;
        File file = chooser.showOpenDialog(window);
        if (file != null) {
            String path = file.getAbsolutePath();
            if (siteAfterImgField != null) siteAfterImgField.setText(path);
            ImageCache.loadAsync(path, 190, 120, img -> {
                if (siteAfterImageView != null) siteAfterImageView.setImage(img);
            });
        }
    }

    @FXML
    public void onSiteCompleteWork() {
        GovernanceTicket selected = siteTable != null ? siteTable.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            showErrorAlert("Site Error", "Please select an active work order from the table.");
            return;
        }

        String before = siteBeforeImgField != null ? siteBeforeImgField.getText().trim() : "";
        String after = siteAfterImgField != null ? siteAfterImgField.getText().trim() : "";

        if (before.isBlank()) before = "samples/before_pothole_1.jpg";
        if (after.isBlank()) after = "samples/after_patch_1.jpg";

        try {
            GovernanceTicket completed = governanceService.completeWork(
                    selected.ticketId(), UserRole.SITE_ENGINEER, before, after, null);

            if (statusLabel != null) {
                statusLabel.setText(String.format("Work order %s completed. Audit Status: %s",
                        completed.ticketId(), completed.auditApprovalStatus().label()));
            }
        } catch (Exception e) {
            showErrorAlert("Work Completion Error", e.getMessage());
        }
    }

    // =========================================================================
    // QUALITY AUDITOR ACTIONS
    // =========================================================================

    @FXML
    public void onAuditorApprove() {
        GovernanceTicket selected = auditorTable != null ? auditorTable.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            showErrorAlert("Audit Error", "Please select a completed work order from the audit table.");
            return;
        }

        String notes = auditorNotesField != null ? auditorNotesField.getText().trim() : "";
        if (notes.isBlank()) notes = "Quality and site compaction verified compliant with IRC standards.";

        try {
            GovernanceTicket resolved = governanceService.auditTicket(
                    selected.ticketId(), UserRole.QUALITY_AUDITOR, true, notes);

            if (statusLabel != null) {
                statusLabel.setText("Payment authorized. Work order " + resolved.ticketId() + " formally RESOLVED.");
            }
        } catch (Exception e) {
            showErrorAlert("Audit Approval Error", e.getMessage());
        }
    }

    @FXML
    public void onAuditorReject() {
        GovernanceTicket selected = auditorTable != null ? auditorTable.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            showErrorAlert("Audit Error", "Please select a completed work order from the audit table.");
            return;
        }

        String notes = auditorNotesField != null ? auditorNotesField.getText().trim() : "";
        if (notes.isBlank()) notes = "Workmanship defective: Rework required on edge compaction.";

        try {
            GovernanceTicket reworked = governanceService.auditTicket(
                    selected.ticketId(), UserRole.QUALITY_AUDITOR, false, notes);

            if (statusLabel != null) {
                statusLabel.setText("Audit REJECTED for " + reworked.ticketId() + ". Returned to IN_PROGRESS for rework.");
            }
        } catch (Exception e) {
            showErrorAlert("Audit Rejection Error", e.getMessage());
        }
    }

    private void showErrorAlert(String title, String message) {
        LOGGER.log(Level.WARNING, "{0}: {1}", title, message);
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message != null ? message : "Action failed");
            alert.showAndWait();
        });
    }

    // =========================================================================
    // INSPECTION PIPELINE LOGIC (Baseline preserved)
    // =========================================================================

    private void renderSnapshot(FrameSnapshot snap) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("renderSnapshot must be called on JavaFX Application Thread");
        }
        if (originalView != null && snap.original() != null) {
            GraphicsContext gc = originalView.getGraphicsContext2D();
            double cw = originalView.getWidth();
            double ch = originalView.getHeight();
            gc.clearRect(0, 0, cw, ch);
            WritableImage img = snap.original();
            double iw = img.getWidth();
            double ih = img.getHeight();
            if (iw > 0 && ih > 0 && cw > 0 && ch > 0) {
                double scale = Math.min(cw / iw, ch / ih);
                double rw = iw * scale;
                double rh = ih * scale;
                gc.drawImage(img, (cw - rw) / 2.0, (ch - rh) / 2.0, rw, rh);
            }
        }
        if (annotatedCanvas != null) {
            annotatedCanvas.render(snap);
        }
    }

    private void appendDamageRow(DamageRow row) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("appendDamageRow must be called on JavaFX Application Thread");
        }
        if (logTable != null) {
            logTable.getItems().add(row);
        } else {
            damageRows.add(row);
        }
    }

    @FXML
    public void onOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Inspection Media File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Media Files (*.mp4, *.avi, *.jpg, *.png)",
                        "*.mp4", "*.avi", "*.mov", "*.mkv", "*.jpg", "*.jpeg", "*.png", "*.bmp"),
                new FileChooser.ExtensionFilter("Video Files (*.mp4, *.avi, *.mov, *.mkv)",
                        "*.mp4", "*.avi", "*.mov", "*.mkv"),
                new FileChooser.ExtensionFilter("Image Files (*.jpg, *.jpeg, *.png, *.bmp)",
                        "*.jpg", "*.jpeg", "*.png", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );

        Window window = openButton != null && openButton.getScene() != null ? openButton.getScene().getWindow() : null;
        File file = chooser.showOpenDialog(window);
        if (file != null) {
            selectedPath = file.toPath();
            if (statusLabel != null) {
                statusLabel.setText("Loaded: " + file.getName());
            }
            if (startButton != null) {
                startButton.setDisable(false);
            }
        }
    }

    @FXML
    public void onStart() {
        if (selectedPath == null) {
            return;
        }

        if (startButton != null) startButton.setDisable(true);
        if (openButton != null) openButton.setDisable(true);
        if (stopButton != null) stopButton.setDisable(false);
        if (statusLabel != null) statusLabel.setText("Inspecting: " + selectedPath.getFileName() + "...");
        if (progress != null) progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        damageRows.clear();
        alertItems.clear();

        if (metricsTimeline != null) {
            metricsTimeline.stop();
        }
        metricsTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> {
            InspectionPipeline pipeline = activePipeline;
            if (pipeline != null && statusLabel != null) {
                PipelineMetrics.Snapshot m = pipeline.metrics().snapshot();
                statusLabel.setText(String.format(Locale.US,
                        "FPS: %.1f | In: %d | Out: %d | Dropped: %d | Errors: %d | Skipped S2: %d",
                        m.fps(), m.framesIn(), m.framesOut(), m.droppedFrames(), m.errors(), m.framesSkippedStage2()));
            }
        }));
        metricsTimeline.setCycleCount(Animation.INDEFINITE);
        metricsTimeline.play();

        executor.submit(() -> {
            try {
                AppConfig appConfig = AppConfig.load();
                PipelineConfig config = new PipelineConfig(
                        Path.of("models"),
                        appConfig.frameStride(),
                        appConfig.queueCapacity(),
                        appConfig.stage1Threshold(),
                        appConfig.skipStage2WhenClean()
                );

                Consumer<FrameResult> resultSink = result -> {
                    long fIdx = result.frameIndex();
                    long tMs = result.timestampMillis();
                    for (DamageAssessment da : result.assessments()) {
                        Detection det = da.detection();
                        BoundingBox b = det.box();
                        DamageRow row = new DamageRow(
                                fIdx,
                                tMs,
                                det.damageClass().label(),
                                det.confidence(),
                                b.x1(),
                                b.y1(),
                                b.x2(),
                                b.y2(),
                                da.areaRatio(),
                                da.aspectRatio(),
                                da.priorityScore(),
                                da.level(),
                                da.explanation()
                        );
                        Platform.runLater(() -> appendDamageRow(row));

                        if (da.level() == SeverityLevel.CRITICAL || da.level() == SeverityLevel.HIGH) {
                            String alertMsg = String.format(Locale.US,
                                    "ALERT: %s detected at frame %d (%d ms) - Priority %d (%s)",
                                    det.damageClass().label(), fIdx, tMs, da.priorityScore(), da.level());
                            Platform.runLater(() -> {
                                alertItems.add(0, alertMsg);
                                if (alertItems.size() > 100) {
                                    alertItems.remove(alertItems.size() - 1);
                                }
                            });
                        }
                    }
                };

                Consumer<FrameSnapshot> frameSink = pendingSnapshot::set;

                InspectionPipeline pipeline = pipelineFactory.create(config, resultSink, frameSink);
                this.activePipeline = pipeline;
                try (FrameSource source = FrameSource.open(selectedPath)) {
                    pipeline.run(source);
                }

                Platform.runLater(() -> {
                    if (statusLabel != null) {
                        PipelineMetrics.Snapshot finalMetrics = pipeline.metrics().snapshot();
                        statusLabel.setText(String.format(Locale.US,
                                "Completed: %d frames processed, %d damages detected (FPS: %.1f)",
                                finalMetrics.framesOut(), damageRows.size(), finalMetrics.fps()));
                    }
                    if (progress != null) progress.setProgress(1.0);
                });

            } catch (Exception ex) {
                LOGGER.log(Level.ERROR, "Inspection execution failed: {0}", ex.getMessage());
                Platform.runLater(() -> {
                    if (statusLabel != null) statusLabel.setText("Inspection failed: " + ex.getMessage());
                    if (progress != null) progress.setProgress(0.0);
                });
            } finally {
                Platform.runLater(() -> {
                    if (startButton != null) startButton.setDisable(false);
                    if (openButton != null) openButton.setDisable(false);
                    if (stopButton != null) stopButton.setDisable(true);
                    if (metricsTimeline != null) metricsTimeline.stop();
                });
                InspectionPipeline p = this.activePipeline;
                if (p != null) {
                    try {
                        p.close();
                    } catch (Exception ignored) {
                    }
                    this.activePipeline = null;
                }
            }
        });
    }

    @FXML
    public void onStop() {
        InspectionPipeline pipeline = activePipeline;
        if (pipeline != null) {
            pipeline.cancel();
            if (statusLabel != null) {
                statusLabel.setText("Inspection cancelled by user.");
            }
        }
        if (stopButton != null) stopButton.setDisable(true);
        if (startButton != null) startButton.setDisable(false);
        if (openButton != null) openButton.setDisable(false);
        if (progress != null) progress.setProgress(0.0);
    }

    @FXML
    public void onExport() {
        if (damageRows.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export CSV");
            alert.setHeaderText(null);
            alert.setContentText("No damage detection records available to export.");
            alert.show();
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Damage Assessment CSV Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"));
        chooser.setInitialFileName("road_damage_report.csv");

        Window window = exportButton != null && exportButton.getScene() != null ? exportButton.getScene().getWindow() : null;
        File file = chooser.showSaveDialog(window);
        if (file == null) {
            return;
        }

        Path target = file.toPath();
        List<DamageRow> rowsToExport = new ArrayList<>(damageRows);

        if (statusLabel != null) statusLabel.setText("Exporting report to " + file.getName() + "...");
        if (exportButton != null) exportButton.setDisable(true);

        executor.submit(() -> {
            try {
                CsvReportExporter.export(target, rowsToExport);
                Platform.runLater(() -> {
                    if (statusLabel != null) statusLabel.setText("Exported " + rowsToExport.size() + " records to " + target.getFileName());
                    if (exportButton != null) exportButton.setDisable(false);
                });
            } catch (Exception e) {
                LOGGER.log(Level.ERROR, "Export failed: {0}", e.getMessage());
                Platform.runLater(() -> {
                    if (statusLabel != null) statusLabel.setText("Export failed: " + e.getMessage());
                    if (exportButton != null) exportButton.setDisable(false);
                });
            }
        });
    }

    /**
     * Cleanly shuts down animations and background executors.
     */
    public void shutdown() {
        if (metricsTimeline != null) {
            metricsTimeline.stop();
        }
        if (uiPacer != null) {
            uiPacer.stop();
        }
        InspectionPipeline pipelineToStop = activePipeline;
        if (pipelineToStop != null) {
            try {
                pipelineToStop.cancel();
                pipelineToStop.close();
            } catch (Exception ignored) {
            }
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    void setSelectedPath(Path path) {
        this.selectedPath = Objects.requireNonNull(path, "path must not be null");
        if (statusLabel != null) {
            statusLabel.setText("Loaded: " + path.getFileName());
        }
        if (startButton != null) {
            startButton.setDisable(false);
        }
    }

    Path getSelectedPath() {
        return selectedPath;
    }

    ObservableList<DamageRow> getDamageRows() {
        return damageRows;
    }

    Label getStatusLabel() {
        return statusLabel;
    }

    TableView<DamageRow> getLogTable() {
        return logTable;
    }

    public GovernanceService getGovernanceService() {
        return governanceService;
    }

    public StaffPrincipal getCurrentPrincipal() {
        return currentPrincipal;
    }
}
