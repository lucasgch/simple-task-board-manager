package org.desviante.view.component;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.desviante.model.Field;
import org.desviante.model.PercentageField;
import org.desviante.model.enums.FieldType;
import org.desviante.service.FieldService;

import java.util.List;
import java.util.Optional;

/**
 * Controlador para gerenciamento de campos de progresso percentual (PercentageField).
 *
 * <p>Segue o mesmo padrão de {@link ChecklistViewController}: inicialização manual
 * via {@code initialize(FieldService)}, carregamento via {@code loadFields(Long)}
 * e construção dinâmica das linhas de item.</p>
 */
public class FieldViewController {

    @FXML private VBox fieldContainer;
    @FXML private Label progressLabel;
    @FXML private VBox fieldsContainer;
    @FXML private TextField labelTextField;
    @FXML private Spinner<Integer> totalSpinner;
    @FXML private TextField unitTextField;
    @FXML private Button addFieldButton;
    @FXML private Button removeAllButton;

    private FieldService fieldService;
    private Long currentCardId;
    private List<Field> currentFields;

    public FieldViewController() {}

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
        } catch (Exception e) {
            showError("Erro ao carregar campos", e.getMessage());
        }
    }

    private void updateFieldsDisplay() {
        fieldsContainer.getChildren().clear();
        if (currentFields == null) return;
        for (Field field : currentFields) {
            if (field instanceof PercentageField pf) {
                fieldsContainer.getChildren().add(createFieldRow(pf));
            }
        }
    }

    private HBox createFieldRow(PercentageField field) {
        HBox row = new HBox(6);
        row.getStyleClass().add("checklist-item-box");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label infoLabel = new Label(field.getDisplayText());
        infoLabel.getStyleClass().add("checklist-item-label");
        infoLabel.setWrapText(false);
        infoLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(infoLabel, javafx.scene.layout.Priority.ALWAYS);

        Button decrementBtn = new Button("−");
        decrementBtn.getStyleClass().addAll("checklist-item-button");
        decrementBtn.setTooltip(new Tooltip("Decrementar"));
        decrementBtn.setOnAction(e -> adjustCurrent(field, -1));

        Button incrementBtn = new Button("+");
        incrementBtn.getStyleClass().addAll("checklist-item-button");
        incrementBtn.setTooltip(new Tooltip("Incrementar"));
        incrementBtn.setOnAction(e -> adjustCurrent(field, +1));

        Button editBtn = new Button("✏");
        editBtn.getStyleClass().addAll("checklist-item-button", "checklist-edit-button");
        editBtn.setTooltip(new Tooltip("Editar valor atual"));
        editBtn.setOnAction(e -> editCurrentValue(field));

        Button deleteBtn = new Button("X");
        deleteBtn.getStyleClass().addAll("checklist-item-button", "checklist-remove-button");
        deleteBtn.setTooltip(new Tooltip("Remover campo"));
        deleteBtn.setOnAction(e -> deleteField(field));

        row.getChildren().addAll(infoLabel, decrementBtn, incrementBtn, editBtn, deleteBtn);
        return row;
    }

    private void adjustCurrent(PercentageField field, int delta) {
        int newValue = Math.max(0, Math.min(field.getTotal(), field.getCurrent() + delta));
        field.setCurrent(newValue);
        try {
            fieldService.updateField(field);
            refreshFields();
        } catch (Exception e) {
            showError("Erro ao atualizar campo", e.getMessage());
        }
    }

    private void editCurrentValue(PercentageField field) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(field.getCurrent()));
        dialog.setTitle("Editar Valor Atual");
        dialog.setHeaderText("Campo: " + field.getLabel());
        dialog.setContentText(String.format("Valor atual (0 a %d):", field.getTotal()));

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(text -> {
            try {
                int value = Integer.parseInt(text.trim());
                if (value < 0 || value > field.getTotal()) {
                    showError("Valor inválido", "O valor deve estar entre 0 e " + field.getTotal());
                    return;
                }
                field.setCurrent(value);
                fieldService.updateField(field);
                refreshFields();
            } catch (NumberFormatException ex) {
                showError("Valor inválido", "Digite um número inteiro válido.");
            } catch (Exception ex) {
                showError("Erro ao atualizar campo", ex.getMessage());
            }
        });
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
        String unit = unitTextField.getText().trim();

        try {
            int nextOrder = currentFields != null ? currentFields.size() : 0;
            fieldService.createPercentageField(currentCardId, label, total, unit.isEmpty() ? "" : unit, nextOrder);
            labelTextField.clear();
            unitTextField.clear();
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

    public VBox getFieldContainer() {
        return fieldContainer;
    }

    public boolean hasFields() {
        return currentFields != null && !currentFields.isEmpty();
    }
}
