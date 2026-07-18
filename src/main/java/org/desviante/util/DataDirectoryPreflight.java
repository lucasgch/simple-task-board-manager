package org.desviante.util;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Verificação pré-inicialização (pre-flight) do diretório de dados da aplicação.
 *
 * <p>Deve ser executada no {@code main()}, <strong>antes</strong> de o contexto
 * Spring subir e abrir o banco H2. Sem esta verificação, um diretório de dados
 * inacessível derruba a inicialização do Spring antes do JavaFX ser lançado e o
 * aplicativo "não abre" sem nenhuma mensagem ao usuário.</p>
 *
 * <p>Comportamento:</p>
 * <ul>
 *   <li>Resolve o diretório efetivo de dados: override escolhido pelo usuário
 *       (persistido via {@link Preferences}, que não depende do próprio diretório)
 *       ou o padrão {@code ~/myboards};</li>
 *   <li>Testa escrita real (criação de arquivo de sondagem — {@code Files.isWritable}
 *       não é confiável em todos os filesystems);</li>
 *   <li>Em caso de falha, exibe um diálogo Swing (o JavaFX ainda não subiu)
 *       permitindo escolher outra pasta, tentar novamente ou sair;</li>
 *   <li>Publica o resultado na propriedade de sistema {@value #DATA_DIR_PROPERTY},
 *       consumida por {@code application.properties} (URL do datasource) e pelas
 *       demais classes via {@link #dataDir()}.</li>
 * </ul>
 */
public final class DataDirectoryPreflight {

    /**
     * Propriedade de sistema com o diretório de dados efetivo da aplicação.
     */
    public static final String DATA_DIR_PROPERTY = "app.data.dir";

    private static final String PREF_KEY_DATA_DIR = "dataDir";
    private static final Logger logger = Logger.getLogger(DataDirectoryPreflight.class.getName());

    private DataDirectoryPreflight() {
        // Classe utilitária estática
    }

    /**
     * Retorna o diretório de dados efetivo da aplicação.
     *
     * <p>Após o pre-flight, reflete a pasta validada (padrão ou escolhida pelo
     * usuário). Se chamado sem o pre-flight ter rodado (ex.: testes), retorna
     * o padrão {@code ~/myboards}.</p>
     *
     * @return caminho do diretório de dados, sem barra final
     */
    public static String dataDir() {
        return System.getProperty(DATA_DIR_PROPERTY, defaultDataDir());
    }

    /**
     * Garante um diretório de dados gravável antes da inicialização do Spring/H2.
     *
     * <p>Bloqueia até obter um diretório válido ou encerra a JVM se o usuário
     * desistir (ou se o ambiente for headless e o diretório estiver inacessível).</p>
     *
     * @return diretório de dados validado
     */
    public static Path ensureWritableDataDirectory() {
        Preferences prefs = Preferences.userNodeForPackage(DataDirectoryPreflight.class);
        String override = prefs.get(PREF_KEY_DATA_DIR, null);
        Path candidate = Paths.get(override != null ? override : defaultDataDir());
        boolean usingOverride = override != null;

        while (!isWritableDirectory(candidate)) {
            logger.severe("Diretório de dados inacessível ou sem permissão de escrita: " + candidate);

            if (GraphicsEnvironment.isHeadless()) {
                logger.severe("Ambiente sem interface gráfica — encerrando. "
                        + "Defina -D" + DATA_DIR_PROPERTY + "=<pasta> para usar outro diretório.");
                System.exit(1);
            }

            int choice = showFailureDialog(candidate, usingOverride);

            if (choice == 0) { // Escolher outra pasta...
                Path chosen = chooseDirectory(candidate);
                if (chosen != null) {
                    candidate = chosen;
                    usingOverride = true;
                }
                // null = usuário cancelou o seletor; volta ao diálogo
            } else if (choice == 1) { // Tentar novamente
                // Reavalia o mesmo diretório (ex.: usuário corrigiu permissões ou plugou o drive)
            } else if (usingOverride && choice == 2) { // Restaurar pasta padrão
                candidate = Paths.get(defaultDataDir());
                usingOverride = false;
            } else { // Sair (ou diálogo fechado)
                logger.info("Usuário optou por encerrar o aplicativo no pre-flight do diretório de dados");
                System.exit(1);
            }
        }

        // Persiste a escolha do usuário e publica o diretório efetivo
        String resolved = candidate.toAbsolutePath().toString();
        try {
            if (usingOverride) {
                prefs.put(PREF_KEY_DATA_DIR, resolved);
            } else {
                prefs.remove(PREF_KEY_DATA_DIR);
            }
            prefs.flush();
        } catch (Exception e) {
            // Não é fatal: o override vale para esta execução; sem persistência,
            // o diálogo voltará a aparecer na próxima abertura.
            logger.warning("Não foi possível persistir a pasta de dados escolhida: " + e.getMessage());
        }

        System.setProperty(DATA_DIR_PROPERTY, resolved);
        logger.info("Diretório de dados validado: " + resolved);
        return candidate;
    }

    private static String defaultDataDir() {
        return System.getProperty("user.home") + "/myboards";
    }

    /**
     * Testa se o diretório existe (criando-o se necessário) e aceita escrita real.
     */
    private static boolean isWritableDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            Path probe = dir.resolve(".write-check-" + UUID.randomUUID());
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception e) {
            logger.warning("Teste de escrita falhou em " + dir + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Exibe o diálogo de falha e retorna o índice da opção escolhida
     * (ordem do array de opções; -1 se o diálogo for fechado).
     */
    private static int showFailureDialog(Path candidate, boolean usingOverride) {
        List<String> options = new ArrayList<>(List.of("Escolher outra pasta...", "Tentar novamente"));
        if (usingOverride) {
            options.add("Restaurar pasta padrão");
        }
        options.add("Sair");

        String message = "A pasta de dados do aplicativo não está acessível ou não tem permissão de escrita:\n\n"
                + candidate.toAbsolutePath() + "\n\n"
                + "O Simple Task Board Manager precisa de uma pasta gravável para armazenar seus boards.\n"
                + "O que deseja fazer?";

        return runOnEdt(() -> JOptionPane.showOptionDialog(
                null,
                message,
                "Simple Task Board Manager — Pasta de dados inacessível",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options.toArray(),
                options.get(0)));
    }

    /**
     * Abre o seletor de diretórios.
     *
     * @return pasta escolhida, ou null se o usuário cancelou
     */
    private static Path chooseDirectory(Path current) {
        return runOnEdt(() -> {
            JFileChooser chooser = new JFileChooser(System.getProperty("user.home"));
            chooser.setDialogTitle("Escolha a pasta de dados do Simple Task Board Manager");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);
            if (chooser.showDialog(null, "Usar esta pasta") == JFileChooser.APPROVE_OPTION
                    && chooser.getSelectedFile() != null) {
                return chooser.getSelectedFile().toPath();
            }
            return null;
        });
    }

    /**
     * Executa uma ação na Event Dispatch Thread do Swing e retorna seu resultado.
     */
    private static <T> T runOnEdt(java.util.function.Supplier<T> action) {
        if (SwingUtilities.isEventDispatchThread()) {
            return action.get();
        }
        AtomicReference<T> result = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> result.set(action.get()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pre-flight interrompido", e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IllegalStateException("Erro no diálogo de pre-flight", e.getCause());
        }
        return result.get();
    }
}
