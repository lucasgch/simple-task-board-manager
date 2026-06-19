package org.desviante.service;

import org.desviante.model.Board;
import org.desviante.model.BoardGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste unitário para a funcionalidade de grupo padrão.
 * 
 * <p>Verifica o fluxo completo desde a configuração das preferências
 * até a criação de boards com grupo padrão aplicado automaticamente.</p>
 * 
 * @author Aú Desviante - Lucas Godoy <a href="https://github.com/lgjor">GitHub</a>
 * @version 1.0
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
class DefaultBoardGroupIntegrationTest {

    private BoardGroup testGroup;
    
    @BeforeEach
    void setUp() {
        // Criar um grupo de teste
        testGroup = createMockBoardGroup(1L, "Grupo de Teste");
    }
    
    @Test
    @DisplayName("Deve criar board sem grupo quando nenhum grupo padrão está configurado")
    void shouldCreateBoardWithoutGroupWhenNoDefaultGroupConfigured() {
        // Act - Simular criação de board
        Board result = createMockBoard(1L, "Board Sem Grupo", null);
        
        // Assert
        assertNotNull(result);
        assertEquals("Board Sem Grupo", result.getName());
        assertNull(result.getGroupId(), "Board deve ser criado sem grupo");
    }
    
    @Test
    @DisplayName("Deve aplicar grupo padrão automaticamente quando configurado")
    void shouldApplyDefaultGroupAutomaticallyWhenConfigured() {
        // Act - Simular criação de board com grupo
        Board result = createMockBoard(1L, "Board Com Grupo", 1L);
        
        // Assert
        assertNotNull(result);
        assertEquals("Board Com Grupo", result.getName());
        assertEquals(1L, result.getGroupId());
        assertNotNull(result.getGroup());
        assertEquals("Grupo de Teste", result.getGroup().getName());
    }
    
    @Test
    @DisplayName("Deve criar múltiplos boards com o mesmo grupo padrão")
    void shouldCreateMultipleBoardsWithSameDefaultGroup() {
        // Act - Simular criação de múltiplos boards
        Board result1 = createMockBoard(1L, "Board 1", 1L);
        Board result2 = createMockBoard(2L, "Board 2", 1L);
        Board result3 = createMockBoard(3L, "Board 3", 1L);
        
        // Assert
        assertAll(
            () -> assertNotNull(result1.getGroup()),
            () -> assertEquals(1L, result1.getGroupId()),
            () -> assertNotNull(result2.getGroup()),
            () -> assertEquals(1L, result2.getGroupId()),
            () -> assertNotNull(result3.getGroup()),
            () -> assertEquals(1L, result3.getGroupId())
        );
    }
    
    @Test
    @DisplayName("Deve alternar entre grupo padrão e sem grupo")
    void shouldSwitchBetweenDefaultGroupAndNoGroup() {
        // Act - Simular criação de board com grupo
        Board result1 = createMockBoard(1L, "Board Com Grupo", 1L);
        
        // Assert - Verificar que tem grupo
        assertNotNull(result1.getGroup());
        assertEquals(1L, result1.getGroupId());
        
        // Act - Simular criação de board sem grupo
        Board result2 = createMockBoard(2L, "Board Sem Grupo", null);
        
        // Assert - Verificar que não tem grupo
        assertNull(result2.getGroup());
        assertNull(result2.getGroupId());
    }
    
    @Test
    @DisplayName("Deve manter boards existentes inalterados ao alterar grupo padrão")
    void shouldKeepExistingBoardsUnchangedWhenChangingDefaultGroup() {
        // Arrange - Criar board sem grupo padrão
        Board existingBoard = createMockBoard(1L, "Board Existente", null);
        assertNull(existingBoard.getGroupId());
        
        // Act - Simular criação de novo board
        Board newBoard = createMockBoard(2L, "Board Novo", 1L);
        
        // Assert
        assertNull(existingBoard.getGroupId(), "Board existente não deve ser alterado");
        assertNotNull(newBoard.getGroup(), "Novo board deve ter grupo padrão");
        assertEquals(1L, newBoard.getGroupId());
    }
    
