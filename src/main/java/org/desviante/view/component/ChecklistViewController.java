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
import org.desviante.service.ChecklistItemService;
import org.desviante.service.dto.ChecklistItemDTO;

import java.util.List;

public class ChecklistViewController {

    @FXML private VBox checklistContainer;
    @FXML private VBox headerPane;
    @FXML private Label groupNameLabel;
    @FXML private TextField groupNameField;
    @FXML private Label groupDescLabel;
    @FXML private TextField groupDescField;
    @FXML private Label progressLabel;
    @FXML private VBox itemsContainer;
    @FXML private TextField newItemTextField;
    @FXML private Button addItemButton;
    @FXML private Button clearCompletedButton;
    @FXML private Button removeAllButton;

    private ChecklistItemService checklistItemService;
    private Long currentCardId;
    private Long groupId;
    private List<ChecklistItemDTO> currentItems;
    private Runnable onProgressChanged;
    private Runnable onGroupDeleted;
    private boolean[] isEditingHeader = {false};

    public ChecklistViewController() {}

    public void initialize(ChecklistItemService checklistItemService) {
        this.checklistItemService = checklistItemService;
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        addItemButton.setOnAction(e -> addNewItem());
        addItemButton.setTooltip(new Tooltip("Adicionar"));
        newItemTextField.setOnAction(e -> addNewItem());
        clearCompletedButton.setOnAction(e -> clearCompletedItems());
        removeAllButton.setOnAction(e -> removeGroup());
        checklistContainer.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) e.consume();
        });
        setupHeaderInlineEdit();
    }

    private void setupHeaderInlineEdit() {
        Runnable startEdit = () -> {
            if (isEditingHeader[0]) return;
            isEditingHeader[0] = true;

            groupNameLabel.setVisible(false); groupNameLabel.setManaged(false);
            groupNameField.setText(groupNameLabel.getText());
            groupNameField.setVisible(true); groupNameField.setManaged(true);

            groupDescLabel.setVisible(false); groupDescLabel.setManaged(false);
            groupDescField.setText(groupDescLabel.getText() != null ? groupDescLabel.getText() : "");
            groupDescField.setVisible(true); groupDescField.setManaged(true);

            Platform.runLater(() -> { groupNameField.requestFocus(); groupNameField.selectAll(); });
        };

        Runnable commitEdit = () -> {
            if (!isEditingHeader[0]) return;
            isEditingHeader[0] = false;

            String newName = groupNameField.getText().trim();
            String newDesc = groupDescField.getText().trim();

            groupNameField.setVisible(false); groupNameField.setManaged(false);
            groupDescField.setVisible(false); groupDescField.setManaged(false);
            groupNameLabel.setVisible(true); groupNameLabel.setManaged(true);
            groupDescLabel.setVisible(true); groupDescLabel.setManaged(true);

            if (newName.isEmpty()) return;
            try {
                checklistItemService.updateGroupNameAndDescription(groupId, newName, newDesc);
                groupNameLabel.setText(newName);
                groupDescLabel.setText(newDesc);
            } catch (Exception e) {
                showError("Erro ao salvar", e.getMessage());
            }
        };

        Runnable cancelEdit = () -> {
            if (!isEditingHeader[0]) return;
            isEditingHeader[0] = false;
            groupNameField.setVisible(false); groupNameField.setManaged(false);
            groupDescField.setVisible(false); groupDescField.setManaged(false);
            groupNameLabel.setVisible(true); groupNameLabel.setManaged(true);
            groupDescLabel.setVisible(true); groupDescLabel.setManaged(true);
        };

        headerPane.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) { startEdit.run(); e.consume(); }
        });

        groupNameField.setOnAction(e -> { groupDescField.requestFocus(); groupDescField.selectAll(); });
        groupNameField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) cancelEdit.run(); });
        groupNameField.focusedProperty().addListener((obs, was, is) -> {
            if (was && !is && isEditingHeader[0])
                Platform.runLater(() -> { if (!groupDescField.isFocused()) commitEdit.run(); });
        });

        groupDescField.setOnAction(e -> commitEdit.run());
        groupDescField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) cancelEdit.run(); });
        groupDescField.focusedProperty().addListener((obs, was, is) -> {
            if (was && !is && isEditingHeader[0])
                Platform.runLater(() -> { if (!groupNameField.isFocused()) commitEdit.run(); });
        });
    }

    public void setOnProgressChanged(Runnable callback) { this.onProgressChanged = callback; }
    public void setOnGroupDeleted(Runnable callback) { this.onGroupDeleted = callback; }

    public void loadGroup(Long cardId, Long groupId, String groupName) {
        this.currentCardId = cardId;
        this.groupId = groupId;
        if (groupNameLabel != null) groupNameLabel.setText(groupName != null ? groupName : "Checklist");
        // description starts empty; if the field exists it will be loaded via the service
        if (groupDescLabel != null) {
            String desc = checklistItemService.getGroupDescription(groupId);
            groupDescLabel.setText(desc != null ? desc : "");
        }
        refreshItems();
    }

    private void refreshItems() {
        if (groupId == null) return;
        try {
            currentItems = checklistItemService.getItemsByGroupId(groupId);
            updateItemsDisplay();
            updateProgressDisplay();
            if (onProgressChanged != null) onProgressChanged.run();
        } catch (Exception e) {
            showError("Erro ao carregar itens", e.getMessage());
        }
    }

    private void updateItemsDisplay() {
        itemsContainer.getChildren().clear();
        if (currentItems == null) return;
        for (ChecklistItemDTO item : currentItems) {
            itemsContainer.getChildren().add(createItemCard(item));
        }
    }

    private VBox createItemCard(ChecklistItemDTO item) {
        VBox card = new VBox(4);
        card.getStyleClass().add("field-card");
        card.setPadding(new Insets(8));

        Label titleLabel = new Label(item.text());
        titleLabel.getStyleClass().add("field-card-title");
        if (item.completed()) titleLabel.getStyleClass().add("completed");
        titleLabel.setWrapText(true);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        TextField titleField = new TextField(item.text() != null ? item.text() : "");
        titleField.setVisible(false);
        titleField.setManaged(false);
        HBox.setHgrow(titleField, Priority.ALWAYS);

        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(item.completed());
        checkBox.setOnAction(e -> toggleItemCompleted(item, checkBox.isSelected()));

        Button deleteBtn = new Button("X");
        deleteBtn.getStyleClass().addAll("checklist-item-button", "checklist-remove-button");
        deleteBtn.setOnAction(e -> removeItem(item));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(checkBox, titleLabel, titleField, deleteBtn);
        card.getChildren().add(header);

        boolean[] isEditing = {false};

        Runnable startEdit = () -> {
            if (isEditing[0]) return;
            isEditing[0] = true;
            titleLabel.setVisible(false); titleLabel.setManaged(false);
            titleField.setText(item.text() != null ? item.text() : "");
            titleField.setVisible(true); titleField.setManaged(true);
            titleField.requestFocus(); titleField.selectAll();
        };

        Runnable commitEdit = () -> {
            if (!isEditing[0]) return;
            isEditing[0] = false;
            String newText = titleField.getText().trim();
            titleField.setVisible(false); titleField.setManaged(false);
            titleLabel.setVisible(true); titleLabel.setManaged(true);
            if (!newText.isEmpty() && !newText.equals(item.text())) {
                try {
                    checklistItemService.updateItemText(item.id(), newText);
                    refreshItems();
                } catch (Exception e) {
                    showError("Erro ao editar item", e.getMessage());
                }
            }
        };

        Runnable cancelEdit = () -> {
            if (!isEditing[0]) return;
            isEditing[0] = false;
            titleField.setVisible(false); titleField.setManaged(false);
            titleLabel.setVisible(true); titleLabel.setManaged(true);
        };

        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) { startEdit.run(); e.consume(); }
        });
        titleField.setOnAction(e -> commitEdit.run());
        titleField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) cancelEdit.run(); });
        titleField.focusedProperty().addListener((obs, was, is) -> {
            if (was && !is && isEditing[0]) Platform.runLater(commitEdit::run);
        });

        return card;
    }

    private void addNewItem() {
        if (currentCardId == null || groupId == null) return;
        String text = newItemTextField.getText().trim();
        if (text.isEmpty()) return;
        try {
            checklistItemService.addItemToGroup(currentCardId, groupId, text);
            newItemTextField.clear();
            refreshItems();
        } catch (Exception e) {
            showError("Erro ao adicionar item", e.getMessage());
        }
    }

    private void toggleItemCompleted(ChecklistItemDTO item, boolean completed) {
        try {
            checklistItemService.toggleItemCompleted(item.id(), completed);
            refreshItems();
        } catch (Exception e) {
            showError("Erro ao atualizar item", e.getMessage());
        }
    }

    private void removeItem(ChecklistItemDTO item) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Remoção");
        alert.setHeaderText("Remover Item");
        alert.setContentText("Deseja remover '" + item.text() + "'?");
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    checklistItemService.removeItem(item.id());
                    refreshItems();
                } catch (Exception e) {
                    showError("Erro ao remover item", e.getMessage());
                }
            }
        });
    }

    private void clearCompletedItems() {
        if (currentItems == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Limpar Concluídos");
        alert.setHeaderText("Remover itens concluídos deste checklist?");
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    currentItems.stream().filter(ChecklistItemDTO::completed)
                        .forEach(item -> checklistItemService.removeItem(item.id()));
                    refreshItems();
                } catch (Exception e) {
                    showError("Erro ao limpar itens", e.getMessage());
                }
            }
        });
    }

    private void removeGroup() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Remover Checklist");
        alert.setHeaderText("Remover este checklist e todos os seus itens?");
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    checklistItemService.removeAllItemsFromGroup(groupId);
                    checklistItemService.removeItem(groupId); // remove the group record itself
                    if (onGroupDeleted != null) onGroupDeleted.run();
                } catch (Exception e) {
                    showError("Erro ao remover checklist", e.getMessage());
                }
            }
        });
    }

    private void updateProgressDisplay() {
        if (currentItems == null) { progressLabel.setText("0/0"); return; }
        long done = currentItems.stream().filter(ChecklistItemDTO::completed).count();
        progressLabel.setText(done + "/" + currentItems.size());
    }

    public void promptAddItem() {
        if (currentCardId == null || groupId == null) return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Adicionar Item");
        dialog.setHeaderText("Novo item em '" + groupNameLabel.getText() + "':");
        dialog.setContentText("Texto:");
        dialog.showAndWait().ifPresent(text -> {
            if (!text.trim().isEmpty()) {
                try {
                    checklistItemService.addItemToGroup(currentCardId, groupId, text.trim());
                    refreshItems();
                } catch (Exception e) {
                    showError("Erro ao adicionar item", e.getMessage());
                }
            }
        });
    }

    public VBox getChecklistContainer() { return checklistContainer; }
    public boolean hasItems() { return currentItems != null && !currentItems.isEmpty(); }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro"); alert.setHeaderText(title);
        alert.setContentText(message); alert.showAndWait();
    }
}
