package org.desviante.view.component;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.desviante.model.ChecklistField;
import org.desviante.model.Field;
import org.desviante.service.TaskManagerFacade;
import org.desviante.service.dto.CardDetailDTO;
import org.desviante.service.dto.UpdateCardDetailsDTO;
import org.desviante.model.Card;
import org.desviante.model.CardType;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import org.desviante.model.enums.ProgressType;
import org.desviante.service.progress.ProgressContext;
import org.desviante.service.progress.ProgressUIConfig;
import org.desviante.service.progress.ProgressInputData;
import org.desviante.service.progress.ProgressValidationResult;
import javafx.scene.image.WritableImage;
import org.desviante.service.ChecklistItemService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Controlador para visualização e edição de cards individuais.
 * 
 * <p>Responsável por gerenciar a interface de usuário para exibição,
 * edição e manipulação de cards, incluindo funcionalidades como
 * drag and drop, edição inline, controle de progresso e checklist.</p>
 * 
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 1.0
 */
public class CardViewController {

    @FXML private VBox cardPane;
    @FXML private Label cardTypeLabel;
    @FXML private ComboBox<CardType> cardTypeComboBox;
    @FXML private Label titleLabel;
    @FXML private TextField titleField;
    @FXML private Label descriptionLabel;
    @FXML private TextArea descriptionArea;

    // --- COMPONENTES DO RODAPÉ ATUALIZADOS ---
    @FXML private Separator footerSeparator;
    @FXML private VBox footerPane;
    @FXML private HBox scheduledDateBox;
    @FXML private Label scheduledDateLabel;
    @FXML private HBox dueDateBox;
    @FXML private Label dueDateLabel;
    @FXML private HBox creationDateBox;
    @FXML private Label creationDateLabel;
    @FXML private HBox lastUpdateDateBox;
    @FXML private Label lastUpdateDateLabel;
    @FXML private HBox completionDateBox;
    @FXML private Label completionDateLabel;

    // --- BOTÕES PARA INTEGRAÇÃO COM SISTEMAS EXTERNOS ---
    @FXML private Button createCalendarEventButton;
    @FXML private Button createGoogleTaskButton;

    // --- COMPONENTES DE CONTROLE DE EDIÇÃO ---
    @FXML private HBox editControlsBox;
    @FXML private Button saveButton;
    @FXML private Button deleteButton;

    // --- COMPONENTES DE CONTROLE DE MOVIMENTAÇÃO ---
    @FXML private HBox moveControlsBox;
    @FXML private Button moveUpButton;
    @FXML private Button moveDownButton;

    // --- CAMPOS DE PROGRESSO ---
    @FXML private VBox progressContainer;
    
    // Seção de progresso genérica (substitui as seções específicas)
    @FXML private VBox progressSection;
    
    // Campo de progresso geral
    @FXML private Label progressLabel;
    @FXML private Label progressValueLabel;
    @FXML private StackPane progressBarTrack;
    @FXML private Region progressBarFill;

    // Campo de status do card
    @FXML private Label statusValueLabel;
    
    // --- CAMPOS DE AGENDAMENTO E VENCIMENTO ---
    @FXML private VBox schedulingContainer;
    @FXML private VBox schedulingSection;
    @FXML private DatePicker scheduledDatePicker;
    @FXML private Spinner<Integer> scheduledHourSpinner;
    @FXML private Spinner<Integer> scheduledMinuteSpinner;
    @FXML private DatePicker dueDatePicker;
    @FXML private Spinner<Integer> dueHourSpinner;
    @FXML private Spinner<Integer> dueMinuteSpinner;
    @FXML private Button clearScheduledDateButton;
    @FXML private Button clearDueDateButton;
    @FXML private Label urgencyLabel;
    
    // Campo de tipo de progresso
    @FXML private HBox progressTypeContainer;
    @FXML private ComboBox<ProgressType> progressTypeComboBox;
    
    // --- COMPONENTE DE CHECKLIST ---
    @FXML private VBox checklistContainer;
    private final List<ChecklistViewController> checklistControllers = new ArrayList<>();
    private ChecklistItemService checklistItemService;

    // --- COMPONENTE DE CAMPOS PERCENTUAIS ---
    @FXML private VBox fieldContainer;
    private FieldViewController fieldViewController;

    // --- BOTÕES DE ADIÇÃO RÁPIDA ---
    @FXML private HBox addFieldsBox;
    @FXML private Button addPercentageFieldButton;
    @FXML private Button addChecklistItemButton;

    // --- CAMPOS DE DADOS ---
    private TaskManagerFacade facade;
    private CardDetailDTO cardData;
    private BiConsumer<Long, UpdateCardDetailsDTO> onSaveCallback;
    
    // --- PROGRESS CONTEXT ---
    private ProgressContext progressContext;

    /**
     * Construtor padrão do controlador.
     * 
     * <p>Este construtor é chamado automaticamente pelo JavaFX
     * durante a inicialização da interface.</p>
     */
    public CardViewController() {
        // Inicialização automática via JavaFX
    }

    /**
     * Inicializa o controlador e configura a interface.
     * 
     * <p>Este método é chamado automaticamente pelo JavaFX após a
     * construção do controlador.</p>
     */
    @FXML
    public void initialize() {
        System.out.println("=== CARDVIEWCONTROLLER INITIALIZE() CHAMADO ===");
        setupDragAndDrop();
        setupEditMode();
        setupTooltips();
        setupProgressTypeComboBox();
        setupCardTypeComboBox();
        setupChecklistComponent();
        setupFieldComponent();
        setupDatePickers();
        setupProgressBar();

        // ProgressContext é inicializado em setData() quando facade está disponível

        // Garantir que os controles de movimentação estejam configurados corretamente
        moveControlsBox.setVisible(false);
        moveControlsBox.setManaged(false);
        editControlsBox.setVisible(false);
        editControlsBox.setManaged(false);
        
        // Garantir que o progressContainer esteja visível desde o início
        progressContainer.setVisible(true);
        progressContainer.setManaged(true);
    }
    
    /**
     * Cria a configuração da UI para o ProgressContext.
     * 
     * @return configuração da UI
     */
    private ProgressUIConfig createProgressUIConfig() {
        return new ProgressUIConfig(
            progressContainer, progressSection,
            progressLabel, progressValueLabel,
            statusValueLabel, progressTypeContainer,
            progressBarTrack, progressBarFill
        );
    }

    /**
     * Configura o binding estrutural da barra de progresso visual (trilho +
     * preenchimento). O preenchimento precisa de maxWidth travado no
     * prefWidth, senão o StackPane o esticaria para preencher toda a
     * largura do trilho, ignorando a fração de progresso.
     */
    private void setupProgressBar() {
        progressBarFill.setMaxWidth(Region.USE_PREF_SIZE);
        progressBarFill.prefHeightProperty().bind(progressBarTrack.heightProperty());
    }

    /**
     * Configura os tooltips dos botões de movimentação
     */
    private void setupTooltips() {
        Tooltip moveUpTooltip = new Tooltip("Mover para cima");
        Tooltip moveDownTooltip = new Tooltip("Mover para baixo");
        
        Tooltip.install(moveUpButton, moveUpTooltip);
        Tooltip.install(moveDownButton, moveDownTooltip);
    }
    
