package org.desviante.view.component;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.desviante.model.Field;
import org.desviante.model.PercentageField;
import org.desviante.model.enums.FieldType;
import org.desviante.service.FieldService;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class FieldViewController {

    @FXML private VBox fieldContainer;
    @FXML private Label progressLabel;
    @FXML private VBox fieldsContainer;
    @FXML private TextField labelTextField;
    @FXML private TextField totalField;
    @FXML private TextField descriptionTextField;
    @FXML private Button addFieldButton;
    @FXML private Button removeAllButton;

    private FieldService fieldService;
    private Long currentCardId;
    private List<Field> currentFields;
    private Runnable onProgressChanged;

    public FieldViewController() {}

    public void setOnProgressChanged(Runnable callback) {
        this.onProgressChanged = callback;
    }

    public void initialize(FieldService fieldService) {
        this.fieldService = fieldService;
        totalField.setText("1");
        totalField.setTextFormatter(new TextFormatter<>(numericFilter()));
        totalField.setTooltip(new Tooltip("Valor total do campo de progresso. Mínimo: 1."));
        setupEventHandlers();
    }

    /**
     * Filtro de {@link TextFormatter} que só aceita dígitos (até 6 casas) ou texto vazio,
     * usado para transformar os campos de total/atual em entradas puramente numéricas.
     *
     * <p>Substitui os antigos {@code Spinner}s: digitar um número não dispara nenhum
     * mecanismo de auto-repetição, então não há risco do valor "disparar sozinho" caso
     * um Alert modal (ex.: notificação de início/conclusão de progresso) roube o foco
     * no meio da digitação — o pior cenário é apenas a perda de foco do campo, que é
     * um evento único e idempotente.</p>
     */
    private static UnaryOperator<TextFormatter.Change> numericFilter() {
        return change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty() || newText.matches("\\d{0,6}")) {
                return change;
            }
            return null;
        };
    }

    /**
     * Faz o parse de um campo numérico de texto, retornando {@code fallback} caso o
     * texto esteja vazio ou não seja um inteiro válido.
     */
    private static int parseIntOrDefault(String text, int fallback) {
        if (text == null || text.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void setupEventHandlers() {
        addFieldButton.setOnAction(e -> addNewField());
        labelTextField.setOnAction(e -> addNewField());
        removeAllButton.setOnAction(e -> removeAllFields());
    }

    public void loadFields(Long cardId) {
        this.currentCardId = cardId;
        refreshFields();
    }

    private void refreshFields() {
        if (currentCardId == null) return;
        try {
            currentFields = fieldService.getFieldsByCardIdAndType(currentCardId, FieldType.PERCENTAGE);
            updateFieldsDisplay();
            updateProgressDisplay();
            if (onProgressChanged != null) onProgressChanged.run();
        } catch (Exception e) {
            showError("Erro ao carregar campos", e.getMessage());
        }
    }

    private void updateFieldsDisplay() {
        fieldsContainer.getChildren().clear();
        if (currentFields == null) return;
        for (Field field : currentFields) {
            if (field instanceof PercentageField pf) {
                fieldsContainer.getChildren().add(createFieldCard(pf));
            }
        }
    }

    private VBox createFieldCard(PercentageField field) {
        VBox card = new VBox(6);
        card.getStyleClass().add("field-card");
        card.setPadding(new Insets(8));

        // --- Title: label (display) + TextField (edit) ---
        Label titleLabel = new Label(field.getLabel());
        titleLabel.getStyleClass().add("field-card-title");
        titleLabel.setWrapText(true);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        TextField titleField = new TextField(field.getLabel() != null ? field.getLabel() : "");
        titleField.setPromptText("Título do campo");
        titleField.setVisible(false);
        titleField.setManaged(false);
        HBox.setHgrow(titleField, Priority.ALWAYS);

        Button deleteBtn = new Button("X");
        deleteBtn.getStyleClass().addAll("checklist-item-button", "checklist-remove-button");
        deleteBtn.setOnAction(e -> deleteField(field));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(titleLabel, titleField, deleteBtn);
        card.getChildren().add(header);

        // --- Description: label (display) + TextField (edit) ---
        String desc = field.getDescription();
        boolean hasDesc = desc != null && !desc.isBlank();

        Label descLabel = new Label(hasDesc ? desc : "");
        descLabel.getStyleClass().add("field-card-unit");
        descLabel.setVisible(hasDesc);
        descLabel.setManaged(hasDesc);

        TextField descField = new TextField(hasDesc ? desc : "");
        descField.setPromptText("Descrição (opcional)");
        descField.setVisible(false);
        descField.setManaged(false);
        descField.getStyleClass().add("new-item-input");

        card.getChildren().addAll(descLabel, descField);

        // --- Progress label, spinners ---
        Label fieldProgressLabel = new Label(String.format("%.1f%%", field.getProgressPercentage()));
        fieldProgressLabel.getStyleClass().add("field-card-progress");

        int totalVal = field.getTotal() != null && field.getTotal() > 0 ? field.getTotal() : 1;
        int currentVal = field.getCurrent() != null ? Math.min(field.getCurrent(), totalVal) : 0;

        // Campos numéricos simples em vez de Spinner<Integer>. Spinners têm botões de
        // seta com auto-repeat (um Timeline de ciclo indefinido no JavaFX) que só para
        // quando o próprio Spinner recebe o MOUSE_RELEASED; se um Alert modal (notificação
        // de início/conclusão de progresso) abrir no meio do clique-e-segure, esse evento
        // é roubado pelo Alert e o auto-repeat nunca é interrompido, fazendo o valor
        // "disparar sozinho". Um TextField comum não tem nenhum mecanismo de repetição:
        // cada dígito é um evento único e discreto, então esse tipo de bug deixa de ser
        // estruturalmente possível.
        TextField totalInput = new TextField(String.valueOf(totalVal));
        totalInput.setPromptText("Total");
        totalInput.setPrefWidth(70);
        totalInput.setTextFormatter(new TextFormatter<>(numericFilter()));

        TextField currentInput = new TextField(String.valueOf(currentVal));
        currentInput.setPromptText("Atual");
        currentInput.setPrefWidth(70);
        currentInput.setTextFormatter(new TextFormatter<>(numericFilter()));

        HBox totalRow = new HBox(10);
        totalRow.setAlignment(Pos.CENTER_LEFT);
        Label totalLbl = new Label("Total:");
        totalLbl.getStyleClass().add("progress-label");
        totalRow.getChildren().addAll(totalLbl, totalInput);

        HBox currentRow = new HBox(10);
        currentRow.setAlignment(Pos.CENTER_LEFT);
        Label currentLbl = new Label("Atual:");
        currentLbl.getStyleClass().add("progress-label");
        currentRow.getChildren().addAll(currentLbl, currentInput);

        // Atualiza o campo em memória e o label a cada tecla digitada (feedback visual
        // instantâneo). A persistência no banco (e a consequente verificação/publicação
        // de eventos de progresso) só acontece quando o campo perde o foco ou quando o
        // usuário pressiona Enter — nunca a cada tecla — evitando escritas excessivas no
        // banco sem depender de nenhum mecanismo de auto-repeat que possa travar.
        totalInput.textProperty().addListener((obs, oldVal, newVal) -> {
            int total = Math.max(1, parseIntOrDefault(newVal, field.getTotal() != null ? field.getTotal() : 1));
            field.setTotal(total);
            int current = parseIntOrDefault(currentInput.getText(), field.getCurrent() != null ? field.getCurrent() : 0);
            if (current > total) {
                current = total;
                currentInput.setText(String.valueOf(current));
            }
            field.setCurrent(current);
            fieldProgressLabel.setText(String.format("%.1f%%", field.getProgressPercentage()));
        });

        currentInput.textProperty().addListener((obs, oldVal, newVal) -> {
            int total = Math.max(1, parseIntOrDefault(totalInput.getText(), field.getTotal() != null ? field.getTotal() : 1));
            int current = parseIntOrDefault(newVal, field.getCurrent() != null ? field.getCurrent() : 0);
            if (current > total) {
                current = total;
                currentInput.setText(String.valueOf(current));
                return; // o setText acima já vai re-disparar este listener com o valor já clamped
            }
            field.setCurrent(current);
            fieldProgressLabel.setText(String.format("%.1f%%", field.getProgressPercentage()));
        });

        // Persiste ao perder o foco...
        totalInput.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) {
                if (totalInput.getText() == null || totalInput.getText().isBlank()) {
                    totalInput.setText(String.valueOf(field.getTotal() != null ? field.getTotal() : 1));
                }
                persistField(field);
            }
        });
        currentInput.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) {
                if (currentInput.getText() == null || currentInput.getText().isBlank()) {
                    currentInput.setText(String.valueOf(field.getCurrent() != null ? field.getCurrent() : 0));
                }
                persistField(field);
            }
        });
        // ...e também ao pressionar Enter, cobrindo o caso em que o usuário digita um
        // valor e nunca chega a clicar em outro lugar (ex.: fecha o card em seguida) —
        // sem isso a mudança poderia nunca ser persistida nem disparar a notificação.
        totalInput.setOnAction(e -> persistField(field));
        currentInput.setOnAction(e -> persistField(field));

        HBox progressRow = new HBox(5);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        Label progressLbl = new Label("Progresso:");
        progressLbl.getStyleClass().add("progress-label");
        progressRow.getChildren().addAll(progressLbl, fieldProgressLabel);

        card.getChildren().addAll(totalRow, currentRow, progressRow);

        // --- Inline edit logic ---
        boolean[] isEditing = {false};

        Runnable startEdit = () -> {
            if (isEditing[0]) return;
            isEditing[0] = true;
            titleLabel.setVisible(false);
            titleLabel.setManaged(false);
            titleField.setText(field.getLabel() != null ? field.getLabel() : "");
            titleField.setVisible(true);
            titleField.setManaged(true);
            descLabel.setVisible(false);
            descLabel.setManaged(false);
            descField.setText(field.getDescription() != null ? field.getDescription() : "");
            descField.setVisible(true);
            descField.setManaged(true);
            titleField.requestFocus();
            titleField.selectAll();
        };

        Runnable commitEdit = () -> {
            if (!isEditing[0]) return;
            isEditing[0] = false;
            String newTitle = titleField.getText().trim();
            if (!newTitle.isEmpty()) {
                field.setLabel(newTitle);
                field.setDescription(descField.getText().trim());
                persistField(field);
            }
            titleField.setVisible(false);
            titleField.setManaged(false);
            titleLabel.setText(field.getLabel() != null ? field.getLabel() : "");
            titleLabel.setVisible(true);
            titleLabel.setManaged(true);
            descField.setVisible(false);
            descField.setManaged(false);
            String newDesc = field.getDescription();
            boolean showDesc = newDesc != null && !newDesc.isBlank();
            descLabel.setText(showDesc ? newDesc : "");
            descLabel.setVisible(showDesc);
            descLabel.setManaged(showDesc);
        };

        Runnable cancelEdit = () -> {
            if (!isEditing[0]) return;
            isEditing[0] = false;
            titleField.setVisible(false);
            titleField.setManaged(false);
            titleLabel.setVisible(true);
            titleLabel.setManaged(true);
            descField.setVisible(false);
            descField.setManaged(false);
            String currentDesc = field.getDescription();
            boolean showDesc = currentDesc != null && !currentDesc.isBlank();
            descLabel.setVisible(showDesc);
            descLabel.setManaged(showDesc);
        };

        // Double-click activates inline edit; consume prevents card from opening
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                startEdit.run();
                e.consume();
            }
        });

        // Enter in title → move to description; Enter in description → commit
        titleField.setOnAction(e -> descField.requestFocus());
        descField.setOnAction(e -> commitEdit.run());

        // Escape cancels
        titleField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) cancelEdit.run(); });
        descField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) cancelEdit.run(); });

        // Focus loss: commit when focus leaves both fields
        titleField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused && isEditing[0]) {
                Platform.runLater(() -> { if (!descField.isFocused()) commitEdit.run(); });
            }
        });
        descField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused && isEditing[0]) {
                Platform.runLater(() -> { if (!titleField.isFocused()) commitEdit.run(); });
            }
        });

        return card;
    }

    private void persistField(PercentageField field) {
        try {
            fieldService.updateField(field);
            updateProgressDisplay();
            if (onProgressChanged != null) onProgressChanged.run();
        } catch (Exception e) {
            showError("Erro ao atualizar campo", e.getMessage());
        }
    }

    private void deleteField(PercentageField field) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Remoção");
        alert.setHeaderText("Remover Campo");
        alert.setContentText("Deseja remover o campo '" + field.getLabel() + "'?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                fieldService.deleteField(field.getId());
                refreshFields();
            } catch (Exception e) {
                showError("Erro ao remover campo", e.getMessage());
            }
        }
    }

    private void addNewField() {
        if (currentCardId == null) {
            showError("Card não salvo", "Salve o card antes de adicionar campos.");
            return;
        }
        String label = labelTextField.getText().trim();
        if (label.isEmpty()) {
            showError("Campo inválido", "O nome do campo não pode estar vazio.");
            return;
        }
        int total = Math.max(1, parseIntOrDefault(totalField.getText(), 1));
        String description = descriptionTextField.getText().trim();

        try {
            int nextOrder = currentFields != null ? currentFields.size() : 0;
            fieldService.createPercentageField(currentCardId, label, total, description, nextOrder);
            labelTextField.clear();
            descriptionTextField.clear();
            totalField.setText("1");
            refreshFields();
        } catch (Exception e) {
            showError("Erro ao adicionar campo", e.getMessage());
        }
    }

    private void removeAllFields() {
        if (currentCardId == null || currentFields == null || currentFields.isEmpty()) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Remoção");
        alert.setHeaderText("Remover Todos os Campos");
        alert.setContentText("Deseja remover todos os campos de progresso?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                for (Field f : currentFields) {
                    fieldService.deleteField(f.getId());
                }
                refreshFields();
            } catch (Exception e) {
                showError("Erro ao remover campos", e.getMessage());
            }
        }
    }

    private void updateProgressDisplay() {
        if (currentFields == null || currentFields.isEmpty()) {
            progressLabel.setText("0.0%");
            return;
        }
        double avg = currentFields.stream()
                .mapToDouble(Field::getProgressPercentage)
                .average()
                .orElse(0.0);
        progressLabel.setText(String.format("%.1f%%", avg));
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void promptAddField() {
        if (currentCardId == null) {
            showError("Card não salvo", "Salve o card antes de adicionar campos.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Adicionar Campo Percentual");
        dialog.setHeaderText("Novo Campo de Progresso Percentual");
        dialog.setContentText("Nome do campo:");
        dialog.showAndWait().ifPresent(label -> {
            if (!label.trim().isEmpty()) {
                try {
                    int nextOrder = currentFields != null ? currentFields.size() : 0;
                    fieldService.createPercentageField(currentCardId, label.trim(), 100, "", nextOrder);
                    refreshFields();
                } catch (Exception e) {
                    showError("Erro ao adicionar campo", e.getMessage());
                }
            }
        });
    }

    public VBox getFieldContainer() {
        return fieldContainer;
    }

    public boolean hasFields() {
        return currentFields != null && !currentFields.isEmpty();
    }
}
