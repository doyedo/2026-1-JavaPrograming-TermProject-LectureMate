package com.lecturemate.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lecturemate.dao.DatabaseManager;
import com.lecturemate.dao.LectureDAO;
import com.lecturemate.dao.ScheduleDAO;
import com.lecturemate.model.Lecture;
import com.lecturemate.model.Schedule;
import com.lecturemate.service.AudioRecordingService;
import com.lecturemate.service.AudioTranscriptionService;
import com.lecturemate.service.AudioTranscriptionService.TranscriptionOptions;
import com.lecturemate.service.AudioTranscriptionService.TranscriptionResult;
import com.lecturemate.service.LectureSummaryService;
import com.lecturemate.service.LectureSummaryService.SummaryResult;
import com.lecturemate.service.LectureQuestionService;
import com.lecturemate.service.LectureQuestionService.QuestionResult;
import com.lecturemate.util.ApiConfig;
import com.lecturemate.util.DateUtil;
import com.lecturemate.util.FileValidator;
import com.lecturemate.util.FileValidator.ValidationResult;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.util.Duration;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.IndexRange;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Coordinates the dashboard UI with SQLite DAOs and OpenAI service wrappers.
 */
public class MainController {

    private static final List<String> SUMMARY_FORMATS = List.of(
            "기본 강의 요약",
            "시험 대비 요약",
            "키워드 중심 요약",
            "복습 질문 포함 요약");
    private static final List<String> SCHEDULE_TYPES = List.of("복습", "과제", "시험", "기타");
    private static final int DEFAULT_NOTE_FONT_SIZE = 14;
    private static final String DEFAULT_NOTE_FONT = "Malgun Gothic";
    private static final double STATUS_COMPACT_WIDTH = 260;
    private static final double STATUS_EXPANDED_WIDTH = 760;
    private static final double STATUS_TEXT_CHROME_WIDTH = 122;
    private static final String IMAGE_MARKER_PREFIX = "[[LECTUREMATE_IMAGE:";
    private static final String IMAGE_MARKER_SUFFIX = "]]";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<List<StoredStyleRange>> STYLE_RANGE_LIST_TYPE = new TypeReference<>() {
    };

    private final LectureDAO lectureDAO = new LectureDAO();
    private final ScheduleDAO scheduleDAO = new ScheduleDAO();
    private final AudioRecordingService audioRecordingService = new AudioRecordingService();
    private final AudioTranscriptionService audioTranscriptionService = new AudioTranscriptionService();
    private final LectureSummaryService lectureSummaryService = new LectureSummaryService();
    private final LectureQuestionService lectureQuestionService = new LectureQuestionService();
    private final ObservableList<Lecture> lectures = FXCollections.observableArrayList();
    private final ObservableList<Schedule> schedules = FXCollections.observableArrayList();

    private Lecture selectedLecture;
    private File selectedAudioFile;
    private YearMonth currentCalendarMonth = YearMonth.now();
    private LocalDate selectedDate;
    private boolean syncingSelection;
    private Timeline statusResizeAnimation;
    private Timeline recordingTimer;
    private long recordingStartedAtMillis;
    private long recordingPausedAtMillis;
    private long totalPausedMillis;
    private MediaPlayer lectureAudioPlayer;
    private Duration lectureAudioDuration = Duration.ZERO;
    private boolean updatingAudioSlider;
    private LibraryEditorMode libraryEditorMode = LibraryEditorMode.SUMMARY;
    private WorkspaceViewMode workspaceViewMode = WorkspaceViewMode.INTEGRATED;
    private long workspaceSwitchVersion;

    @FXML private HBox statusBanner;
    @FXML private ProgressIndicator globalProgressIndicator;
    @FXML private Label taskNameLabel;
    @FXML private Label statusMessageLabel;
    @FXML private Label statusResultLabel;
    @FXML private TabPane navTabPane;
    @FXML private Tab voiceTab;
    @FXML private Tab lectureListTab;
    @FXML private Tab scheduleTab;
    @FXML private Tab questionTab;
    @FXML private TextField lectureTitleField;
    @FXML private TextField lectureSubjectField;
    @FXML private Button createLectureButton;
    @FXML private Button updateLectureButton;
    @FXML private Button deleteLectureButton;
    @FXML private ListView<Lecture> lectureListView;
    @FXML private ListView<Lecture> summaryNoteListView;
    @FXML private Button playLectureAudioButton;
    @FXML private Button pauseLectureAudioButton;
    @FXML private Button stopLectureAudioButton;
    @FXML private Slider audioPlaybackSlider;
    @FXML private Label audioPlaybackTimeLabel;
    @FXML private Label audioPlaybackStatusLabel;
    @FXML private Button previousMonthButton;
    @FXML private Button nextMonthButton;
    @FXML private Label calendarMonthLabel;
    @FXML private GridPane calendarGrid;
    @FXML private Button showAllSchedulesButton;
    @FXML private Label scheduleFilterLabel;
    @FXML private Button addScheduleButton;
    @FXML private ListView<Schedule> scheduleListView;
    @FXML private Button chooseFileButton;
    @FXML private Button startRecordingButton;
    @FXML private Button pauseRecordingButton;
    @FXML private Button stopRecordingButton;
    @FXML private Label recordingStatusLabel;
    @FXML private Label selectedFileNameLabel;
    @FXML private TextField selectedFilePathField;
    @FXML private ComboBox<LanguageOption> audioLanguageComboBox;
    @FXML private ComboBox<String> sttModelComboBox;
    @FXML private ComboBox<String> summaryFormatComboBox;
    @FXML private Button transcribeButton;
    @FXML private Button summaryButton;
    @FXML private Label currentLectureTitleLabel;
    @FXML private Label currentLectureSubjectLabel;
    @FXML private Label currentLectureCreatedAtLabel;
    @FXML private TextArea transcriptTextArea;
    @FXML private Button saveTranscriptButton;
    @FXML private Label transcriptStatusLabel;
    @FXML private TextField summarySearchField;
    @FXML private Button searchSummaryButton;
    @FXML private ComboBox<Integer> noteFontSizeComboBox;
    @FXML private ComboBox<String> noteFontComboBox;
    @FXML private StackPane summaryEditorContainer;
    @FXML private Button openIntegratedWorkspaceButton;
    @FXML private Button editLectureButton;
    @FXML private Button saveSummaryButton;
    @FXML private Label summaryStatusLabel;
    @FXML private StackPane workspaceStack;
    @FXML private HBox conversionWorkspace;
    @FXML private VBox libraryWorkspace;
    @FXML private Label libraryLectureTitleLabel;
    @FXML private Label libraryLectureMetaLabel;
    @FXML private Label libraryModeLabel;
    @FXML private Button toggleLibraryModeButton;
    @FXML private Button splitWorkspaceButton;
    @FXML private TextField librarySearchField;
    @FXML private Button searchLibraryButton;
    @FXML private ComboBox<Integer> libraryFontSizeComboBox;
    @FXML private ComboBox<String> libraryFontComboBox;
    @FXML private StackPane libraryEditorContainer;
    @FXML private Button saveLibraryEditorButton;
    @FXML private Label libraryEditorStatusLabel;
    @FXML private Label chatContextLabel;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatMessagesBox;
    @FXML private TextArea chatQuestionTextArea;
    @FXML private Button sendQuestionButton;
    @FXML private Button clearChatButton;

    private InlineCssTextArea summaryTextArea;
    private InlineCssTextArea libraryTextArea;
    private String currentSummaryInputStyle = buildInlineStyle(DEFAULT_NOTE_FONT_SIZE, DEFAULT_NOTE_FONT);
    private String currentLibraryInputStyle = buildInlineStyle(DEFAULT_NOTE_FONT_SIZE, DEFAULT_NOTE_FONT);

    /**
     * Initializes database tables, UI defaults, list bindings, and calendar state.
     */
    @FXML
    private void initialize() {
        DatabaseManager.initializeDatabase();
        localizeButtons();
        initializeSummaryEditor();
        initializeLibraryEditor();
        initializeComboBoxes();
        initializeLists();
        initializeNavigation();
        initializeAudioPlayback();
        setProgressVisible(false);
        clearLectureWorkspace();
        loadLectures();
        renderCalendar();
        refreshSchedules();
        setRecordingControls(false);
        showStatus("Ready", "Select or create a lecture to begin.", "READY", StatusKind.NORMAL);
    }

    private void localizeButtons() {
        createLectureButton.setText("새 강의");
        updateLectureButton.setText("수정");
        deleteLectureButton.setText("삭제");
        previousMonthButton.setText("이전");
        nextMonthButton.setText("다음");
        showAllSchedulesButton.setText("전체 일정 보기");
        addScheduleButton.setText("일정 추가");
        playLectureAudioButton.setText("");
        pauseLectureAudioButton.setText("");
        stopLectureAudioButton.setText("");
        chooseFileButton.setText("파일 선택");
        transcribeButton.setText("텍스트 변환");
        summaryButton.setText("요약 생성");
        saveTranscriptButton.setText("원문 저장");
        searchSummaryButton.setText("검색");
        saveSummaryButton.setText("정리 노트 저장");
        openIntegratedWorkspaceButton.setText("통합 보기");
        editLectureButton.setText("제목 수정");
        updateLibraryModeControls();
        splitWorkspaceButton.setText("분할 보기");
        searchLibraryButton.setText("검색");
        saveLibraryEditorButton.setText("선택 내용 저장");
        sendQuestionButton.setText("질문 보내기");
        clearChatButton.setText("지우기");
        startRecordingButton.setText("녹음");
        pauseRecordingButton.setText("일시정지");
        stopRecordingButton.setText("중지");
    }