    /**
     * Configura o ComboBox do tipo de progresso.
     */
    private void setupProgressTypeComboBox() {
        // Carregar tipos de progresso disponíveis (sem CUSTOM)
        progressTypeComboBox.getItems().setAll(
            ProgressType.NONE,
            ProgressType.CHECKLIST,
            ProgressType.PERCENTAGE,
            ProgressType.TOTAL
        );
        
        // Configurar a exibição dos itens
        progressTypeComboBox.setCellFactory(param -> new ListCell<ProgressType>() {
            @Override
            protected void updateItem(ProgressType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                } else {
                    setText(item.getDisplayName());
                }
            }
        });
        
        // Configurar a exibição do item selecionado
        progressTypeComboBox.setButtonCell(progressTypeComboBox.getCellFactory().call(null));
    }
    
    /**
     * Configura o ComboBox dos tipos de card.
     */
    private void setupCardTypeComboBox() {
        // Configurar a exibição dos itens
        cardTypeComboBox.setCellFactory(param -> new ListCell<CardType>() {
            @Override
            protected void updateItem(CardType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                } else {
                    setText(item.getName());
                }
            }
        });
        
        // Configurar a exibição do item selecionado
        cardTypeComboBox.setButtonCell(cardTypeComboBox.getCellFactory().call(null));
    }
    
    /**
     * Carrega os tipos de card disponíveis no ComboBox.
     */
    private void loadCardTypes() {
        if (facade != null) {
            try {
                List<CardType> cardTypes = facade.getAllCardTypes();
                cardTypeComboBox.getItems().clear();
                cardTypeComboBox.getItems().addAll(cardTypes);
            } catch (Exception e) {
                System.err.println("Erro ao carregar tipos de card: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Define o tipo de card atual no ComboBox baseado no card atual.
     */
    private void setCurrentCardType() {
        if (cardData != null && cardData.typeName() != null) {
            // Encontrar o tipo de card atual na lista
            cardTypeComboBox.getItems().stream()
                .filter(type -> type.getName().equals(cardData.typeName()))
                .findFirst()
                .ifPresent(cardTypeComboBox::setValue);
        }
    }
    
    /**
     * Configura os DatePickers e campos de horário.
     */
    private void setupDatePickers() {
        // Configurar formatação dos DatePickers
        scheduledDatePicker.setPromptText("Selecionar data");
        dueDatePicker.setPromptText("Selecionar data");
        
        // Adicionar listeners para debug
        scheduledDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("SCHEDULED DatePicker alterado: " + oldVal + " -> " + newVal);
        });
        
        dueDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("DUE DatePicker alterado: " + oldVal + " -> " + newVal);
        });
        
        // Configurar Spinners de horário para agendamento
        scheduledHourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 12));
        scheduledMinuteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        
        // Configurar Spinners de horário para vencimento
        dueHourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 18));
        dueMinuteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        
        // Configurar formatação dos Spinners (agendamento)
        scheduledHourSpinner.setEditable(true);
        scheduledMinuteSpinner.setEditable(true);
        
        // Configurar formatação dos Spinners (vencimento)
        dueHourSpinner.setEditable(true);
        dueMinuteSpinner.setEditable(true);
        
        // Configurar formatação de exibição para agendamento
        scheduledHourSpinner.getValueFactory().setConverter(new javafx.util.converter.IntegerStringConverter() {
            @Override
            public String toString(Integer value) {
                return String.format("%02d", value);
            }
        });
        
        scheduledMinuteSpinner.getValueFactory().setConverter(new javafx.util.converter.IntegerStringConverter() {
            @Override
            public String toString(Integer value) {
                return String.format("%02d", value);
            }
        });
        
        // Configurar formatação de exibição para vencimento
        dueHourSpinner.getValueFactory().setConverter(new javafx.util.converter.IntegerStringConverter() {
            @Override
            public String toString(Integer value) {
                return String.format("%02d", value);
            }
        });
        
        dueMinuteSpinner.getValueFactory().setConverter(new javafx.util.converter.IntegerStringConverter() {
            @Override
            public String toString(Integer value) {
                return String.format("%02d", value);
            }
        });
        
        // Aplicar estilos iniciais (placeholder)
        updateTimeSpinnerStyles();
        
        // Adicionar listeners para atualização de estilos
        scheduledDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateTimeSpinnerStyles());
        dueDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateTimeSpinnerStyles());
        scheduledHourSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateTimeSpinnerStyles());
        scheduledMinuteSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateTimeSpinnerStyles());
        dueHourSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateTimeSpinnerStyles());
        dueMinuteSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateTimeSpinnerStyles());
    }
    
    /**
     * Atualiza os estilos dos spinners de horário baseado no estado dos DatePickers
     */
    private void updateTimeSpinnerStyles() {
        // Verificar se há data selecionada para agendamento
        boolean hasScheduledDate = scheduledDatePicker.getValue() != null;
        
        // Aplicar estilos para spinners de agendamento
        if (hasScheduledDate) {
            scheduledHourSpinner.getStyleClass().removeAll("time-spinner-placeholder");
            scheduledHourSpinner.getStyleClass().add("time-spinner-active");
            scheduledMinuteSpinner.getStyleClass().removeAll("time-spinner-placeholder");
            scheduledMinuteSpinner.getStyleClass().add("time-spinner-active");
        } else {
            scheduledHourSpinner.getStyleClass().removeAll("time-spinner-active");
            scheduledHourSpinner.getStyleClass().add("time-spinner-placeholder");
            scheduledMinuteSpinner.getStyleClass().removeAll("time-spinner-active");
            scheduledMinuteSpinner.getStyleClass().add("time-spinner-placeholder");
        }
        
        // Verificar se há data selecionada para vencimento
        boolean hasDueDate = dueDatePicker.getValue() != null;
        
        // Aplicar estilos para spinners de vencimento
        if (hasDueDate) {
            dueHourSpinner.getStyleClass().removeAll("time-spinner-placeholder");
            dueHourSpinner.getStyleClass().add("time-spinner-active");
            dueMinuteSpinner.getStyleClass().removeAll("time-spinner-placeholder");
            dueMinuteSpinner.getStyleClass().add("time-spinner-active");
        } else {
            dueHourSpinner.getStyleClass().removeAll("time-spinner-active");
            dueHourSpinner.getStyleClass().add("time-spinner-placeholder");
            dueMinuteSpinner.getStyleClass().removeAll("time-spinner-active");
            dueMinuteSpinner.getStyleClass().add("time-spinner-placeholder");
        }
    }
    
    /**
     * Combina data do DatePicker com horário dos Spinners para criar LocalDateTime.
     * 
     * @return LocalDateTime combinado ou null se não houver data
     */
    private LocalDateTime getScheduledDateTime() {
        LocalDate date = scheduledDatePicker.getValue();
        System.out.println("getScheduledDateTime() - Data selecionada: " + date);
        
        if (date == null) {
            System.out.println("getScheduledDateTime() - Retornando null (sem data)");
            return null;
        }
        
        // Obter valores dos Spinners
        int hour = scheduledHourSpinner.getValue();
        int minute = scheduledMinuteSpinner.getValue();
        System.out.println("getScheduledDateTime() - Hora: " + hour + ", Minuto: " + minute);
        
        try {
            LocalTime time = LocalTime.of(hour, minute);
            LocalDateTime result = date.atTime(time);
            System.out.println("getScheduledDateTime() - Resultado: " + result);
            return result;
        } catch (Exception e) {
            // Se houver erro, usar meio-dia como padrão
            LocalDateTime result = date.atTime(12, 0);
            System.out.println("getScheduledDateTime() - Erro, usando padrão 12:00: " + result);
            return result;
        }
    }
    
    /**
     * Combina data do DatePicker de vencimento com horário dos Spinners para criar LocalDateTime.
     * 
     * @return LocalDateTime combinado ou null se não houver data
     */
    private LocalDateTime getDueDateTime() {
        LocalDate date = dueDatePicker.getValue();
        System.out.println("getDueDateTime() - Data selecionada: " + date);
        
        if (date == null) {
            System.out.println("getDueDateTime() - Retornando null (sem data)");
            return null;
        }
        
        // Obter valores dos Spinners
        int hour = dueHourSpinner.getValue();
        int minute = dueMinuteSpinner.getValue();
        System.out.println("getDueDateTime() - Hora: " + hour + ", Minuto: " + minute);
        
        try {
            LocalTime time = LocalTime.of(hour, minute);
            LocalDateTime result = date.atTime(time);
            System.out.println("getDueDateTime() - Resultado: " + result);
            return result;
        } catch (Exception e) {
            // Se houver erro, usar 18:00 como padrão
            LocalDateTime result = date.atTime(18, 0);
            System.out.println("getDueDateTime() - Erro, usando padrão 18:00: " + result);
            return result;
        }
    }
    
    /**
     * Configura o componente de checklist.
     */
    private void setupChecklistComponent() {
        checklistContainer.getChildren().clear();
        checklistControllers.clear();
        checklistContainer.setVisible(false);
        checklistContainer.setManaged(false);
    }

    private void addChecklistGroupController(ChecklistField group, Long cardId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/checklist-view.fxml"));
            VBox checklistView = loader.load();
            ChecklistViewController controller = loader.getController();
            controller.initialize(checklistItemService);
            controller.setOnProgressChanged(this::refreshProgressDisplay);
            controller.setOnGroupDeleted(() -> {
                checklistContainer.getChildren().remove(checklistView);
                checklistControllers.remove(controller);
                refreshChecklistContainerVisibility();
                refreshProgressDisplay();
            });
            controller.loadGroup(cardId, group.getId(), group.getText());
            checklistContainer.getChildren().add(checklistView);
            checklistControllers.add(controller);
        } catch (Exception e) {
            System.err.println("Erro ao carregar checklist group controller: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Configura o componente de campos percentuais.
     */
    private void setupFieldComponent() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/percentage-fields-view.fxml"));
            VBox fieldView = loader.load();
            fieldViewController = loader.getController();

            fieldContainer.getChildren().clear();
            fieldContainer.getChildren().add(fieldView);

            fieldContainer.setVisible(false);
            fieldContainer.setManaged(false);
        } catch (Exception e) {
            System.err.println("Erro ao carregar componente de campos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Inicializa o FieldViewController com o FieldService quando o facade está disponível.
     */
    private void initializeFieldComponentIfNeeded() {
        if (fieldViewController != null && facade != null) {
            try {
                fieldViewController.initialize(facade.getFieldService());
                fieldViewController.setOnProgressChanged(this::refreshProgressDisplay);
            } catch (Exception e) {
                System.err.println("Erro ao inicializar campos: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Atualiza a visibilidade do componente de campos percentuais com base no tipo de progresso.
     */
    private void updateFieldComponent(CardDetailDTO card) {
        if (fieldViewController == null) return;
        fieldViewController.loadFields(card.id());
        refreshFieldContainerVisibility();
    }

    private void refreshFieldContainerVisibility() {
        boolean hasFields = fieldViewController != null && fieldViewController.hasFields();
        fieldContainer.setVisible(hasFields);
        fieldContainer.setManaged(hasFields);
    }

    /**
     * Define os dados do card e configura o controlador.
     * 
     * @param facade fachada principal para gerenciamento de tarefas
     * @param card dados do card a ser exibido
     * @param onSaveCallback callback para quando o card for salvo
     */
    public void setData(
            TaskManagerFacade facade,
            CardDetailDTO card,
            BiConsumer<Long, UpdateCardDetailsDTO> onSaveCallback
    ) {
        this.facade = facade;
        this.cardData = card;
        this.onSaveCallback = onSaveCallback;

        // Inicializar ProgressContext agora que facade está disponível
        progressContext = new ProgressContext(facade.getFieldService());
        progressContext.setUIConfig(createProgressUIConfig());

        // Inicializar o checklist se ainda não foi inicializado
        initializeChecklistIfNeeded();
        initializeFieldComponentIfNeeded();
        
        updateDisplayData(card);
    }

    /**
     * Atualiza a exibição dos dados do card.
     * 
     * @param card dados atualizados do card
     */
    public void updateDisplayData(CardDetailDTO card) {
        this.cardData = card;
        updateCardTypeLabel(card);
        titleLabel.setText(card.title());
        descriptionLabel.setText(card.description());
        updateProgressFields(card);
        updateSchedulingFields(card);
        updateFooter(card);
        showAddFieldsButtons();
        
        // Garantir que os controles de movimentação sejam exibidos em modo de visualização
        switchToDisplayMode();
        
        // Garantir que o status seja sempre visível após a atualização
        if (statusValueLabel.getParent() != null) {
            statusValueLabel.getParent().setVisible(true);
            statusValueLabel.getParent().setManaged(true);
        }
    }

    /**
     * Carrega as datas de agendamento e vencimento do banco de dados para os campos de edição
     */
    private void loadSchedulingDatesFromDatabase() {
        System.out.println("=== CARREGANDO DATAS DO BANCO DE DADOS ===");
        System.out.println("Card ID: " + cardData.id());
        
        if (facade != null) {
            try {
                // Buscar o card atualizado do banco
                Optional<Card> cardOptional = facade.getCardById(cardData.id());
                if (cardOptional.isPresent()) {
                    Card card = cardOptional.get();
                    System.out.println("Card encontrado no banco:");
                    System.out.println("  - Agendamento: " + card.getScheduledDate());
                    System.out.println("  - Vencimento: " + card.getDueDate());
                    
                    // Carregar data de agendamento
                    if (card.getScheduledDate() != null) {
                        LocalDateTime scheduledDateTime = card.getScheduledDate();
                        scheduledDatePicker.setValue(scheduledDateTime.toLocalDate());
                        scheduledHourSpinner.getValueFactory().setValue(scheduledDateTime.getHour());
                        scheduledMinuteSpinner.getValueFactory().setValue(scheduledDateTime.getMinute());
                        System.out.println("Data de agendamento carregada: " + scheduledDateTime);
                    } else {
                        // Limpar campos se não houver data
                        scheduledDatePicker.setValue(null);
                        scheduledHourSpinner.getValueFactory().setValue(12);
                        scheduledMinuteSpinner.getValueFactory().setValue(0);
                        System.out.println("Nenhuma data de agendamento encontrada - campos limpos");
                    }
                    
                    // Carregar data de vencimento
                    if (card.getDueDate() != null) {
                        LocalDateTime dueDateTime = card.getDueDate();
                        dueDatePicker.setValue(dueDateTime.toLocalDate());
                        dueHourSpinner.getValueFactory().setValue(dueDateTime.getHour());
                        dueMinuteSpinner.getValueFactory().setValue(dueDateTime.getMinute());
                        System.out.println("Data de vencimento carregada: " + dueDateTime);
                    } else {
                        // Limpar campos se não houver data
                        dueDatePicker.setValue(null);
                        dueHourSpinner.getValueFactory().setValue(18);
                        dueMinuteSpinner.getValueFactory().setValue(0);
                        System.out.println("Nenhuma data de vencimento encontrada - campos limpos");
                    }
                    
                    // Atualizar exibição de urgência
                    updateUrgencyDisplay();
                    
                    // Atualizar estilos dos spinners
                    updateTimeSpinnerStyles();
                    
                } else {
                    System.out.println("ERRO: Card não encontrado no banco de dados");
                }
            } catch (Exception e) {
                System.out.println("ERRO ao carregar datas do banco: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("ERRO: facade é null");
        }
    }

    /**
     * Atualiza os campos de agendamento e vencimento
     */
    private void updateSchedulingFields(CardDetailDTO card) {
        System.out.println("=== ATUALIZANDO CAMPOS DE AGENDAMENTO ===");
        System.out.println("Card ID: " + card.id());
        System.out.println("Data de agendamento do DTO: " + card.scheduledDate());
        System.out.println("Data de vencimento do DTO: " + card.dueDate());
        
        // Atualizar campos de data e horário para agendamento
        if (card.scheduledDate() != null && !card.scheduledDate().trim().isEmpty()) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(card.scheduledDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                scheduledDatePicker.setValue(dateTime.toLocalDate());
                scheduledHourSpinner.getValueFactory().setValue(dateTime.getHour());
                scheduledMinuteSpinner.getValueFactory().setValue(dateTime.getMinute());
            } catch (DateTimeParseException e) {
                scheduledDatePicker.setValue(null);
                scheduledHourSpinner.getValueFactory().setValue(12);
                scheduledMinuteSpinner.getValueFactory().setValue(0);
            }
        } else {
            scheduledDatePicker.setValue(null);
            scheduledHourSpinner.getValueFactory().setValue(12);
            scheduledMinuteSpinner.getValueFactory().setValue(0);
        }
        
        // Atualizar campos de data e horário para vencimento
        if (card.dueDate() != null && !card.dueDate().trim().isEmpty()) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(card.dueDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                dueDatePicker.setValue(dateTime.toLocalDate());
                dueHourSpinner.getValueFactory().setValue(dateTime.getHour());
                dueMinuteSpinner.getValueFactory().setValue(dateTime.getMinute());
            } catch (DateTimeParseException e) {
                dueDatePicker.setValue(null);
                dueHourSpinner.getValueFactory().setValue(18);
                dueMinuteSpinner.getValueFactory().setValue(0);
            }
        } else {
            dueDatePicker.setValue(null);
            dueHourSpinner.getValueFactory().setValue(18);
            dueMinuteSpinner.getValueFactory().setValue(0);
        }
        
        // Atualizar exibição da urgência
        updateUrgencyDisplay();
        
        // Mostrar seção de agendamento se houver dados ou estiver em modo de edição
        boolean hasSchedulingData = (card.scheduledDate() != null && !card.scheduledDate().isEmpty()) || 
                                   (card.dueDate() != null && !card.dueDate().isEmpty());
        boolean isEditing = editControlsBox.isVisible();
        
        if (hasSchedulingData || isEditing) {
            schedulingContainer.setVisible(true);
            schedulingContainer.setManaged(true);
        } else {
            schedulingContainer.setVisible(false);
            schedulingContainer.setManaged(false);
        }
    }

    /**
     * Atualiza a label da categoria do card
     */
    private void updateCardTypeLabel(CardDetailDTO card) {
        String typeName = card.typeName();
        String typeText = "";
        
        if (typeName != null && !typeName.trim().isEmpty()) {
            // Usar o nome real do tipo de Card
            typeText = typeName;
        } else {
            typeText = "Card";
        }
        
        cardTypeLabel.setText(typeText);
        cardTypeLabel.setVisible(true);
        cardTypeLabel.setManaged(true);
    }

    /**
     * Atualiza os campos de progresso baseado no tipo do card
     */
    private void updateProgressFields(CardDetailDTO card) {
        // Usar ProgressContext para gerenciar o progresso
        progressContext.setStrategy(card.progressType());
        progressContext.configureUI();
        progressContext.updateDisplay(
            card.totalUnits(),
            card.currentUnits(),
            card.columnKind(),
            card.id()
        );

        // Gerenciar componentes de checklist e campos percentuais
        updateChecklistComponent(card);
        updateFieldComponent(card);
    }
    
    /**
     * Inicializa o serviço de checklist se necessário.
     */
    private void initializeChecklistIfNeeded() {
        if (facade != null && checklistItemService == null) {
            checklistItemService = new ChecklistItemService(facade.getFieldService());
        }
    }
    
    /**
     * Atualiza o componente de checklist baseado nos grupos existentes do card.
     */
    private void updateChecklistComponent(CardDetailDTO card) {
        checklistContainer.getChildren().clear();
        checklistControllers.clear();
        if (checklistItemService == null) return;
        List<Field> groups = facade.getFieldService().getChecklistGroupsByCardId(card.id());
        for (Field group : groups) {
            if (group instanceof ChecklistField cf) {
                addChecklistGroupController(cf, card.id());
            }
        }
        refreshChecklistContainerVisibility();
    }

    private void refreshChecklistContainerVisibility() {
        boolean hasGroups = !checklistControllers.isEmpty();
        checklistContainer.setVisible(hasGroups);
        checklistContainer.setManaged(hasGroups);
    }

    /**
     * Lógica atualizada para gerenciar a visibilidade de cada linha de data.
     */
    private void updateFooter(CardDetailDTO card) {
        System.out.println("=== ATUALIZANDO RODAPÉ DO CARD ===");
        System.out.println("Card ID: " + card.id());
        System.out.println("Data de agendamento do DTO: " + card.scheduledDate());
        System.out.println("Data de vencimento do DTO: " + card.dueDate());
        
        boolean scheduledVisible = setDateRow(scheduledDateBox, scheduledDateLabel, card.scheduledDate());
        boolean dueVisible = setDateRow(dueDateBox, dueDateLabel, card.dueDate());
        boolean creationVisible = setDateRow(creationDateBox, creationDateLabel, card.creationDate());
        boolean updateVisible = setDateRow(lastUpdateDateBox, lastUpdateDateLabel, card.lastUpdateDate());
        boolean completionVisible = setDateRow(completionDateBox, completionDateLabel, card.completionDate());

        System.out.println("Visibilidade - Agendamento: " + scheduledVisible + ", Vencimento: " + dueVisible);
        System.out.println("Visibilidade - Criação: " + creationVisible + ", Atualização: " + updateVisible + ", Conclusão: " + completionVisible);

        // Torna o rodapé e o separador visíveis apenas se houver alguma data para mostrar
        boolean hasAnyDate = scheduledVisible || dueVisible || creationVisible || updateVisible || completionVisible;
        System.out.println("Rodapé visível: " + hasAnyDate);
        
        footerSeparator.setVisible(hasAnyDate);
        footerSeparator.setManaged(hasAnyDate);
        footerPane.setVisible(hasAnyDate);
        footerPane.setManaged(hasAnyDate);
    }

    private boolean setDateRow(HBox container, Label dateLabel, String dateValue) {
        System.out.println("setDateRow() - Valor da data: '" + dateValue + "'");
        if (dateValue != null && !dateValue.trim().isEmpty()) {
            dateLabel.setText(dateValue);
            container.setVisible(true);
            container.setManaged(true);
            System.out.println("setDateRow() - Linha visível: true");
            return true;
        } else {
            container.setVisible(false);
            container.setManaged(false);
            System.out.println("setDateRow() - Linha visível: false");
            return false;
        }
    }



    private void setupDragAndDrop() {
        cardPane.setOnDragDetected(event -> {
            Dragboard db = cardPane.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(cardData.id().toString());
            db.setContent(content);
            
            // Adicionar preview visual do card sendo arrastado
            if (cardData != null) {
                // Criar uma snapshot do card para preview
                WritableImage snapshot = cardPane.snapshot(null, null);
                db.setDragView(snapshot);
                
                // Posicionar o preview no cursor
                db.setDragViewOffsetX(snapshot.getWidth() / 2);
                db.setDragViewOffsetY(snapshot.getHeight() / 2);
            }
            
            // Aplicar estilo visual durante o drag
            cardPane.setStyle(cardPane.getStyle() + "; -fx-opacity: 0.6; -fx-effect: dropshadow(gaussian, rgba(0,123,255,0.5), 10, 0, 0, 2);");
            
            event.consume();
        });
        
        // Restaurar estilo quando o drag termina
        cardPane.setOnDragDone(event -> {
            cardPane.setStyle(cardPane.getStyle().replace("; -fx-opacity: 0.6; -fx-effect: dropshadow(gaussian, rgba(0,123,255,0.5), 10, 0, 0, 2);", ""));
            event.consume();
        });
    }

    private void setupEditMode() {
        cardPane.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                switchToEditMode();
            }
        });

        titleField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleSave();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                switchToDisplayMode();
            }
        });

        descriptionArea.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
                handleSave();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                switchToDisplayMode();
            }
        });
    }

    private void switchToEditMode() {
        titleField.setText(titleLabel.getText());
        descriptionArea.setText(descriptionLabel.getText());
        
        // Configurar ComboBox de tipos de card
        loadCardTypes();
        setCurrentCardType();
        
        titleLabel.setVisible(false);
        titleField.setVisible(true);
        titleField.setManaged(true);
        
        descriptionLabel.setVisible(false);
        descriptionArea.setVisible(true);
        descriptionArea.setManaged(true);
        
        // Mostrar ComboBox de tipo de card e ocultar label
        cardTypeLabel.setVisible(false);
        cardTypeComboBox.setVisible(true);
        cardTypeComboBox.setManaged(true);
        
        editControlsBox.setVisible(true);
        editControlsBox.setManaged(true);
        
        // Ocultar controles de movimentação em modo de edição
        moveControlsBox.setVisible(false);
        moveControlsBox.setManaged(false);
        
        // Mostrar seção de agendamento em modo de edição
        schedulingSection.setVisible(true);
        schedulingSection.setManaged(true);
        
        // Carregar datas salvas do banco de dados
        loadSchedulingDatesFromDatabase();
        
        // Adicionar listeners para atualizar urgência em tempo real
        scheduledDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateUrgencyDisplay());
        scheduledHourSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateUrgencyDisplay());
        scheduledMinuteSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateUrgencyDisplay());
        dueDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateUrgencyDisplay());
        dueHourSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateUrgencyDisplay());
        dueMinuteSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateUrgencyDisplay());
        
        // Mostrar campos de progresso em modo de edição
        if (progressContainer.isVisible()) {
            progressContainer.setVisible(true);
            progressContainer.setManaged(true);

            // A seção de progresso manual nunca é exibida: o progresso é sempre
            // calculado a partir dos campos (checklist e percentuais)
            progressSection.setVisible(false);
            progressSection.setManaged(false);

            // Mostrar "Status:" sempre em modo de edição
            statusValueLabel.getParent().setVisible(true);

            // Mostrar o ComboBox do tipo de progresso em modo de edição
            progressTypeContainer.setVisible(true);
            progressTypeContainer.setManaged(true);
            progressTypeComboBox.setValue(cardData.progressType());
        }
        
        Platform.runLater(() -> titleField.requestFocus());
    }

    private void switchToDisplayMode() {
        titleField.setVisible(false);
        titleField.setManaged(false);
        titleLabel.setVisible(true);
        
        descriptionArea.setVisible(false);
        descriptionArea.setManaged(false);
        descriptionLabel.setVisible(true);
        
        // Ocultar ComboBox de tipo de card e mostrar label
        cardTypeComboBox.setVisible(false);
        cardTypeComboBox.setManaged(false);
        cardTypeLabel.setVisible(true);
        
        editControlsBox.setVisible(false);
        editControlsBox.setManaged(false);
        
        // Mostrar controles de movimentação em modo de visualização
        moveControlsBox.setVisible(true);
        moveControlsBox.setManaged(true);
        
        // Ocultar seção de agendamento em modo de visualização
        schedulingSection.setVisible(false);
        schedulingSection.setManaged(false);
        
        // Configurar campos de progresso em modo de visualização
        if (progressContainer.isVisible()) {
            progressContainer.setVisible(true);
            progressContainer.setManaged(true);

            // Seção de progresso manual sempre oculta: progresso vem dos campos
            progressSection.setVisible(false);
            progressSection.setManaged(false);

            // Garantir que o status seja sempre visível em modo de exibição
            if (statusValueLabel.getParent() != null) {
                statusValueLabel.getParent().setVisible(true);
                statusValueLabel.getParent().setManaged(true);
            }

            // Ocultar o ComboBox do tipo de progresso em modo de visualização
            progressTypeContainer.setVisible(false);
            progressTypeContainer.setManaged(false);
        }
    }

    @FXML
    private void handleSave() {
        System.out.println("🔵 MÉTODO handleSave() CHAMADO - BOTÃO SALVAR CLICADO");
        System.out.println("=== MÉTODO handleSave() CHAMADO ===");
        String newTitle = titleField.getText().trim();
        String newDescription = descriptionArea.getText().trim();
        
        // Obter o tipo de card selecionado
        CardType selectedCardType = cardTypeComboBox.getValue();
        if (selectedCardType == null) {
            showAlert("Erro", "Selecione um tipo de card válido", Alert.AlertType.ERROR);
            return;
        }
        
        // Obter o tipo de progresso selecionado
        ProgressType selectedProgressType = progressTypeComboBox.getValue();
        if (selectedProgressType == null) {
            selectedProgressType = ProgressType.NONE; // Valor padrão
        }
        
        // O progresso é sempre calculado pelos campos (checklist/percentuais) via estratégia;
        // total/current units são apenas preservados do estado atual do card
        Integer totalUnits = cardData.totalUnits();
        Integer currentUnits = cardData.currentUnits();
        
        // Usar ProgressContext para validação
        ProgressInputData inputData = new ProgressInputData(totalUnits, currentUnits, newTitle, newDescription);
        progressContext.setStrategy(selectedProgressType);
        ProgressValidationResult validationResult = progressContext.validate(inputData);
        
        System.out.println("=== VALIDAÇÃO DE PROGRESSO ===");
        System.out.println("Tipo de progresso: " + selectedProgressType);
        System.out.println("Total units: " + totalUnits);
        System.out.println("Current units: " + currentUnits);
        System.out.println("Validação válida: " + validationResult.isValid());
        if (!validationResult.isValid()) {
            System.out.println("Erro de validação: " + validationResult.getErrorMessage());
        }
        
        if (!validationResult.isValid()) {
            showAlert("Erro", validationResult.getErrorMessage(), Alert.AlertType.ERROR);
            return;
        }
        
        // Atualizar o card com o novo tipo
        if (facade != null) {
            try {
                CardDetailDTO updatedCard = facade.updateCardType(cardData.id(), selectedCardType.getId());
                // Atualizar apenas os dados necessários sem limpar os DatePickers
                this.cardData = updatedCard;
                updateCardTypeLabel(updatedCard);
            } catch (Exception e) {
                showAlert("Erro", "Erro ao atualizar tipo do card: " + e.getMessage(), Alert.AlertType.ERROR);
                return;
            }
        }
        
        // Criar DTO de atualização com progresso
        UpdateCardDetailsDTO updateData = new UpdateCardDetailsDTO(newTitle, newDescription, totalUnits, currentUnits, selectedProgressType);
        
        // Salvar campos de agendamento e vencimento
        try {
            String logMsg1 = "=== INICIANDO SALVAMENTO DE DATAS DE AGENDAMENTO ===";
            String logMsg2 = "Card ID: " + cardData.id();
            String logMsg3 = "Facade disponível: " + (facade != null);
            
            System.out.println(logMsg1);
            System.out.println(logMsg2);
            System.out.println(logMsg3);
            
            // Log também no arquivo de debug do CardRepository
            logToFile(logMsg1);
            logToFile(logMsg2);
            logToFile(logMsg3);
            
            // Log do estado dos DatePickers antes de coletar
            System.out.println("=== ESTADO DOS DATEPICKERS ANTES DE COLETAR ===");
            System.out.println("Scheduled DatePicker value: " + scheduledDatePicker.getValue());
            System.out.println("Due DatePicker value: " + dueDatePicker.getValue());
            System.out.println("Scheduled Hour: " + scheduledHourSpinner.getValue());
            System.out.println("Scheduled Minute: " + scheduledMinuteSpinner.getValue());
            System.out.println("Due Hour: " + dueHourSpinner.getValue());
            System.out.println("Due Minute: " + dueMinuteSpinner.getValue());
            
            LocalDateTime scheduledDate = getScheduledDateTime();
            LocalDateTime dueDate = getDueDateTime();
            
            // Log das datas coletadas
            System.out.println("=== SALVANDO DATAS DO CARD ===");
            System.out.println("Card ID: " + cardData.id());
            System.out.println("Data de agendamento coletada: " + scheduledDate);
            System.out.println("Data de vencimento coletada: " + dueDate);
            
            // Validar datas se ambas estiverem preenchidas
            if (scheduledDate != null && dueDate != null && dueDate.isBefore(scheduledDate)) {
                showAlert("Erro", "Data de vencimento não pode ser anterior à data de agendamento", Alert.AlertType.ERROR);
                return;
            }
            
            // Atualizar datas no card PRIMEIRO
            if (facade != null) {
                System.out.println("=== CHAMANDO FACADE.SETSCHEDULINGDATES() ===");
                System.out.println("Card ID: " + cardData.id());
                System.out.println("Scheduled Date: " + scheduledDate);
                System.out.println("Due Date: " + dueDate);
                System.out.println("Chamando facade.setSchedulingDates()...");
                
                // Obter as datas atuais do card para preservar as que não foram alteradas
                Optional<Card> currentCardOptional = facade.getCardById(cardData.id());
                LocalDateTime currentScheduledDate = null;
                LocalDateTime currentDueDate = null;
                
                if (currentCardOptional.isPresent()) {
                    Card currentCard = currentCardOptional.get();
                    currentScheduledDate = currentCard.getScheduledDate();
                    currentDueDate = currentCard.getDueDate();
                    System.out.println("Datas atuais no banco:");
                    System.out.println("  - Agendamento atual: " + currentScheduledDate);
                    System.out.println("  - Vencimento atual: " + currentDueDate);
                }
                
                // Usar as datas atuais se as novas forem null (preservar dados existentes)
                LocalDateTime finalScheduledDate = (scheduledDate != null) ? scheduledDate : currentScheduledDate;
                LocalDateTime finalDueDate = (dueDate != null) ? dueDate : currentDueDate;
                
                System.out.println("Datas finais para salvar:");
                System.out.println("  - Agendamento final: " + finalScheduledDate);
                System.out.println("  - Vencimento final: " + finalDueDate);
                
                System.out.println("=== EXECUTANDO FACADE.SETSCHEDULINGDATES() ===");
                try {
                    facade.setSchedulingDates(cardData.id(), finalScheduledDate, finalDueDate);
                    System.out.println("✅ facade.setSchedulingDates() executado com sucesso");
                    logToFile("✅ facade.setSchedulingDates() executado com sucesso");
                } catch (Exception e) {
                    String errorMsg = "❌ ERRO ao executar facade.setSchedulingDates(): " + e.getMessage();
                    System.err.println(errorMsg);
                    logToFile(errorMsg);
                    e.printStackTrace();
                    throw e; // Re-lançar para que o erro seja propagado
                }
                
                // Verificar se as datas foram realmente salvas
                try {
                    Optional<Card> cardOptional = facade.getCardById(cardData.id());
                    if (cardOptional.isPresent()) {
                        Card card = cardOptional.get();
                        System.out.println("VERIFICAÇÃO PÓS-SALVAMENTO:");
                        System.out.println("  - Agendamento salvo: " + card.getScheduledDate());
                        System.out.println("  - Vencimento salvo: " + card.getDueDate());
                    } else {
                        System.out.println("ERRO: Não foi possível verificar o card após salvamento");
                    }
                } catch (Exception e) {
                    System.out.println("ERRO ao verificar card após salvamento: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("ERRO: facade é null!");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("ERRO ao salvar datas: " + e.getMessage());
            showAlert("Erro", e.getMessage(), Alert.AlertType.ERROR);
            return;
        } catch (Exception e) {
            System.out.println("ERRO inesperado ao salvar datas: " + e.getMessage());
            e.printStackTrace();
            showAlert("Erro", "Erro inesperado ao salvar datas: " + e.getMessage(), Alert.AlertType.ERROR);
            return;
        }
        
        // Verificar se houve mudança significativa no progresso para mostrar feedback
        String progressChangeMessage = getProgressChangeMessage(cardData, updateData);
        
        // As datas foram salvas - deixar o callback recarregar a interface
        System.out.println("Datas salvas com sucesso. Callback será chamado para recarregar a interface.");
        
        if (onSaveCallback != null) {
            onSaveCallback.accept(cardData.id(), updateData);
        }
        
        // Mostrar feedback sobre mudança de coluna se aplicável
        if (progressChangeMessage != null && !progressChangeMessage.isEmpty()) {
            showAlert("Sincronização Automática", progressChangeMessage, Alert.AlertType.INFORMATION);
        }
        
        switchToDisplayMode();
    }
    
    /**
     * Gera mensagem de feedback sobre mudanças de progresso e coluna.
     * 
     * @param originalCard dados originais do card
     * @param updateData dados de atualização
     * @return mensagem de feedback ou null se não houver mudança significativa
     */
    private String getProgressChangeMessage(CardDetailDTO originalCard, UpdateCardDetailsDTO updateData) {
        // Progresso e status estão desacoplados, não mostrar mensagens de sincronização
        return null;
    }

    @FXML
    private void handleDelete() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Exclusão");
        alert.setHeaderText("Excluir Card");
        alert.setContentText("Tem certeza que deseja excluir o card '" + cardData.title() + "'?");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    facade.deleteCard(cardData.id());
                    
                    // Notificar o controlador pai para remover o card da interface
                    if (onSaveCallback != null) {
                        onSaveCallback.accept(cardData.id(), null);
                    }
                    
                    showAlert("Sucesso", "Card excluído com sucesso!", Alert.AlertType.INFORMATION);
                } catch (Exception e) {
                    showAlert("Erro", "Erro ao excluir o card: " + e.getMessage(), Alert.AlertType.ERROR);
                }
            }
        });
    }

    @FXML
    private void handleCreateCalendarEvent() {
        if (cardData == null) {
            showAlert("Erro", "Nenhum card selecionado para criar evento no calendário.", Alert.AlertType.ERROR);
            return;
        }

        // Verificar se o card pode ter um evento criado
        if (!facade.canCreateCalendarEvent(cardData.id())) {
            showAlert("Aviso", "Este card não possui data de agendamento. Configure uma data de agendamento primeiro.", Alert.AlertType.WARNING);
            return;
        }

        // Desabilitar o botão para evitar múltiplos cliques
        createCalendarEventButton.setDisable(true);

        // Executar em thread de background
        javafx.concurrent.Task<Boolean> creationTask = new javafx.concurrent.Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return facade.createCalendarEvent(cardData.id());
            }
        };

        creationTask.setOnSucceeded(event -> {
            createCalendarEventButton.setDisable(false);
            Boolean success = creationTask.getValue();
            if (success) {
                showAlert("Sucesso", "Evento criado com sucesso no calendário!", Alert.AlertType.INFORMATION);
            } else {
                showAlert("Erro", "Não foi possível criar o evento no calendário.", Alert.AlertType.ERROR);
            }
        });

        creationTask.setOnFailed(event -> {
            createCalendarEventButton.setDisable(false);
            Throwable e = creationTask.getException();
            String errorMessage = (e != null) ? e.getMessage() : "Ocorreu um erro desconhecido.";
            showAlert("Erro ao Criar Evento", "Ocorreu um erro ao tentar criar o evento no calendário: " + errorMessage, Alert.AlertType.ERROR);
        });

        new Thread(creationTask).start();
    }

    @FXML
    private void handleCreateGoogleTask() {
        if (cardData == null) {
            showAlert("Erro", "Nenhum card selecionado para criar tarefa no Google Tasks.", Alert.AlertType.ERROR);
            return;
        }

        // Verificar se o card pode ter uma tarefa criada
        if (!facade.canCreateGoogleTask(cardData.id())) {
            showAlert("Aviso", "Este card não possui data de vencimento. Configure uma data de vencimento primeiro.", Alert.AlertType.WARNING);
            return;
        }

        // Desabilitar o botão para evitar múltiplos cliques
        createGoogleTaskButton.setDisable(true);

        // Executar em thread de background
        javafx.concurrent.Task<Boolean> creationTask = new javafx.concurrent.Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return facade.createGoogleTask(cardData.id());
            }
        };

        creationTask.setOnSucceeded(event -> {
            createGoogleTaskButton.setDisable(false);
            Boolean success = creationTask.getValue();
            if (success) {
                showAlert("Sucesso", "Tarefa criada com sucesso no Google Tasks!", Alert.AlertType.INFORMATION);
            } else {
                showAlert("Erro", "Não foi possível criar a tarefa no Google Tasks.", Alert.AlertType.ERROR);
            }
        });

        creationTask.setOnFailed(event -> {
            createGoogleTaskButton.setDisable(false);
            Throwable e = creationTask.getException();
            String errorMessage = (e != null) ? e.getMessage() : "Ocorreu um erro desconhecido.";
            
            if (errorMessage != null && errorMessage.contains("Google Tasks API não está configurada")) {
                showAlert("Integração Google Tasks Desabilitada",
                    "A integração com Google Tasks não está configurada.\n\n" +
                    "Para habilitar:\n" +
                    "1. Configure as credenciais do Google em src/main/resources/auth/credentials.json\n" +
                    "2. Execute a autenticação inicial\n" +
                    "3. Reinicie a aplicação",
                    Alert.AlertType.WARNING);
            } else {
                showAlert("Erro ao Criar Tarefa", "Ocorreu um erro ao tentar criar a tarefa no Google: " + errorMessage, Alert.AlertType.ERROR);
            }
        });

        new Thread(creationTask).start();
    }

    private void refreshProgressDisplay() {
        if (cardData != null && progressContext != null) {
            progressContext.updateDisplay(
                cardData.totalUnits(),
                cardData.currentUnits(),
                cardData.columnKind(),
                cardData.id()
            );
        }
    }

    private void showAddFieldsButtons() {
        if (addFieldsBox != null) {
            addFieldsBox.setVisible(true);
            addFieldsBox.setManaged(true);
        }
    }

    @FXML
    private void handleAddPercentageField() {
        if (fieldViewController != null) {
            fieldViewController.promptAddField();
            refreshFieldContainerVisibility();
        }
    }

    @FXML
    private void handleAddChecklistItem() {
        if (cardData == null || checklistItemService == null) return;
        TextInputDialog dialog = new TextInputDialog("Checklist");
        dialog.setTitle("Novo Checklist");
        dialog.setHeaderText("Nome do novo checklist:");
        dialog.setContentText("Nome:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                try {
                    int orderIndex = facade.getFieldService().countFieldsByCardId(cardData.id());
                    ChecklistField group = facade.getFieldService().createChecklistGroup(cardData.id(), name.trim(), orderIndex);
                    addChecklistGroupController(group, cardData.id());
                    refreshChecklistContainerVisibility();
                } catch (Exception e) {
                    showAlert("Erro", "Erro ao criar checklist: " + e.getMessage(), Alert.AlertType.ERROR);
                }
            }
        });
    }

    @FXML
    private void handleMoveUp() {
        if (cardData == null) {
            showAlert("Erro", "Nenhum card selecionado para mover.", Alert.AlertType.ERROR);
            return;
        }

        try {
            boolean moved = facade.moveCardUp(cardData.id());
            if (moved) {
                // Notificar o controlador pai para recarregar a interface
                Platform.runLater(() -> {
                    if (onSaveCallback != null) {
                        // Para movimentação, não chamar updateCardDetails() pois isso sobrescreve o order_index
                        // Apenas recarregar a interface passando null para indicar que é apenas uma atualização de posição
                        onSaveCallback.accept(cardData.id(), null);
                    }
                });
            } else {
                showAlert("Informação", "Card já está no topo da coluna.", Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            showAlert("Erro", "Falha ao mover card para cima: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleMoveDown() {
        if (cardData == null) {
            showAlert("Erro", "Nenhum card selecionado para mover.", Alert.AlertType.ERROR);
            return;
        }

        try {
            boolean moved = facade.moveCardDown(cardData.id());
            if (moved) {
                // Notificar o controlador pai para recarregar a interface
                Platform.runLater(() -> {
                    if (onSaveCallback != null) {
                        // Para movimentação, não chamar updateCardDetails() pois isso sobrescreve o order_index
                        // Apenas recarregar a interface passando null para indicar que é apenas uma atualização de posição
                        onSaveCallback.accept(cardData.id(), null);
                    }
                });
            } else {
                showAlert("Informação", "Card já está na base da coluna.", Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            showAlert("Erro", "Falha ao mover card para baixo: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Limpa o campo de data de agendamento.
     */
    @FXML
    private void handleClearScheduledDate() {
        System.out.println("🔴 BOTÃO LIMPAR CLICADO - handleClearScheduledDate() CHAMADO");
        System.out.println("=== LIMPANDO DATA DE AGENDAMENTO ===");
        
        // Verificar se há uma data de agendamento salva no banco
        Optional<Card> currentCardOptional = facade.getCardById(cardData.id());
        boolean hasScheduledDate = currentCardOptional.isPresent() && 
                                 currentCardOptional.get().getScheduledDate() != null;
        
        if (hasScheduledDate) {
            // Exibir confirmação antes de remover
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmar Remoção");
            alert.setHeaderText("Remover Data de Agendamento");
            alert.setContentText("Deseja realmente remover a data de agendamento?");
            
            Optional<ButtonType> result = alert.showAndWait();
            System.out.println("🔴 RESULTADO DA CONFIRMAÇÃO: " + result);
            if (result.isPresent()) {
                System.out.println("🔴 BOTÃO SELECIONADO: " + result.get());
                System.out.println("🔴 É OK? " + (result.get() == ButtonType.OK));
            }
            
            if (result.isPresent() && result.get() == ButtonType.OK) {
                System.out.println("🔴 USUÁRIO CONFIRMOU - INICIANDO REMOÇÃO");
                // Usuário confirmou - limpar e salvar
                scheduledDatePicker.setValue(null);
                scheduledHourSpinner.getValueFactory().setValue(12);
                scheduledMinuteSpinner.getValueFactory().setValue(0);
                
                System.out.println("🔴 CAMPOS LIMPOS - CHAMANDO FACADE.SETSCHEDULINGDATES()");
                // Salvar a remoção no banco - preservar data de vencimento
                try {
                    // Obter a data de vencimento atual para preservá-la
                    Optional<Card> currentCard = facade.getCardById(cardData.id());
                    LocalDateTime currentDueDate = currentCard.isPresent() ? currentCard.get().getDueDate() : null;
                    
                    System.out.println("🔴 CHAMANDO FACADE.SETSCHEDULINGDATES() COM: cardId=" + cardData.id() + ", scheduledDate=null, dueDate=" + currentDueDate);
                    facade.setSchedulingDates(cardData.id(), null, currentDueDate);
                    System.out.println("✅ Data de agendamento removida e salva no banco (data de vencimento preservada)");
                    
                    // Atualizar a urgência e estilos
                    updateUrgencyDisplay();
                    updateTimeSpinnerStyles();
                    
                    // Notificar o callback para atualizar a UI
                    if (onSaveCallback != null) {
                        onSaveCallback.accept(cardData.id(), null);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Erro ao remover data de agendamento: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // Usuário cancelou - não fazer nada
                System.out.println("🔴 Remoção de data de agendamento cancelada pelo usuário");
            }
        } else {
            // Não há data salva - apenas limpar os campos
            scheduledDatePicker.setValue(null);
            scheduledHourSpinner.getValueFactory().setValue(12);
            scheduledMinuteSpinner.getValueFactory().setValue(0);
            updateUrgencyDisplay();
            updateTimeSpinnerStyles();
            System.out.println("Data de agendamento limpa (não havia data salva)");
        }
    }

    /**
     * Limpa o campo de data de vencimento.
     */
    @FXML
    private void handleClearDueDate() {
        System.out.println("=== LIMPANDO DATA DE VENCIMENTO ===");
        
        // Verificar se há uma data de vencimento salva no banco
        Optional<Card> currentCardOptional = facade.getCardById(cardData.id());
        boolean hasDueDate = currentCardOptional.isPresent() && 
                           currentCardOptional.get().getDueDate() != null;
        
        if (hasDueDate) {
            // Exibir confirmação antes de remover
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmar Remoção");
            alert.setHeaderText("Remover Data de Vencimento");
            alert.setContentText("Deseja realmente remover a data de vencimento?");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Usuário confirmou - limpar e salvar
                dueDatePicker.setValue(null);
                dueHourSpinner.getValueFactory().setValue(18);
                dueMinuteSpinner.getValueFactory().setValue(0);
                
                // Salvar a remoção no banco - preservar data de agendamento
                try {
                    // Obter a data de agendamento atual para preservá-la
                    Optional<Card> currentCard = facade.getCardById(cardData.id());
                    LocalDateTime currentScheduledDate = currentCard.isPresent() ? currentCard.get().getScheduledDate() : null;
                    
                    facade.setSchedulingDates(cardData.id(), currentScheduledDate, null);
                    System.out.println("Data de vencimento removida e salva no banco (data de agendamento preservada)");
                    
                    // Atualizar a urgência e estilos
                    updateUrgencyDisplay();
                    updateTimeSpinnerStyles();
                    
                    // Notificar o callback para atualizar a UI
                    if (onSaveCallback != null) {
                        onSaveCallback.accept(cardData.id(), null);
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao remover data de vencimento: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // Usuário cancelou - não fazer nada
                System.out.println("Remoção de data de vencimento cancelada pelo usuário");
            }
        } else {
            // Não há data salva - apenas limpar os campos
            dueDatePicker.setValue(null);
            dueHourSpinner.getValueFactory().setValue(18);
            dueMinuteSpinner.getValueFactory().setValue(0);
            updateUrgencyDisplay();
            updateTimeSpinnerStyles();
            System.out.println("Data de vencimento limpa (não havia data salva)");
        }
    }

    /**
     * Atualiza a exibição da urgência baseada nas datas inseridas.
     */
    private void updateUrgencyDisplay() {
        try {
            LocalDateTime scheduledDate = getScheduledDateTime();
            LocalDateTime dueDate = getDueDateTime();
            
            // Criar um card temporário para calcular a urgência
            if (cardData != null) {
                // Simular um card com as datas inseridas para calcular urgência
                int urgencyLevel = calculateUrgencyLevel(scheduledDate, dueDate);
                String urgencyText = getUrgencyText(urgencyLevel);
                urgencyLabel.setText(urgencyText);
                
                // Aplicar estilo baseado na urgência
                urgencyLabel.getStyleClass().clear();
                urgencyLabel.getStyleClass().add("urgency-value");
                urgencyLabel.getStyleClass().add("urgency-level-" + urgencyLevel);
            }
        } catch (Exception e) {
            urgencyLabel.setText("Data inválida");
            urgencyLabel.getStyleClass().clear();
            urgencyLabel.getStyleClass().add("urgency-value");
            urgencyLabel.getStyleClass().add("urgency-error");
        }
    }

    /**
     * Calcula o nível de urgência baseado nas datas.
     */
    private int calculateUrgencyLevel(LocalDateTime scheduledDate, LocalDateTime dueDate) {
        if (dueDate == null) {
            return 0; // Sem urgência se não há prazo
        }
        
        LocalDateTime now = LocalDateTime.now();
        long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), dueDate.toLocalDate());
        
        if (daysUntilDue < 0) {
            return 4; // Crítica - vencido
        } else if (daysUntilDue == 0) {
            return 3; // Alta - vence hoje
        } else if (daysUntilDue <= 1) {
            return 2; // Média - vence em 1 dia
        } else if (daysUntilDue <= 3) {
            return 1; // Baixa - vence em 2-3 dias
        }
        
        return 0; // Sem urgência
    }

    /**
     * Obtém o texto de urgência baseado no nível.
     */
    private String getUrgencyText(int urgencyLevel) {
        return switch (urgencyLevel) {
            case 4 -> "CRÍTICA - Vencido";
            case 3 -> "ALTA - Vence hoje";
            case 2 -> "MÉDIA - Vence em 1 dia";
            case 1 -> "BAIXA - Vence em 2-3 dias";
            default -> "Sem urgência";
        };
    }



    private void showAlert(String title, String content, Alert.AlertType alertType) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
    
    /**
     * Escreve uma mensagem de log no arquivo de debug.
     * 
     * @param message mensagem a ser logada
     */
    private void logToFile(String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(org.desviante.util.DataDirectoryPreflight.dataDir() + "/card_repository_debug.log", true))) {
            writer.println("[" + LocalDateTime.now() + "] " + message);
            writer.flush();
        } catch (IOException e) {
            // Se não conseguir escrever no arquivo, pelo menos imprimir no console
            System.err.println("Erro ao escrever log: " + e.getMessage());
        }
    }
}