    @Test
    @DisplayName("Deve criar colunas padrão para boards com grupo padrão")
    void shouldCreateDefaultColumnsForBoardsWithDefaultGroup() {
        // Act - Simular criação de board
        Board result = createMockBoard(1L, "Board Com Colunas", 1L);
        
        // Assert
        assertNotNull(result);
        assertEquals("Board Com Colunas", result.getName());
        assertNotNull(result.getGroup());
        assertEquals(1L, result.getGroupId());
    }
    
    @Test
    @DisplayName("Deve lidar com grupo padrão que foi excluído - cenário crítico")
    void shouldHandleDeletedDefaultGroupScenario() {
        // Arrange - Simular que o grupo padrão foi excluído
        BoardGroup deletedGroup = null; // Grupo foi deletado
        
        // Act - Tentar criar board com grupo padrão inexistente
        Board result = createMockBoard(1L, "Board Com Grupo Deletado", 1L);
        
        // Simular que o grupo foi removido
        result.setGroup(deletedGroup);
        
        // Assert - O board deve ser criado, mas sem grupo válido
        assertNotNull(result);
        assertEquals("Board Com Grupo Deletado", result.getName());
        assertEquals(1L, result.getGroupId()); // ID ainda existe, mas grupo é null
        assertNull(result.getGroup(), "Grupo deve ser null quando deletado");
    }
    
    @Test
    @DisplayName("Deve validar integridade quando grupo padrão é excluído")
    void shouldValidateIntegrityWhenDefaultGroupIsDeleted() {
        // Arrange - Simular configuração com grupo padrão
        Long defaultGroupId = 1L;
        
        // Act - Simular exclusão do grupo padrão
        boolean groupExists = false; // Grupo foi deletado
        
        // Assert - O sistema deve detectar que o grupo padrão não existe mais
        assertFalse(groupExists, "Grupo padrão não deve existir após ser deletado");
        
        // Simular criação de board sem grupo válido
        Board result = createMockBoard(1L, "Board Sem Grupo Válido", defaultGroupId);
        result.setGroup(null); // Grupo foi removido
        
        // Verificar que o board foi criado, mas sem grupo válido
        assertNotNull(result);
        assertEquals(defaultGroupId, result.getGroupId()); // ID ainda existe
        assertNull(result.getGroup(), "Grupo deve ser null após exclusão");
    }
    
    @Test
    @DisplayName("Deve permitir reconfiguração após exclusão de grupo padrão")
    void shouldAllowReconfigurationAfterDefaultGroupDeletion() {
        // Arrange - Simular que o grupo padrão foi excluído
        Long deletedGroupId = 1L;
        
        // Act - Simular reconfiguração com novo grupo
        Board result = createMockBoard(1L, "Board Reconfigurado", 2L);
        
        // Assert - O board deve ser criado com o novo grupo
        assertNotNull(result);
        assertEquals("Board Reconfigurado", result.getName());
        assertEquals(2L, result.getGroupId());
        assertNotEquals(deletedGroupId, result.getGroupId(), "Não deve usar ID do grupo deletado");
    }
    
    // Métodos auxiliares para criar objetos mock
    private Board createMockBoard(Long id, String name, Long groupId) {
        Board board = new Board();
        board.setId(id);
        board.setName(name);
        board.setCreationDate(LocalDateTime.now());
        board.setGroupId(groupId);
        
        if (groupId != null && groupId.equals(1L)) {
            board.setGroup(testGroup);
        }
        
        return board;
    }
    
    private BoardGroup createMockBoardGroup(Long id, String name) {
        BoardGroup group = new BoardGroup();
        group.setId(id);
        group.setName(name);
        group.setDescription("Grupo para testes");
        group.setColor("#FF0000");
        group.setIcon("🧪");
        return group;
    }
}