    private void initializeComboBoxes() {
        audioLanguageComboBox.setItems(FXCollections.observableArrayList(
                new LanguageOption("한국어", "ko"),
                new LanguageOption("영어", "en"),
                new LanguageOption("일본어", "ja"),
                new LanguageOption("중국어", "zh"),
                new LanguageOption("자동 감지", "")));
        audioLanguageComboBox.getSelectionModel().selectFirst();

        sttModelComboBox.setItems(FXCollections.observableArrayList(
                ApiConfig.DEFAULT_TRANSCRIPTION_MODEL,
                "gpt-4o-transcribe",
                "whisper-1"));
        sttModelComboBox.getSelectionModel().selectFirst();

        summaryFormatComboBox.setItems(FXCollections.observableArrayList(SUMMARY_FORMATS));
        summaryFormatComboBox.getSelectionModel().selectFirst();

        noteFontSizeComboBox.setItems(FXCollections.observableArrayList(12, 14, 16, 18, 20, 24));
        noteFontSizeComboBox.getSelectionModel().select(Integer.valueOf(DEFAULT_NOTE_FONT_SIZE));
        noteFontComboBox.setItems(FXCollections.observableArrayList(
                DEFAULT_NOTE_FONT,
                "Noto Sans KR",
                "Pretendard",
                "Arial"));
        noteFontComboBox.getSelectionModel().selectFirst();
        noteFontSizeComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applySummaryTextStyle());
        noteFontComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applySummaryTextStyle());
        applySummaryTextStyle();

        libraryFontSizeComboBox.setItems(FXCollections.observableArrayList(12, 14, 16, 18, 20, 24));
        libraryFontSizeComboBox.getSelectionModel().select(Integer.valueOf(DEFAULT_NOTE_FONT_SIZE));
        libraryFontComboBox.setItems(FXCollections.observableArrayList(
                DEFAULT_NOTE_FONT,
                "Noto Sans KR",
                "Pretendard",
                "Arial"));
        libraryFontComboBox.getSelectionModel().selectFirst();
        libraryFontSizeComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyLibraryEditorStyle());
        libraryFontComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyLibraryEditorStyle());
        applyLibraryEditorStyle();
    }

    private void initializeSummaryEditor() {
        summaryTextArea = new InlineCssTextArea();
        summaryTextArea.getStyleClass().add("summary-rich-editor");
        summaryTextArea.setWrapText(true);
        summaryTextArea.setPrefHeight(0);
        summaryTextArea.setMaxHeight(Double.MAX_VALUE);
        summaryTextArea.setStyle(currentSummaryInputStyle);
        summaryTextArea.plainTextChanges().subscribe(change -> {
            String inserted = change.getInserted();
            if (inserted == null || inserted.isEmpty()) {
                return;
            }
            int start = change.getPosition();
            int end = start + inserted.length();
            summaryTextArea.setStyle(start, end, currentSummaryInputStyle);
        });
        summaryEditorContainer.getChildren().setAll(summaryTextArea);
    }

    private void initializeLibraryEditor() {
        libraryTextArea = new InlineCssTextArea();
        libraryTextArea.getStyleClass().add("summary-rich-editor");
        libraryTextArea.getStyleClass().add("library-rich-editor");
        libraryTextArea.setWrapText(true);
        libraryTextArea.setPrefHeight(0);
        libraryTextArea.setMaxHeight(Double.MAX_VALUE);
        libraryTextArea.setStyle(currentLibraryInputStyle);
        libraryTextArea.plainTextChanges().subscribe(change -> {
            String inserted = change.getInserted();
            if (inserted == null || inserted.isEmpty()) {
                return;
            }
            int start = change.getPosition();
            int end = start + inserted.length();
            libraryTextArea.setStyle(start, end, currentLibraryInputStyle);
        });
        libraryEditorContainer.getChildren().setAll(libraryTextArea);
    }

    private void initializeLists() {
        lectureListView.setItems(lectures);
        summaryNoteListView.setItems(lectures);
        lectureListView.setCellFactory(listView -> new LectureCell(false));
        summaryNoteListView.setCellFactory(listView -> new LectureCell(true));
        scheduleListView.setItems(schedules);
        scheduleListView.setCellFactory(listView -> new ScheduleCardCell());

        lectureListView.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, lecture) -> handleLectureSelectionFromList(lecture, true));
        summaryNoteListView.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, lecture) -> handleLectureSelectionFromList(lecture, false));
    }

    private void initializeNavigation() {
        navTabPane.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldTab, newTab) -> {
                    if (newTab == lectureListTab && oldTab != lectureListTab) {
                        workspaceViewMode = WorkspaceViewMode.INTEGRATED;
                    } else if (newTab == voiceTab) {
                        workspaceViewMode = WorkspaceViewMode.SPLIT;
                    }
                    syncWorkspaceWithSelectedTab(newTab);
                    Platform.runLater(() -> syncWorkspaceWithSelectedTab(navTabPane.getSelectionModel().getSelectedItem()));
                });
        syncWorkspaceWithSelectedTab(navTabPane.getSelectionModel().getSelectedItem());
    }

    private void initializeAudioPlayback() {
        audioPlaybackSlider.setMin(0);
        audioPlaybackSlider.setMax(1);
        audioPlaybackSlider.setValue(0);
        audioPlaybackSlider.setDisable(true);
        audioPlaybackSlider.valueChangingProperty().addListener((observable, wasChanging, isChanging) -> {
            if (!isChanging) {
                seekLectureAudio();
            }
        });
        audioPlaybackSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (audioPlaybackSlider.isValueChanging()) {
                updateAudioTime(Duration.seconds(newValue.doubleValue()), lectureAudioDuration);
            }
        });
        audioPlaybackSlider.setOnMouseReleased(event -> seekLectureAudio());
        updateAudioTime(Duration.ZERO, Duration.ZERO);
    }

    private void syncWorkspaceWithSelectedTab(Tab selectedTab) {
        if (selectedTab == lectureListTab) {
            showWorkspaceForCurrentMode();
        } else if (selectedTab == voiceTab) {
            showConversionWorkspace();
        } else if (selectedTab == questionTab) {
            updateChatContextLabel();
        }
    }

    /**
     * Creates a lecture row from the title and subject fields.
     */
    @FXML
    private void handleCreateLecture() {
        String title = normalize(lectureTitleField.getText());
        String subject = normalize(lectureSubjectField.getText());
        if (title.isBlank() || subject.isBlank()) {
            showWarning("강의 생성", "강의 제목과 과목명을 입력해 주세요.");
            return;
        }

        try {
            Lecture lecture = lectureDAO.insert(new Lecture(title, subject));
            loadLectures();
            selectLectureById(lecture.getId());
            showStatus("Lecture", "강의를 저장했습니다.", "SUCCESS", StatusKind.SUCCESS);
        } catch (IllegalStateException exception) {
            showError("강의 생성 실패", exception.getMessage());
        }
    }

    /**
     * Updates the selected lecture's title and subject from the side form.
     */
    @FXML
    private void handleUpdateLecture() {
        if (!ensureLectureSelected("강의 정보 수정")) {
            return;
        }
        updateSelectedLectureInfo(normalize(lectureTitleField.getText()), normalize(lectureSubjectField.getText()));
    }

    /**
     * Opens a compact edit dialog from the lecture-list tab.
     */
    @FXML
    private void handleEditLectureInfo() {
        if (!ensureLectureSelected("강의 제목 수정")) {
            return;
        }

        Dialog<LectureEditData> dialog = new Dialog<>();
        dialog.setTitle("강의 제목 수정");
        dialog.setHeaderText("강의 정보를 수정합니다.");

        ButtonType saveButtonType = new ButtonType("저장", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField titleField = new TextField(defaultText(selectedLecture.getTitle(), ""));
        TextField subjectField = new TextField(defaultText(selectedLecture.getSubject(), ""));
        titleField.setPromptText("강의 제목");
        subjectField.setPromptText("과목명");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(10));
        form.add(new Label("강의 제목"), 0, 0);
        form.add(titleField, 1, 0);
        form.add(new Label("과목명"), 0, 1);
        form.add(subjectField, 1, 1);
        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(subjectField, Priority.ALWAYS);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getStyleClass().add("schedule-dialog");

        dialog.setResultConverter(buttonType -> buttonType == saveButtonType
                ? new LectureEditData(normalize(titleField.getText()), normalize(subjectField.getText()))
                : null);

        dialog.showAndWait().ifPresent(data -> updateSelectedLectureInfo(data.title(), data.subject()));
    }

    private void updateSelectedLectureInfo(String title, String subject) {
        if (selectedLecture == null) {
            showWarning("강의 정보 수정", "수정할 강의를 선택해 주세요.");
            return;
        }
        if (title.isBlank() || subject.isBlank()) {
            showWarning("강의 정보 수정", "강의 제목과 과목명을 입력해 주세요.");
            return;
        }

        try {
            selectedLecture.setTitle(title);
            selectedLecture.setSubject(subject);
            lectureDAO.update(selectedLecture);
            loadLecturesPreservingSelection();
            showStatus("Lecture", "강의 정보를 수정했습니다.", "SUCCESS", StatusKind.SUCCESS);
        } catch (IllegalStateException exception) {
            showError("강의 정보 수정 실패", exception.getMessage());
        }
    }

    /**
     * Deletes the selected lecture and cascades related rows through SQLite foreign keys.
     */
    @FXML
    private void handleDeleteLecture() {
        if (selectedLecture == null) {
            showWarning("강의 삭제", "삭제할 강의를 선택해 주세요.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("강의 삭제");
        confirm.setHeaderText("선택한 강의를 삭제할까요?");
        confirm.setContentText("연결된 원문, 정리 노트, 일정도 함께 삭제됩니다.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        try {
            stopLectureAudio(false);
            lectureDAO.delete(selectedLecture.getId());
            selectedLecture = null;
            selectedAudioFile = null;
            clearLectureWorkspace();
            loadLectures();
            refreshSchedules();
            renderCalendar();
            showStatus("Lecture", "강의를 삭제했습니다.", "SUCCESS", StatusKind.SUCCESS);
        } catch (IllegalStateException exception) {
            showError("강의 삭제 실패", exception.getMessage());
        }
    }

    /**
     * Opens a FileChooser and validates mp3, wav, and m4a files up to 25MB.
     */
    @FXML
    private void handleChooseAudioFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("오디오 파일 선택");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Audio Files (*.mp3, *.wav, *.m4a)", "*.mp3", "*.wav", "*.m4a"));
        Window owner = chooseFileButton.getScene() == null ? null : chooseFileButton.getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            showStatus("File", "파일 선택을 취소했습니다.", "READY", StatusKind.NORMAL);
            return;
        }

        ValidationResult validation = FileValidator.validateAudioFile(file);
        if (!validation.valid()) {
            showWarning("파일 확인", validation.message());
            showStatus("File", "파일을 첨부하지 못했습니다.", "ERROR", StatusKind.ERROR);
            return;
        }

        attachAudioFile(file);
        showStatus("File", "파일 선택 완료", "SUCCESS", StatusKind.SUCCESS);
    }

    /**
     * Starts microphone recording to a temporary WAV file.
     */
    @FXML
    private void handleStartRecording() {
        if (audioRecordingService.isRecording()) {
            showWarning("녹음", "이미 녹음 중입니다.");
            return;
        }

        try {
            audioRecordingService.startRecording();
            recordingStartedAtMillis = System.currentTimeMillis();
            recordingPausedAtMillis = 0;
            totalPausedMillis = 0;
            startRecordingTimer();
            setRecordingControls(true);
            recordingStatusLabel.setText("녹음 상태: 녹음 중... 00:00");
            showStatus("Recording", "녹음 중... 중지하면 파일로 첨부됩니다.", "LOADING", StatusKind.LOADING);
        } catch (IllegalStateException exception) {
            setRecordingControls(false);
            showError("녹음 시작 실패", exception.getMessage());
        }
    }

    /**
     * Toggles microphone recording between paused and active states.
     */
    @FXML
    private void handlePauseRecording() {
        if (!audioRecordingService.isRecording()) {
            showWarning("녹음", "진행 중인 녹음이 없습니다.");
            return;
        }

        try {
            if (audioRecordingService.isPaused()) {
                totalPausedMillis += System.currentTimeMillis() - recordingPausedAtMillis;
                recordingPausedAtMillis = 0;
                audioRecordingService.resumeRecording();
                pauseRecordingButton.setText("일시정지");
                updateRecordingElapsed();
                showStatus("Recording", "녹음을 다시 시작했습니다.", "LOADING", StatusKind.LOADING);
            } else {
                recordingPausedAtMillis = System.currentTimeMillis();
                audioRecordingService.pauseRecording();
                pauseRecordingButton.setText("다시 시작");
                updateRecordingElapsed();
                showStatus("Recording", "녹음이 일시정지되었습니다.", "READY", StatusKind.NORMAL);
            }
        } catch (IllegalStateException exception) {
            showError("녹음 일시정지 실패", exception.getMessage());
        }
    }

    /**
     * Stops recording, validates the WAV file, and attaches it as the current audio.
     */
    @FXML
    private void handleStopRecording() {
        if (!audioRecordingService.isRecording()) {
            showWarning("녹음", "진행 중인 녹음이 없습니다.");
            return;
        }

        try {
            File recordedFile = audioRecordingService.stopRecording();
            stopRecordingTimer();
            setRecordingControls(false);
            pauseRecordingButton.setText("일시정지");

            ValidationResult validation = FileValidator.validateAudioFile(recordedFile);
            if (!validation.valid()) {
                recordingStatusLabel.setText("녹음 상태: 파일 확인 실패");
                showWarning("녹음 파일 확인", validation.message());
                showStatus("Recording", "녹음 파일을 첨부하지 못했습니다.", "ERROR", StatusKind.ERROR);
                return;
            }

            attachAudioFile(recordedFile);
            recordingStatusLabel.setText("녹음 상태: 완료 (" + fileSizeText(recordedFile) + ")");
            showStatus("Recording", "녹음 파일을 첨부했습니다.", "SUCCESS", StatusKind.SUCCESS);
        } catch (IllegalStateException exception) {
            stopRecordingTimer();
            setRecordingControls(false);
            pauseRecordingButton.setText("일시정지");
            showError("녹음 중지 실패", exception.getMessage());
        }
    }

    /**
     * Runs STT conversion on a background JavaFX Task and stores the transcript.
     */
    @FXML
    private void handleTranscribeAudioFile() {
        if (!ensureLectureSelected("텍스트 변환")) {
            return;
        }
        File audioFile = resolveAudioFile();
        if (audioFile == null) {
            showWarning("텍스트 변환", "먼저 녹음 파일을 선택해 주세요.");
            return;
        }
        ValidationResult validation = FileValidator.validateAudioFile(audioFile);
        if (!validation.valid()) {
            showWarning("파일 확인", validation.message());
            return;
        }

        setTranscriptionBusy(true);
        showStatus("STT", "텍스트 변환 중...", "LOADING", StatusKind.LOADING);
        Task<TranscriptionResult> task = new Task<>() {
            @Override
            protected TranscriptionResult call() {
                return audioTranscriptionService.transcribeWithResult(audioFile, buildTranscriptionOptions());
            }
        };
        task.setOnSucceeded(event -> {
            TranscriptionResult result = task.getValue();
            transcriptTextArea.setText(result.text());
            try {
                selectedLecture.setAudioPath(audioFile.getAbsolutePath());
                selectedLecture.setTranscript(result.text());
                lectureDAO.update(selectedLecture);
                transcriptStatusLabel.setText("원문 저장 상태: 저장됨");
                loadLecturesPreservingSelection();
                StatusKind kind = result.fallbackUsed() ? StatusKind.ERROR : StatusKind.SUCCESS;
                showStatus("STT", result.message(), result.fallbackUsed() ? "FALLBACK" : "SUCCESS", kind);
            } catch (IllegalStateException exception) {
                showError("원문 저장 실패", exception.getMessage());
            } finally {
                setTranscriptionBusy(false);
            }
        });
        task.setOnFailed(event -> {
            setTranscriptionBusy(false);
            showError("텍스트 변환 오류", exceptionMessage(task.getException()));
        });
        startDaemonTask(task, "lecturemate-transcription");
    }

    /**
     * Generates a summary on a background JavaFX Task and saves it into lectures.summary.
     */
    @FXML
    private void handleGenerateSummary() {
        if (!ensureLectureSelected("요약 생성")) {
            return;
        }
        String transcript = normalize(transcriptTextArea.getText());
        if (transcript.isBlank()) {
            showWarning("요약 생성", "원문 텍스트가 있어야 요약을 생성할 수 있습니다.");
            return;
        }

        String format = summaryFormatComboBox.getValue();
        setSummaryBusy(true);
        showStatus("Summary", "요약 생성 중...", "LOADING", StatusKind.LOADING);
        Task<SummaryResult> task = new Task<>() {
            @Override
            protected SummaryResult call() {
                return lectureSummaryService.generateSummaryWithResult(transcript, format);
            }
        };
        task.setOnSucceeded(event -> {
            SummaryResult result = task.getValue();
            replaceSummaryText(result.text());
            try {
                String styleJson = serializeEditorStyles(summaryTextArea);
                selectedLecture.setTranscript(transcript);
                selectedLecture.setSummary(result.text());
                selectedLecture.setSummaryStyle(styleJson);
                lectureDAO.update(selectedLecture);
                summaryStatusLabel.setText("정리 노트 상태: 저장됨");
                loadLecturesPreservingSelection();
                StatusKind kind = result.fallbackUsed() ? StatusKind.ERROR : StatusKind.SUCCESS;
                showStatus("Summary", result.message(), result.fallbackUsed() ? "FALLBACK" : "SUCCESS", kind);
            } catch (IllegalStateException exception) {
                showError("요약 저장 실패", exception.getMessage());
            } finally {
                setSummaryBusy(false);
            }
        });
        task.setOnFailed(event -> {
            setSummaryBusy(false);
            showError("요약 생성 오류", exceptionMessage(task.getException()));
        });
        startDaemonTask(task, "lecturemate-summary");
    }

    /**
     * Saves the edited transcript text to lectures.transcript.
     */
    @FXML
    private void handleSaveTranscript() {
        if (!ensureLectureSelected("원문 저장")) {
            return;
        }
        setSaveBusy(saveTranscriptButton, true, "원문 저장 중...");
        try {
            String text = transcriptTextArea.getText();
            lectureDAO.updateTranscript(selectedLecture.getId(), text);
            selectedLecture.setTranscript(text);
            transcriptStatusLabel.setText("원문 저장 상태: 저장됨");
            showStatus("Transcript", "원문 저장 완료", "SUCCESS", StatusKind.SUCCESS);
        } catch (IllegalStateException exception) {
            showError("원문 저장 실패", exception.getMessage());
        } finally {
            setSaveBusy(saveTranscriptButton, false, "원문 저장 상태: 대기");
        }
    }

    /**
     * Saves the edited final note text to lectures.summary.
     */
    @FXML
    private void handleSaveSummary() {
        if (!ensureLectureSelected("정리 노트 저장")) {
            return;
        }
        setSaveBusy(saveSummaryButton, true, "정리 노트 저장 중...");
        try {
            String text = getSummaryText();
            String styleJson = serializeEditorStyles(summaryTextArea);
            lectureDAO.updateSummary(selectedLecture.getId(), text, styleJson);
            selectedLecture.setSummary(text);
            selectedLecture.setSummaryStyle(styleJson);
            summaryStatusLabel.setText("정리 노트 상태: 저장됨");
            loadLecturesPreservingSelection();
            showStatus("Summary", "정리 노트 저장 완료", "SUCCESS", StatusKind.SUCCESS);
        } catch (IllegalStateException exception) {
            showError("정리 노트 저장 실패", exception.getMessage());
        } finally {
            setSaveBusy(saveSummaryButton, false, "정리 노트 상태: 대기");
        }
    }

    /**
     * Finds and selects the first matching keyword in the summary editor.
     */
    @FXML
    private void handleSearchSummary() {
        String keyword = normalize(summarySearchField.getText());
        String text = getSummaryText();
        if (keyword.isBlank()) {
            summaryStatusLabel.setText("검색어를 입력해 주세요.");
            showStatus("Search", "검색어를 입력해 주세요.", "READY", StatusKind.NORMAL);
            return;
        }
        int start = text.toLowerCase().indexOf(keyword.toLowerCase());
        if (start < 0) {
            summaryStatusLabel.setText("검색어를 찾을 수 없습니다.");
            showStatus("Search", "검색어를 찾을 수 없습니다.", "ERROR", StatusKind.ERROR);
            return;
        }
        summaryTextArea.requestFocus();
        summaryTextArea.selectRange(start, start + keyword.length());
        summaryStatusLabel.setText("검색어를 찾았습니다.");
        showStatus("Search", "검색어를 찾았습니다.", "SUCCESS", StatusKind.SUCCESS);
    }

    @FXML
    private void handleToggleLibraryEditorMode() {
        workspaceViewMode = WorkspaceViewMode.INTEGRATED;
        libraryEditorMode = libraryEditorMode == LibraryEditorMode.TRANSCRIPT
                ? LibraryEditorMode.SUMMARY
                : LibraryEditorMode.TRANSCRIPT;
        loadLibraryEditor();
        showLibraryWorkspace();
    }

    @FXML
    private void handleShowIntegratedWorkspace() {
        workspaceViewMode = WorkspaceViewMode.INTEGRATED;
        if (navTabPane.getSelectionModel().getSelectedItem() != lectureListTab) {
            navTabPane.getSelectionModel().select(lectureListTab);
        }
        showLibraryWorkspace();
        showStatus("View", "통합 필기 화면으로 전환했습니다.", "READY", StatusKind.NORMAL);
    }

    @FXML
    private void handleShowSplitWorkspace() {
        workspaceViewMode = WorkspaceViewMode.SPLIT;
        if (navTabPane.getSelectionModel().getSelectedItem() != lectureListTab) {
            navTabPane.getSelectionModel().select(lectureListTab);
        }
        showConversionWorkspace();
        showStatus("View", "분할 화면으로 전환했습니다.", "READY", StatusKind.NORMAL);
    }

    /**
     * Saves the large editor in the lecture-list workspace to transcript or summary.
     */
    @FXML
    private void handleSaveLibraryEditor() {
        if (!ensureLectureSelected("선택 내용 저장")) {
            return;
        }

        setSaveBusy(saveLibraryEditorButton, true, libraryEditorMode.label() + " 저장 중...");
        try {
            String text = getLibraryEditorText();
            if (libraryEditorMode == LibraryEditorMode.TRANSCRIPT) {
                lectureDAO.updateTranscript(selectedLecture.getId(), text);
                selectedLecture.setTranscript(text);
                transcriptTextArea.setText(text);
                transcriptStatusLabel.setText("원문 저장 상태: 저장됨");
            } else {
                String styleJson = serializeEditorStyles(libraryTextArea);
                lectureDAO.updateSummary(selectedLecture.getId(), text, styleJson);
                selectedLecture.setSummary(text);
                selectedLecture.setSummaryStyle(styleJson);
                replaceSummaryText(text, styleJson);
                summaryStatusLabel.setText("정리 노트 상태: 저장됨");
            }
            libraryEditorStatusLabel.setText("편집 상태: 저장됨");
            loadLecturesPreservingSelection();
            loadLibraryEditor();
            updateChatContextLabel();
            showStatus("Library", libraryEditorMode.label() + " 저장 완료", "SUCCESS", StatusKind.SUCCESS);
        } catch (IllegalStateException exception) {
            showError(libraryEditorMode.label() + " 저장 실패", exception.getMessage());
        } finally {
            setSaveBusy(saveLibraryEditorButton, false, "편집 상태: 대기");
        }
    }

    @FXML
    private void handleSearchLibraryEditor() {
        String keyword = normalize(librarySearchField.getText());
        String text = getLibraryEditorText();
        if (keyword.isBlank()) {
            libraryEditorStatusLabel.setText("편집 상태: 검색어를 입력해 주세요.");
            showStatus("Search", "검색어를 입력해 주세요.", "READY", StatusKind.NORMAL);
            return;
        }
        int start = text.toLowerCase().indexOf(keyword.toLowerCase());
        if (start < 0) {
            libraryEditorStatusLabel.setText("편집 상태: 검색어를 찾을 수 없습니다.");
            showStatus("Search", "검색어를 찾을 수 없습니다.", "ERROR", StatusKind.ERROR);
            return;
        }
        libraryTextArea.requestFocus();
        libraryTextArea.selectRange(start, start + keyword.length());
        libraryEditorStatusLabel.setText("편집 상태: 검색어를 찾았습니다.");
        showStatus("Search", "검색어를 찾았습니다.", "SUCCESS", StatusKind.SUCCESS);
    }

    /**
     * Sends a question to the AI assistant with the currently opened note as context.
     */
    @FXML
    private void handleAskQuestion() {
        String question = normalize(chatQuestionTextArea.getText());
        String context = getActiveNoteContextText();
        if (question.isBlank()) {
            showWarning("질문하기", "질문을 입력해 주세요.");
            return;
        }
        if (context.isBlank()) {
            showWarning("질문하기", "질문에 사용할 현재 노트 내용이 없습니다.");
            return;
        }

        appendChat("나", question);
        chatQuestionTextArea.clear();
        sendQuestionButton.setDisable(true);
        setProgressVisible(true);
        showStatus("AI", "노트를 기반으로 답변 중...", "LOADING", StatusKind.LOADING);

        Task<QuestionResult> task = new Task<>() {
            @Override
            protected QuestionResult call() {
                return lectureQuestionService.askWithResult(context, question);
            }
        };
        task.setOnSucceeded(event -> {
            QuestionResult result = task.getValue();
            appendChat("LectureMate AI", result.answer());
            sendQuestionButton.setDisable(false);
            StatusKind kind = result.fallbackUsed() ? StatusKind.ERROR : StatusKind.SUCCESS;
            showStatus("AI", result.message(), result.fallbackUsed() ? "FALLBACK" : "SUCCESS", kind);
        });
        task.setOnFailed(event -> {
            sendQuestionButton.setDisable(false);
            showError("AI 질문 오류", exceptionMessage(task.getException()));
        });
        startDaemonTask(task, "lecturemate-question");
    }

    @FXML
    private void handleClearChat() {
        chatMessagesBox.getChildren().clear();
        showStatus("AI", "대화 내용을 지웠습니다.", "READY", StatusKind.NORMAL);
    }

    /**
     * Plays the audio file attached to the currently selected lecture.
     */
    @FXML
    private void handlePlayLectureAudio() {
        if (!ensureLectureSelected("녹음 파일 듣기")) {
            return;
        }

        File audioFile = resolveLectureAudioFile();
        if (audioFile == null) {
            audioPlaybackStatusLabel.setText("재생 상태: 첨부된 녹음 파일 없음");
            showWarning("녹음 파일 듣기", "선택한 강의에 첨부된 녹음 파일이 없습니다.");
            return;
        }

        if (!Files.isRegularFile(audioFile.toPath())) {
            audioPlaybackStatusLabel.setText("재생 상태: 파일을 찾을 수 없음");
            showWarning("녹음 파일 듣기", "녹음 파일을 찾을 수 없습니다:\n" + audioFile.getAbsolutePath());
            return;
        }

        if (lectureAudioPlayer != null) {
            MediaPlayer.Status status = lectureAudioPlayer.getStatus();
            if (status == MediaPlayer.Status.PLAYING) {
                audioPlaybackStatusLabel.setText("재생 상태: 이미 재생 중");
                return;
            }
            if (status == MediaPlayer.Status.PAUSED || status == MediaPlayer.Status.READY || status == MediaPlayer.Status.STOPPED) {
                if (status == MediaPlayer.Status.STOPPED
                        && durationSeconds(lectureAudioPlayer.getCurrentTime()) >= durationSeconds(lectureAudioDuration)) {
                    lectureAudioPlayer.seek(Duration.ZERO);
                }
                lectureAudioPlayer.play();
                audioPlaybackSlider.setDisable(false);
                audioPlaybackStatusLabel.setText("재생 상태: 재생 중 - " + audioFile.getName());
                showStatus("Audio", "녹음 파일을 재생합니다.", "PLAYING", StatusKind.NORMAL);
                return;
            }
        }

        try {
            stopLectureAudio(false);
            Media media = new Media(audioFile.toURI().toString());
            lectureAudioPlayer = new MediaPlayer(media);
            lectureAudioPlayer.setOnReady(() -> {
                lectureAudioDuration = media.getDuration();
                audioPlaybackSlider.setDisable(false);
                updateAudioProgress(Duration.ZERO, lectureAudioDuration);
                audioPlaybackStatusLabel.setText("재생 상태: 재생 중 - " + audioFile.getName());
                showStatus("Audio", "녹음 파일을 재생합니다.", "PLAYING", StatusKind.NORMAL);
            });
            lectureAudioPlayer.currentTimeProperty()
                    .addListener((observable, oldTime, currentTime) -> {
                        if (!audioPlaybackSlider.isValueChanging()) {
                            updateAudioProgress(currentTime, lectureAudioDuration);
                        }
                    });
            lectureAudioPlayer.setOnEndOfMedia(() -> {
                audioPlaybackStatusLabel.setText("재생 상태: 재생 완료");
                updateAudioProgress(lectureAudioDuration, lectureAudioDuration);
            });
            lectureAudioPlayer.setOnError(() -> {
                MediaException error = lectureAudioPlayer.getError();
                stopLectureAudio(false);
                showError("녹음 재생 오류", error == null ? "녹음 파일을 재생할 수 없습니다." : error.getMessage());
            });
            lectureAudioPlayer.play();
        } catch (RuntimeException exception) {
            stopLectureAudio(false);
            showError("녹음 재생 오류", exceptionMessage(exception));
        }
    }

    @FXML
    private void handlePauseLectureAudio() {
        if (lectureAudioPlayer == null) {
            audioPlaybackStatusLabel.setText("재생 상태: 일시정지할 파일 없음");
            showStatus("Audio", "재생 중인 녹음 파일이 없습니다.", "READY", StatusKind.NORMAL);
            return;
        }

        if (lectureAudioPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            lectureAudioPlayer.pause();
            audioPlaybackStatusLabel.setText("재생 상태: 일시정지됨");
            showStatus("Audio", "녹음 파일 재생을 일시정지했습니다.", "PAUSED", StatusKind.NORMAL);
        }
    }

    @FXML
    private void handleStopLectureAudio() {
        stopLectureAudio(true);
    }

    @FXML
    private void handlePreviousMonth() {
        currentCalendarMonth = currentCalendarMonth.minusMonths(1);
        renderCalendar();
    }

    @FXML
    private void handleNextMonth() {
        currentCalendarMonth = currentCalendarMonth.plusMonths(1);
        renderCalendar();
    }

    /**
     * Clears the date filter and shows every incomplete schedule for the lecture.
     */
    @FXML
    private void handleShowAllSchedules() {
        selectedDate = null;
        refreshSchedules();
        renderCalendar();
        showStatus("Schedule", "전체 미완료 일정을 표시합니다.", "READY", StatusKind.NORMAL);
    }

    /**
     * Opens the schedule dialog and stores a new incomplete schedule.
     */
    @FXML
    private void handleAddSchedule() {
        if (!ensureLectureSelected("일정 추가")) {
            return;
        }
        Optional<ScheduleFormData> formData = showScheduleDialog();
        if (formData.isEmpty()) {
            return;
        }
        ScheduleFormData data = formData.get();
        Optional<LocalTime> time = parseTime(data.timeText());
        if (data.title().isBlank() || data.date() == null || time.isEmpty()) {
            showWarning("일정 추가", "제목, 날짜, 시간을 올바르게 입력해 주세요. 시간 형식은 HH:mm입니다.");
            return;
        }

        try {
            Schedule schedule = new Schedule(selectedLecture.getId(), data.title(), data.date(), time.get(), data.type());
            schedule.setDescription(data.description());
            scheduleDAO.insert(schedule);
            selectedDate = data.date();
            currentCalendarMonth = YearMonth.from(data.date());
            refreshCalendarAndSchedules();
            showStatus("Schedule", "일정 저장 완료", "SUCCESS", StatusKind.SUCCESS);
        } catch (IllegalStateException exception) {
            showError("일정 추가 실패", exception.getMessage());
        }
    }

    private void handleLectureSelectionFromList(Lecture lecture, boolean fromLectureList) {
        if (lecture == null || syncingSelection) {
            return;
        }
        libraryEditorMode = fromLectureList ? LibraryEditorMode.TRANSCRIPT : LibraryEditorMode.SUMMARY;
        selectLectureById(lecture.getId(), fromLectureList);
    }

    private void selectLectureById(long lectureId) {
        selectLectureById(lectureId, true);
    }

    private void selectLectureById(long lectureId, boolean fromLectureList) {
        try {
            Optional<Lecture> found = lectureDAO.findById(lectureId);
            if (found.isEmpty()) {
                showWarning("강의 선택", "선택한 강의를 찾을 수 없습니다.");
                return;
            }
            stopLectureAudio(false);
            selectedLecture = found.get();
            syncingSelection = true;
            Lecture observableLecture = findLectureInObservable(lectureId);
            lectureListView.getSelectionModel().select(observableLecture);
            summaryNoteListView.getSelectionModel().select(observableLecture);
            syncingSelection = false;
            loadLectureToWorkspace(selectedLecture);
            if (isLibraryTabSelected()) {
                libraryEditorMode = fromLectureList ? LibraryEditorMode.TRANSCRIPT : LibraryEditorMode.SUMMARY;
                showWorkspaceForCurrentMode();
            } else if (libraryWorkspace.isVisible()) {
                showLibraryWorkspace();
            }
            selectedDate = null;
            refreshCalendarAndSchedules();
            showStatus("Lecture", "강의를 불러왔습니다.", "SUCCESS", StatusKind.SUCCESS);
        } catch (IllegalStateException exception) {
            syncingSelection = false;
            showError("강의 선택 실패", exception.getMessage());
        }
    }

    private Lecture findLectureInObservable(long lectureId) {
        return lectures.stream().filter(lecture -> lecture.getId() == lectureId).findFirst().orElse(null);
    }

    private void loadLectureToWorkspace(Lecture lecture) {
        lectureTitleField.setText(defaultText(lecture.getTitle(), ""));
        lectureSubjectField.setText(defaultText(lecture.getSubject(), ""));
        currentLectureTitleLabel.setText(defaultText(lecture.getTitle(), "선택된 강의 없음"));
        currentLectureSubjectLabel.setText("과목명: " + defaultText(lecture.getSubject(), "-"));
        currentLectureCreatedAtLabel.setText("생성일: " + DateUtil.displayDateTime(lecture.getCreatedAt()));
        transcriptTextArea.setText(stripImageMarkers(defaultText(lecture.getTranscript(), "")));
        replaceSummaryText(stripImageMarkers(defaultText(lecture.getSummary(), "")), lecture.getSummaryStyle());
        transcriptStatusLabel.setText("원문 저장 상태: 로드됨");
        summaryStatusLabel.setText("정리 노트 상태: 로드됨");
        selectedAudioFile = lecture.getAudioPath() == null || lecture.getAudioPath().isBlank()
                ? null
                : new File(lecture.getAudioPath());
        selectedFileNameLabel.setText(selectedAudioFile == null ? "선택된 파일 없음" : selectedAudioFile.getName());
        selectedFilePathField.setText(selectedAudioFile == null ? "" : selectedAudioFile.getAbsolutePath());
        updateAudioPlaybackStatus();
        loadLibraryEditor();
        updateChatContextLabel();
    }

    private void loadLectures() {
        try {
            lectures.setAll(lectureDAO.findAll());
        } catch (IllegalStateException exception) {
            showError("강의 목록 오류", exception.getMessage());
        }
    }

    private void loadLecturesPreservingSelection() {
        long id = selectedLecture == null ? -1 : selectedLecture.getId();
        loadLectures();
        if (id > 0) {
            selectLectureById(id);
        }
    }

    /**
     * Rebuilds the month grid and marks days that have incomplete schedules.
     */
    private void renderCalendar() {
        calendarGrid.getChildren().clear();
        calendarMonthLabel.setText(currentCalendarMonth.getYear() + ". "
                + String.format("%02d", currentCalendarMonth.getMonthValue()));

        List<String> headers = List.of("월", "화", "수", "목", "금", "토", "일");
        for (int column = 0; column < headers.size(); column++) {
            Label label = new Label(headers.get(column));
            label.getStyleClass().add("sub-label");
            calendarGrid.add(label, column, 0);
        }

        Set<LocalDate> scheduleDates = selectedLecture == null
                ? Set.of()
                : new HashSet<>(scheduleDAO.findScheduleDatesByLectureId(selectedLecture.getId()));
        LocalDate firstDay = currentCalendarMonth.atDay(1);
        int startColumn = firstDay.getDayOfWeek().getValue() - 1;
        int days = currentCalendarMonth.lengthOfMonth();
        for (int day = 1; day <= days; day++) {
            LocalDate date = currentCalendarMonth.atDay(day);
            Button button = new Button(String.valueOf(day));
            button.getStyleClass().add("calendar-day");
            if (scheduleDates.contains(date)) {
                button.getStyleClass().add("calendar-day-has-schedule");
            }
            if (date.equals(selectedDate)) {
                button.getStyleClass().add("calendar-day-selected");
            }
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> {
                selectedDate = date;
                refreshSchedules();
                renderCalendar();
            });
            int index = startColumn + day - 1;
            calendarGrid.add(button, index % 7, index / 7 + 1);
        }
    }

    private void refreshSchedules() {
        if (selectedLecture == null) {
            schedules.clear();
            scheduleFilterLabel.setText("미완료 일정");
            return;
        }
        try {
            if (selectedDate == null) {
                schedules.setAll(scheduleDAO.findIncompleteByLectureId(selectedLecture.getId()));
                scheduleFilterLabel.setText("미완료 일정");
            } else {
                schedules.setAll(scheduleDAO.findIncompleteByLectureIdAndDate(selectedLecture.getId(), selectedDate));
                scheduleFilterLabel.setText(DateUtil.displayDate(selectedDate) + " 미완료 일정");
            }
        } catch (IllegalStateException exception) {
            schedules.clear();
            showError("일정 조회 실패", exception.getMessage());
        }
    }

    private void refreshCalendarAndSchedules() {
        refreshSchedules();
        renderCalendar();
    }

    private Optional<ScheduleFormData> showScheduleDialog() {
        Dialog<ScheduleFormData> dialog = new Dialog<>();
        dialog.setTitle("일정 추가");
        dialog.setHeaderText(null);
        if (addScheduleButton.getScene() != null) {
            dialog.initOwner(addScheduleButton.getScene().getWindow());
        }
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("schedule-dialog");
        ButtonType saveButtonType = new ButtonType("저장", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField titleField = new TextField();
        titleField.setPromptText("일정 제목");
        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("설명");
        descriptionArea.setPrefRowCount(3);
        DatePicker datePicker = new DatePicker(selectedDate == null ? LocalDate.now() : selectedDate);
        TextField timeField = new TextField("09:00");
        ComboBox<String> typeComboBox = new ComboBox<>(FXCollections.observableArrayList(SCHEDULE_TYPES));
        typeComboBox.getSelectionModel().selectFirst();

        Label titleLabel = new Label("일정 추가");
        titleLabel.getStyleClass().add("section-title");
        Label guideLabel = new Label("현재 선택한 강의와 연결할 일정을 입력하세요.");
        guideLabel.getStyleClass().add("sub-label");
        guideLabel.setWrapText(true);

        GridPane form = new GridPane();
        form.getStyleClass().add("schedule-form");
        form.setHgap(12);
        form.setVgap(10);
        form.add(formLabel("제목"), 0, 0);
        form.add(titleField, 1, 0);
        form.add(formLabel("설명"), 0, 1);
        form.add(descriptionArea, 1, 1);
        form.add(formLabel("날짜"), 0, 2);
        form.add(datePicker, 1, 2);
        form.add(formLabel("시간"), 0, 3);
        form.add(timeField, 1, 3);
        form.add(formLabel("유형"), 0, 4);
        form.add(typeComboBox, 1, 4);
        titleField.setMaxWidth(Double.MAX_VALUE);
        descriptionArea.setMaxWidth(Double.MAX_VALUE);
        datePicker.setMaxWidth(Double.MAX_VALUE);
        timeField.setMaxWidth(Double.MAX_VALUE);
        typeComboBox.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(descriptionArea, Priority.ALWAYS);
        GridPane.setHgrow(datePicker, Priority.ALWAYS);
        GridPane.setHgrow(timeField, Priority.ALWAYS);
        GridPane.setHgrow(typeComboBox, Priority.ALWAYS);

        VBox content = new VBox(10, titleLabel, guideLabel, form);
        content.getStyleClass().add("schedule-dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(420);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> normalize(titleField.getText()).isBlank(),
                titleField.textProperty()));
        saveButton.getStyleClass().add("primary-button");
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.getStyleClass().add("secondary-button");
        dialog.setResultConverter(button -> {
            if (button != saveButtonType) {
                return null;
            }
            return new ScheduleFormData(
                    normalize(titleField.getText()),
                    normalize(descriptionArea.getText()),
                    datePicker.getValue(),
                    normalize(timeField.getText()),
                    defaultText(typeComboBox.getValue(), "Other"));
        });
        return dialog.showAndWait();
    }

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sub-label");
        label.setMinWidth(52);
        return label;
    }

    private void completeSchedule(Schedule schedule) {
        try {
            scheduleDAO.updateIsDone(schedule.getId(), true);
            refreshCalendarAndSchedules();
            showStatus("Schedule", "일정을 완료 처리했습니다.", "SUCCESS", StatusKind.SUCCESS);
        } catch (IllegalStateException exception) {
            showError("일정 완료 실패", exception.getMessage());
        }
    }

    private void deleteSchedule(Schedule schedule) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("일정 삭제");
        confirm.setHeaderText("선택한 일정을 삭제할까요?");
        confirm.setContentText(schedule.getTitle());
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        try {
            scheduleDAO.delete(schedule.getId());
            refreshCalendarAndSchedules();
            showStatus("Schedule", "일정을 삭제했습니다.", "SUCCESS", StatusKind.SUCCESS);
        } catch (IllegalStateException exception) {
            showError("일정 삭제 실패", exception.getMessage());
        }
    }

    private TranscriptionOptions buildTranscriptionOptions() {
        LanguageOption language = audioLanguageComboBox.getValue();
        return new TranscriptionOptions(
                language == null ? "" : language.code(),
                sttModelComboBox.getValue());
    }

    private File resolveAudioFile() {
        if (selectedAudioFile != null) {
            return selectedAudioFile;
        }
        if (selectedLecture != null && selectedLecture.getAudioPath() != null && !selectedLecture.getAudioPath().isBlank()) {
            return new File(selectedLecture.getAudioPath());
        }
        return null;
    }

    private File resolveLectureAudioFile() {
        if (selectedLecture == null || selectedLecture.getAudioPath() == null || selectedLecture.getAudioPath().isBlank()) {
            return null;
        }
        return new File(selectedLecture.getAudioPath());
    }

    private void attachAudioFile(File file) {
        selectedAudioFile = file;
        selectedFileNameLabel.setText(file.getName() + " (" + fileSizeText(file) + ")");
        selectedFilePathField.setText(file.getAbsolutePath());
        if (selectedLecture != null) {
            selectedLecture.setAudioPath(file.getAbsolutePath());
            lectureDAO.update(selectedLecture);
        }
        updateAudioPlaybackStatus();
    }

    private void stopLectureAudio(boolean showMessage) {
        if (lectureAudioPlayer != null) {
            lectureAudioPlayer.stop();
            lectureAudioPlayer.dispose();
            lectureAudioPlayer = null;
        }
        lectureAudioDuration = Duration.ZERO;
        resetAudioPlaybackControls(true);
        if (showMessage) {
            audioPlaybackStatusLabel.setText("재생 상태: 정지됨");
            showStatus("Audio", "녹음 파일 재생을 정지했습니다.", "READY", StatusKind.NORMAL);
        }
    }

    private void updateAudioPlaybackStatus() {
        if (selectedLecture == null) {
            resetAudioPlaybackControls(true);
            audioPlaybackStatusLabel.setText("재생 상태: 선택된 강의 없음");
            return;
        }

        File audioFile = resolveLectureAudioFile();
        if (audioFile == null) {
            resetAudioPlaybackControls(true);
            audioPlaybackStatusLabel.setText("재생 상태: 첨부된 녹음 파일 없음");
        } else if (Files.isRegularFile(audioFile.toPath())) {
            resetAudioPlaybackControls(false);
            audioPlaybackStatusLabel.setText("재생 상태: 재생 가능");
        } else {
            resetAudioPlaybackControls(true);
            audioPlaybackStatusLabel.setText("재생 상태: 파일을 찾을 수 없음");
        }
    }

    private void updateAudioProgress(Duration currentTime, Duration totalTime) {
        double totalSeconds = durationSeconds(totalTime);
        double currentSeconds = Math.min(durationSeconds(currentTime), totalSeconds <= 0 ? durationSeconds(currentTime) : totalSeconds);
        updatingAudioSlider = true;
        audioPlaybackSlider.setMax(totalSeconds <= 0 ? 1 : totalSeconds);
        audioPlaybackSlider.setValue(currentSeconds);
        updatingAudioSlider = false;
        updateAudioTime(Duration.seconds(currentSeconds), totalTime);
    }

    private void updateAudioTime(Duration currentTime, Duration totalTime) {
        audioPlaybackTimeLabel.setText(formatDuration(currentTime) + " / " + formatDuration(totalTime));
    }

    private void resetAudioPlaybackControls(boolean disabled) {
        audioPlaybackSlider.setDisable(disabled);
        updateAudioProgress(Duration.ZERO, Duration.ZERO);
    }

    private void seekLectureAudio() {
        if (lectureAudioPlayer == null || updatingAudioSlider) {
            return;
        }
        double seconds = Math.max(0, audioPlaybackSlider.getValue());
        lectureAudioPlayer.seek(Duration.seconds(seconds));
        updateAudioTime(Duration.seconds(seconds), lectureAudioDuration);
    }

    private double durationSeconds(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.isIndefinite()) {
            return 0;
        }
        return Math.max(0, duration.toSeconds());
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = Math.round(durationSeconds(duration));
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private boolean ensureLectureSelected(String actionName) {
        if (selectedLecture != null) {
            return true;
        }
        showWarning(actionName, "먼저 강의를 생성하거나 선택해 주세요.");
        return false;
    }

    private void clearLectureWorkspace() {
        currentLectureTitleLabel.setText("선택된 강의 없음");
        currentLectureSubjectLabel.setText("과목명: -");
        currentLectureCreatedAtLabel.setText("생성일: -");
        transcriptTextArea.clear();
        replaceSummaryText("");
        lectureTitleField.clear();
        lectureSubjectField.clear();
        selectedFileNameLabel.setText("선택된 파일 없음");
        selectedFilePathField.clear();
        recordingStatusLabel.setText("녹음 상태: 대기");
        resetAudioPlaybackControls(true);
        audioPlaybackStatusLabel.setText("재생 상태: 선택된 강의 없음");
        transcriptStatusLabel.setText("원문 저장 상태: 대기");
        summaryStatusLabel.setText("정리 노트 상태: 대기");
        scheduleFilterLabel.setText("미완료 일정");
        libraryLectureTitleLabel.setText("선택된 강의 없음");
        libraryLectureMetaLabel.setText("과목명: -");
        libraryModeLabel.setText(libraryEditorMode.label());
        replaceLibraryText("");
        libraryEditorStatusLabel.setText("편집 상태: 대기");
        updateChatContextLabel();
    }

    private void showConversionWorkspace() {
        setWorkspaceContent(conversionWorkspace);
        updateChatContextLabel();
    }

    private void showLibraryWorkspace() {
        setWorkspaceContent(libraryWorkspace);
        loadLibraryEditor();
        updateChatContextLabel();
    }

    private void showWorkspaceForCurrentMode() {
        if (workspaceViewMode == WorkspaceViewMode.SPLIT) {
            showConversionWorkspace();
        } else {
            showLibraryWorkspace();
        }
    }

    private void setWorkspaceContent(Node content) {
        long switchVersion = ++workspaceSwitchVersion;
        applyWorkspaceContent(content);
        Platform.runLater(() -> {
            if (switchVersion == workspaceSwitchVersion) {
                applyWorkspaceContent(content);
            }
        });
    }

    private void applyWorkspaceContent(Node content) {
        boolean showLibrary = content == libraryWorkspace;
        ensureWorkspaceChildren();
        conversionWorkspace.setVisible(!showLibrary);
        conversionWorkspace.setManaged(!showLibrary);
        libraryWorkspace.setVisible(showLibrary);
        libraryWorkspace.setManaged(showLibrary);
        content.setVisible(true);
        content.setManaged(true);
        content.toFront();
        workspaceStack.requestLayout();
    }

    private void ensureWorkspaceChildren() {
        if (!workspaceStack.getChildren().contains(conversionWorkspace)) {
            workspaceStack.getChildren().add(0, conversionWorkspace);
        }
        if (!workspaceStack.getChildren().contains(libraryWorkspace)) {
            workspaceStack.getChildren().add(libraryWorkspace);
        }
    }

    private boolean isLibraryTabSelected() {
        return navTabPane.getSelectionModel().getSelectedItem() == lectureListTab;
    }

    private void loadLibraryEditor() {
        updateLibraryModeControls();
        if (selectedLecture == null) {
            libraryLectureTitleLabel.setText("선택된 강의 없음");
            libraryLectureMetaLabel.setText("과목명: -");
            replaceLibraryText("");
            libraryEditorStatusLabel.setText("편집 상태: 대기");
            return;
        }

        libraryLectureTitleLabel.setText(defaultText(selectedLecture.getTitle(), "선택된 강의 없음"));
        libraryLectureMetaLabel.setText("과목명: " + defaultText(selectedLecture.getSubject(), "-")
                + " | 생성일: " + DateUtil.displayDateTime(selectedLecture.getCreatedAt()));
        String storedText = libraryEditorMode == LibraryEditorMode.TRANSCRIPT
                ? defaultText(selectedLecture.getTranscript(), "")
                : defaultText(selectedLecture.getSummary(), "");
        replaceLibraryText(
                stripImageMarkers(storedText),
                libraryEditorMode == LibraryEditorMode.SUMMARY ? selectedLecture.getSummaryStyle() : null);
        libraryEditorStatusLabel.setText("편집 상태: " + libraryEditorMode.label() + " 로드됨");
        updateChatContextLabel();
    }

    private void updateLibraryModeControls() {
        libraryModeLabel.setText(libraryEditorMode.label());
        toggleLibraryModeButton.setText(libraryEditorMode == LibraryEditorMode.TRANSCRIPT
                ? "정리 노트 보기"
                : "원문 보기");
    }

    private String getActiveNoteContextText() {
        if (libraryWorkspace.isVisible() && libraryTextArea != null) {
            String libraryText = normalize(getLibraryEditorText());
            if (!libraryText.isBlank()) {
                return buildContextWithLectureMeta(libraryEditorMode.label(), libraryText);
            }
        }

        String summary = normalize(getSummaryText());
        if (!summary.isBlank()) {
            return buildContextWithLectureMeta("정리 노트", summary);
        }

        String transcript = normalize(transcriptTextArea.getText());
        if (!transcript.isBlank()) {
            return buildContextWithLectureMeta("원문 텍스트", transcript);
        }

        if (selectedLecture != null) {
            String storedSummary = normalize(stripImageMarkers(selectedLecture.getSummary()));
            if (!storedSummary.isBlank()) {
                return buildContextWithLectureMeta("정리 노트", storedSummary);
            }
            String storedTranscript = normalize(stripImageMarkers(selectedLecture.getTranscript()));
            if (!storedTranscript.isBlank()) {
                return buildContextWithLectureMeta("원문 텍스트", storedTranscript);
            }
        }
        return "";
    }

    private String buildContextWithLectureMeta(String source, String text) {
        if (selectedLecture == null) {
            return text;
        }
        return """
                강의 제목: %s
                과목명: %s
                내용 종류: %s

                %s
                """.formatted(
                defaultText(selectedLecture.getTitle(), "-"),
                defaultText(selectedLecture.getSubject(), "-"),
                source,
                text);
    }

    private void updateChatContextLabel() {
        if (chatContextLabel == null) {
            return;
        }
        if (selectedLecture == null) {
            chatContextLabel.setText("현재 노트: -");
            return;
        }
        String source = libraryWorkspace != null && libraryWorkspace.isVisible()
                ? libraryEditorMode.label()
                : normalize(getSummaryText()).isBlank() ? "원문 텍스트" : "정리 노트";
        int length = getActiveNoteContextText().length();
        chatContextLabel.setText("현재 노트: " + selectedLecture.getTitle() + " / " + source + " / " + length + "자");
    }

    private void appendChat(String speaker, String text) {
        boolean userMessage = "나".equals(speaker);
        HBox row = new HBox();
        row.setAlignment(userMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.prefWidthProperty().bind(chatMessagesBox.widthProperty());
        row.getStyleClass().addAll("chat-row", userMessage ? "chat-row-user" : "chat-row-ai");

        VBox bubble = new VBox(4);
        bubble.maxWidthProperty().bind(chatMessagesBox.widthProperty().multiply(0.78));
        bubble.getStyleClass().addAll("chat-bubble", userMessage ? "chat-bubble-user" : "chat-bubble-ai");

        Label speakerLabel = new Label(userMessage ? "나" : "LectureMate AI");
        speakerLabel.getStyleClass().add("chat-speaker");

        Label messageLabel = new Label(defaultText(text, ""));
        messageLabel.setWrapText(true);
        messageLabel.maxWidthProperty().bind(bubble.maxWidthProperty().subtract(22));
        messageLabel.getStyleClass().add("chat-message-text");

        bubble.getChildren().addAll(speakerLabel, messageLabel);
        row.getChildren().add(bubble);
        chatMessagesBox.getChildren().add(row);
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    private String stripImageMarkers(String storedText) {
        if (storedText == null || storedText.isBlank()) {
            return "";
        }
        return storedText.lines()
                .filter(line -> {
                    String trimmed = line.trim();
                    return !(trimmed.startsWith(IMAGE_MARKER_PREFIX) && trimmed.endsWith(IMAGE_MARKER_SUFFIX));
                })
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("")
                .stripTrailing();
    }

    private static String stripImageMarkersForPreview(String storedText) {
        if (storedText == null || storedText.isBlank()) {
            return "";
        }
        return storedText.lines()
                .filter(line -> {
                    String trimmed = line.trim();
                    return !(trimmed.startsWith(IMAGE_MARKER_PREFIX) && trimmed.endsWith(IMAGE_MARKER_SUFFIX));
                })
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("")
                .stripTrailing();
    }

    private String getSummaryText() {
        return summaryTextArea == null || summaryTextArea.getText() == null ? "" : summaryTextArea.getText();
    }

    private String getLibraryEditorText() {
        return libraryTextArea == null || libraryTextArea.getText() == null ? "" : libraryTextArea.getText();
    }

    private void replaceSummaryText(String text) {
        replaceSummaryText(text, null);
    }

    private void replaceSummaryText(String text, String styleJson) {
        if (summaryTextArea == null) {
            return;
        }
        replaceStyledText(summaryTextArea, text, styleJson);
    }

    private void replaceLibraryText(String text) {
        replaceLibraryText(text, null);
    }

    private void replaceLibraryText(String text, String styleJson) {
        if (libraryTextArea == null) {
            return;
        }
        replaceStyledText(libraryTextArea, text, styleJson);
    }

    private void replaceStyledText(InlineCssTextArea editor, String text, String styleJson) {
        String safeText = defaultText(text, "");
        editor.replaceText(safeText);
        if (safeText.isBlank()) {
            return;
        }
        if (!applyStoredEditorStyles(editor, safeText.length(), styleJson)) {
            editor.setStyle(0, safeText.length(), buildInlineStyle(DEFAULT_NOTE_FONT_SIZE, DEFAULT_NOTE_FONT));
        }
    }

    private String serializeEditorStyles(InlineCssTextArea editor) {
        if (editor == null || editor.getText() == null || editor.getText().isBlank()) {
            return "";
        }
        int textLength = editor.getText().length();
        StyleSpans<String> spans = editor.getStyleSpans(0, textLength);
        List<StoredStyleRange> ranges = new ArrayList<>();
        int start = 0;
        for (StyleSpan<String> span : spans) {
            int length = Math.min(span.getLength(), textLength - start);
            if (length > 0) {
                String style = defaultText(span.getStyle(), buildInlineStyle(DEFAULT_NOTE_FONT_SIZE, DEFAULT_NOTE_FONT));
                ranges.add(new StoredStyleRange(start, length, style));
            }
            start += span.getLength();
            if (start >= textLength) {
                break;
            }
        }
        try {
            return JSON_MAPPER.writeValueAsString(ranges);
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    private boolean applyStoredEditorStyles(InlineCssTextArea editor, int textLength, String styleJson) {
        if (styleJson == null || styleJson.isBlank() || textLength <= 0) {
            return false;
        }
        try {
            List<StoredStyleRange> ranges = JSON_MAPPER.readValue(styleJson, STYLE_RANGE_LIST_TYPE);
            if (ranges.isEmpty()) {
                return false;
            }
            ranges.sort((left, right) -> Integer.compare(left.start(), right.start()));
            StyleSpansBuilder<String> builder = new StyleSpansBuilder<>();
            String defaultStyle = buildInlineStyle(DEFAULT_NOTE_FONT_SIZE, DEFAULT_NOTE_FONT);
            int cursor = 0;
            for (StoredStyleRange range : ranges) {
                int start = Math.max(0, Math.min(range.start(), textLength));
                int length = Math.max(0, Math.min(range.length(), textLength - start));
                if (length == 0 || start < cursor) {
                    continue;
                }
                if (start > cursor) {
                    builder.add(defaultStyle, start - cursor);
                }
                builder.add(defaultText(range.style(), defaultStyle), length);
                cursor = start + length;
                if (cursor >= textLength) {
                    break;
                }
            }
            if (cursor < textLength) {
                builder.add(defaultStyle, textLength - cursor);
            }
            editor.setStyleSpans(0, builder.create());
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static String buildInlineStyle(int size, String font) {
        String safeFont = font == null || font.isBlank() ? DEFAULT_NOTE_FONT : font.replace("'", "");
        return "-fx-font-size: " + size + "px; -fx-font-family: '" + safeFont + "';";
    }

    private void applySummaryTextStyle() {
        Integer size = noteFontSizeComboBox.getValue();
        String font = noteFontComboBox.getValue();
        currentSummaryInputStyle = buildInlineStyle(
                size == null ? DEFAULT_NOTE_FONT_SIZE : size,
                defaultText(font, DEFAULT_NOTE_FONT));
        if (summaryTextArea == null) {
            return;
        }
        IndexRange selection = summaryTextArea.getSelection();
        if (selection != null && selection.getLength() > 0) {
            summaryTextArea.setStyle(selection.getStart(), selection.getEnd(), currentSummaryInputStyle);
            summaryStatusLabel.setText("정리 노트 상태: 선택 영역 서식 적용");
        } else {
            summaryStatusLabel.setText("정리 노트 상태: 다음 입력부터 서식 적용");
        }
        summaryTextArea.requestFocus();
    }

    private void applyLibraryEditorStyle() {
        Integer size = libraryFontSizeComboBox.getValue();
        String font = libraryFontComboBox.getValue();
        currentLibraryInputStyle = buildInlineStyle(
                size == null ? DEFAULT_NOTE_FONT_SIZE : size,
                defaultText(font, DEFAULT_NOTE_FONT));
        if (libraryTextArea == null) {
            return;
        }
        IndexRange selection = libraryTextArea.getSelection();
        if (selection != null && selection.getLength() > 0) {
            libraryTextArea.setStyle(selection.getStart(), selection.getEnd(), currentLibraryInputStyle);
            libraryEditorStatusLabel.setText("편집 상태: 선택 영역 서식 적용");
        } else {
            libraryEditorStatusLabel.setText("편집 상태: 다음 입력부터 서식 적용");
        }
        libraryTextArea.requestFocus();
    }

    private void setTranscriptionBusy(boolean busy) {
        setProgressVisible(busy);
        chooseFileButton.setDisable(busy);
        startRecordingButton.setDisable(busy || audioRecordingService.isRecording());
        pauseRecordingButton.setDisable(busy || !audioRecordingService.isRecording());
        stopRecordingButton.setDisable(busy || !audioRecordingService.isRecording());
        transcribeButton.setDisable(busy);
        summaryButton.setDisable(busy);
    }

    private void setSummaryBusy(boolean busy) {
        setProgressVisible(busy);
        summaryButton.setDisable(busy);
        saveSummaryButton.setDisable(busy);
    }

    private void setSaveBusy(Button button, boolean busy, String message) {
        setProgressVisible(busy);
        button.setDisable(busy);
        showStatus("Save", message, busy ? "LOADING" : "READY", busy ? StatusKind.LOADING : StatusKind.NORMAL);
    }

    private void setRecordingControls(boolean recording) {
        chooseFileButton.setDisable(recording);
        startRecordingButton.setDisable(recording);
        pauseRecordingButton.setDisable(!recording);
        stopRecordingButton.setDisable(!recording);
        transcribeButton.setDisable(recording);
        summaryButton.setDisable(recording);
        setProgressVisible(recording);
        if (!recording) {
            pauseRecordingButton.setText("일시정지");
        }
    }

    private void startRecordingTimer() {
        stopRecordingTimer();
        recordingTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateRecordingElapsed()));
        recordingTimer.setCycleCount(Timeline.INDEFINITE);
        recordingTimer.play();
    }

    private void stopRecordingTimer() {
        if (recordingTimer != null) {
            recordingTimer.stop();
            recordingTimer = null;
        }
    }

    private void updateRecordingElapsed() {
        long now = System.currentTimeMillis();
        long activePausedMillis = audioRecordingService.isPaused() && recordingPausedAtMillis > 0
                ? now - recordingPausedAtMillis
                : 0;
        long elapsedSeconds = Math.max(0,
                (now - recordingStartedAtMillis - totalPausedMillis - activePausedMillis) / 1000);
        long minutes = elapsedSeconds / 60;
        long seconds = elapsedSeconds % 60;
        String state = audioRecordingService.isPaused() ? "일시정지" : "녹음 중";
        recordingStatusLabel.setText(String.format("녹음 상태: %s... %02d:%02d", state, minutes, seconds));
    }

    private void setProgressVisible(boolean visible) {
        globalProgressIndicator.setVisible(visible);
        globalProgressIndicator.setManaged(visible);
    }

    private void showStatus(String task, String message, String result, StatusKind kind) {
        taskNameLabel.setText(task);
        statusMessageLabel.setText(message);
        statusResultLabel.setText(result);
        animateStatusBanner(task, message, result);
        statusBanner.getStyleClass().removeAll("status-loading", "status-success", "status-error");
        if (kind == StatusKind.LOADING) {
            statusBanner.getStyleClass().add("status-loading");
        } else if (kind == StatusKind.SUCCESS) {
            statusBanner.getStyleClass().add("status-success");
        } else if (kind == StatusKind.ERROR) {
            statusBanner.getStyleClass().add("status-error");
        }
        setProgressVisible(kind == StatusKind.LOADING);
    }

    /**
     * Expands the header status box when a message needs more room, then shrinks
     * it back for short messages.
     */
    private void animateStatusBanner(String task, String message, String result) {
        String visibleText = defaultText(task, "") + " " + defaultText(message, "") + " " + defaultText(result, "");
        double desiredWidth = estimateStatusTextWidth(visibleText) + STATUS_TEXT_CHROME_WIDTH;
        double targetWidth = Math.max(STATUS_COMPACT_WIDTH, Math.min(STATUS_EXPANDED_WIDTH, desiredWidth));
        boolean expanded = desiredWidth > STATUS_EXPANDED_WIDTH;

        statusMessageLabel.setWrapText(expanded);
        statusMessageLabel.setMaxWidth(Math.max(110, targetWidth - 132));

        if (statusResizeAnimation != null) {
            statusResizeAnimation.stop();
        }
        statusResizeAnimation = new Timeline(new KeyFrame(
                Duration.millis(220),
                new KeyValue(statusBanner.minWidthProperty(), targetWidth),
                new KeyValue(statusBanner.prefWidthProperty(), targetWidth),
                new KeyValue(statusBanner.maxWidthProperty(), targetWidth)));
        statusResizeAnimation.play();
    }

    private double estimateStatusTextWidth(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        double width = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (Character.isWhitespace(codePoint)) {
                width += 4;
            } else if (codePoint < 128) {
                width += 6.5;
            } else {
                width += 11.5;
            }
            index += Character.charCount(codePoint);
        }
        return width;
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null || message.isBlank() ? "알 수 없는 오류가 발생했습니다." : message);
        alert.showAndWait();
        showStatus(title, alert.getContentText(), "ERROR", StatusKind.ERROR);
    }

    private void startDaemonTask(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private Optional<LocalTime> parseTime(String value) {
        try {
            return Optional.of(LocalTime.parse(value));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String fileSizeText(File file) {
        try {
            return FileValidator.formatFileSize(Files.size(file.toPath()));
        } catch (IOException exception) {
            return "size unavailable";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String exceptionMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? "알 수 없는 오류가 발생했습니다."
                : throwable.getMessage();
    }

    private enum StatusKind {
        NORMAL,
        LOADING,
        SUCCESS,
        ERROR
    }

    private enum LibraryEditorMode {
        TRANSCRIPT("원문 텍스트"),
        SUMMARY("정리 노트");

        private final String label;

        LibraryEditorMode(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private enum WorkspaceViewMode {
        SPLIT,
        INTEGRATED
    }

    private record StoredStyleRange(int start, int length, String style) {
    }

    private record LectureEditData(String title, String subject) {
    }

    private record LanguageOption(String label, String code) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record ScheduleFormData(String title, String description, LocalDate date, String timeText, String type) {
    }

    private static final class LectureCell extends ListCell<Lecture> {
        private final boolean summaryPreview;

        private LectureCell(boolean summaryPreview) {
            this.summaryPreview = summaryPreview;
            setWrapText(true);
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(Lecture lecture, boolean empty) {
            super.updateItem(lecture, empty);
            if (empty || lecture == null) {
                setText(null);
                return;
            }
            String date = lecture.getCreatedAt() == null ? "-" : DateUtil.displayDateTime(lecture.getCreatedAt());
            if (summaryPreview) {
                String cleanSummary = stripImageMarkersForPreview(lecture.getSummary());
                String summary = cleanSummary.isBlank()
                        ? "저장된 정리 노트 없음"
                        : cleanSummary.replaceAll("\\R+", " ");
                if (summary.length() > 60) {
                    summary = summary.substring(0, 60) + "...";
                }
                setText(lecture.getTitle() + System.lineSeparator() + summary);
            } else {
                String cleanTranscript = stripImageMarkersForPreview(lecture.getTranscript());
                String hint = cleanTranscript.isBlank() ? "" : System.lineSeparator() + cleanTranscript.replaceAll("\\R+", " ");
                if (hint.length() > 60) {
                    hint = hint.substring(0, 60) + "...";
                }
                setText(lecture.getTitle() + System.lineSeparator() + lecture.getSubject() + " | " + date + hint);
            }
        }
    }

    private final class ScheduleCardCell extends ListCell<Schedule> {
        @Override
        protected void updateItem(Schedule schedule, boolean empty) {
            super.updateItem(schedule, empty);
            if (empty || schedule == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            CheckBox doneBox = new CheckBox();
            doneBox.setOnAction(event -> completeSchedule(schedule));

            Label title = new Label(schedule.getTitle());
            title.getStyleClass().add("section-title");
            title.setWrapText(true);
            Label meta = new Label(DateUtil.displayDate(schedule.getScheduleDate()) + " "
                    + DateUtil.displayTime(schedule.getScheduleTime()) + " | " + schedule.getType());
            meta.getStyleClass().add("sub-label");
            meta.setWrapText(true);
            Label description = new Label(defaultText(schedule.getDescription(), ""));
            description.getStyleClass().add("sub-label");
            description.setWrapText(true);

            VBox textBox = new VBox(4, title, meta, description);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            Button deleteButton = new Button("삭제");
            deleteButton.getStyleClass().add("danger-button");
            deleteButton.setMinWidth(52);
            deleteButton.setOnAction(event -> deleteSchedule(schedule));

            HBox row = new HBox(10, doneBox, textBox, deleteButton);
            row.getStyleClass().add("schedule-card");
            row.setPadding(new Insets(6));
            setText(null);
            setGraphic(row);
        }
    }
}
