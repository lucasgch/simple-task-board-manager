# Handoff → JavaFX (IntelliJ)

Este protótipo é HTML só para desenho. Abaixo o mapeamento para a sua codebase JavaFX.
Arquivos deste pacote: `app.css` (stylesheet), `CardCompacto.fxml` (estrutura do card).

## 1. Tradução de conceitos (web → JavaFX)

- `display:flex; gap` → `HBox`/`VBox` com `spacing=` e `Region` com `HBox.hgrow="ALWAYS"` como espaçador.
- `border-radius` → `-fx-background-radius` **e** `-fx-border-radius` (os dois).
- `box-shadow` → `-fx-effect: dropshadow(gaussian, cor, raio, 0, dx, dy);`
- `%` de largura não existe direto → use *binding* (ver barra de progresso).
- `:hover` funciona igual em CSS JavaFX.
- classes múltiplas: `styleClass="chip, chip-doing"`.

## 2. Barra de progresso segmentada / preenchida

Não use `ProgressBar` (visual fixo). Faça trilho + preenchimento e ligue a largura:

```java
// dentro do controller do card
double progresso = 0.419;                 // 0..1
barFill.prefHeightProperty().bind(barTrack.heightProperty());
barFill.prefWidthProperty().bind(barTrack.widthProperty().multiply(progresso));
```

Para a barra de 3 segmentos da tabela (não iniciado / andamento / concluído): um `HBox`
com três `Region` (`bar-fill-todo/doing/done`), cada `prefWidth` ligado à largura do HBox
multiplicada pela fração correspondente.

## 3. Edição inline no duplo clique (Label ⇄ TextField)

Padrão reutilizável — troca o `Label` por um `TextField` no duplo clique e volta no Enter/foco perdido:

```java
public static void tornarEditavel(Label label, java.util.function.Consumer<String> onCommit) {
    label.setOnMouseClicked(e -> {
        if (e.getClickCount() != 2) return;
        HBox parent = (HBox) label.getParent();          // ou o container real
        int idx = parent.getChildren().indexOf(label);

        TextField tf = new TextField(label.getText());
        tf.getStyleClass().add("inline-edit");
        parent.getChildren().set(idx, tf);
        tf.requestFocus();
        tf.selectAll();

        Runnable commit = () -> {
            label.setText(tf.getText());
            onCommit.accept(tf.getText());               // <- salve no banco aqui
            if (parent.getChildren().contains(tf))
                parent.getChildren().set(parent.getChildren().indexOf(tf), label);
        };
        tf.setOnAction(ev -> commit.run());              // Enter
        tf.focusedProperty().addListener((o, was, is) -> { if (!is) commit.run(); }); // perdeu foco
        tf.setOnKeyPressed(ev -> {                       // Esc cancela
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE)
                parent.getChildren().set(parent.getChildren().indexOf(tf), label);
        });
    });
}
```

Uso: `tornarEditavel(title, novo -> cardService.renomear(card.getId(), novo));`
Mesma ideia serve para os itens de checklist e os campos Total/Atual do percentual.

## 4. Expandir/colapsar detalhe do card

```java
@FXML private VBox detail;
@FXML private Button btnExpand;
@FXML private void toggleDetail() {
    boolean abrir = !detail.isVisible();
    detail.setVisible(abrir);
    detail.setManaged(abrir);        // managed=false remove do layout quando fechado -> card fica compacto
    btnExpand.setText(abrir ? "⌃" : "⌄");
}
```
O modo "Compacto/Detalhado" global é só um botão que percorre todos os cards e chama isso.

## 5. Onde encaixar na sua codebase

- `app.css` → `src/main/resources/app.css`; registre em cada `Scene`:
  `scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());`
- Uma coluna do kanban = `VBox` (dentro de `ScrollPane`) que recebe N `CardCompacto.fxml`.
- Troque suas cores/paddings atuais pelas classes daqui; comece pelo `.card` e pelos `.chip-*`,
  que são o que mais muda a percepção de "cheio".

## 6. Dica de Claude Code
Você pode abrir esta pasta `handoff/` no Claude Code dentro do IntelliJ e pedir para ele
aplicar `app.css` aos seus FXML existentes e refatorar o card atual para o modelo compacto —
passe o `app.css` e o `CardCompacto.fxml` como referência.
