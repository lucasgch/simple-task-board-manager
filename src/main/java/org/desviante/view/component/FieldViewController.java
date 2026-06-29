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

public class FieldViewController {

    @FXML private VBox fieldContainer;
    @FXML private Label progressLabel;
    @FXML private VBox fieldsContainer;
    @FXML private TextField labelTextField;
    @FXML private Spinner<Integer> totalSpinner;
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
        totalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 1));
        totalSpinner.setEditable(true);
        setupEventHandlers();
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

        Spinner<Integer> totalSpin = new Spinner<>(1, 9999, totalVal);
        totalSpin.setEditable(true);
        totalSpin.setPrefWidth(90);

        Spinner<Integer> currentSpin = new Spinner<>(0, totalVal, currentVal);
        currentSpin.setEditable(true);
        currentSpin.setPrefWidth(90);

        HBox totalRow = new HBox(10);
        totalRow.setAlignment(Pos.CENTER_LEFT);
        Label totalLbl = new Label("Total:");
        totalLbl.getStyleClass().add("progress-label");
        totalRow.getChildren().addAll(totalLbl, totalSpin);

        HBox currentRow = new HBox(10);
        currentRow.setAlignment(Pos.CENTER_LEFT);
        Label currentLbl = new Label("Atual:");
        currentLbl.getStyleClass().add("progress-label");
        currentRow.getChildren().addAll(currentLbl, currentSpin);

        totalSpin.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            field.setTotal(newVal);
            int clamped = Math.min(currentSpin.getValue(), newVal);
            currentSpin.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, newVal, clamped));
            field.setCurrent(clamped);
            fieldProgressLabel.setText(String.format("%.1f%%", field.getProgressPercentage()));
            persistField(field);
        });

        currentSpin.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            field.setCurrent(newVal);
            fieldProgressLabel.setText(String.format("%.1f%%", field.getProgressPercentage()));
            persistField(field);
        });

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
        int total = totalSpinner.getValue();
        String description = descriptionTextField.getText().trim();

        try {
            int nextOrder = currentFields != null ? currentFields.size() : 0;
            fieldService.createPercentageField(currentCardId, label, total, description, nextOrder);
            labelTextField.clear();
            descriptionTextField.clear();
            totalSpinner.getValueFactory().setValue(1);
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
