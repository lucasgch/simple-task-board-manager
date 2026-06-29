-- Migration: Add fields table for generic field system
-- Author: Aú Desviante - Lucas Godoy
-- Date: 2026-06-28
-- Compatible with H2 Database

-- Create fields table with polymorphic structure
CREATE TABLE IF NOT EXISTS fields (
    -- Common fields for all field types
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id BIGINT NOT NULL,
    field_type VARCHAR(50) NOT NULL,  -- CHECKLIST_ITEM, PERCENTAGE
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Checklist-specific fields (NULL for other types)
    checklist_text TEXT,
    checklist_completed BOOLEAN DEFAULT FALSE,
    checklist_completed_at TIMESTAMP,

    -- Percentage-specific fields (NULL for other types)
    percentage_label VARCHAR(255),
    percentage_total INTEGER,
    percentage_current INTEGER DEFAULT 0,
    percentage_unit VARCHAR(50),

    -- Foreign key constraint
    CONSTRAINT fk_fields_cards FOREIGN KEY (card_id)
        REFERENCES cards(id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX idx_fields_card_id ON fields(card_id);
CREATE INDEX idx_fields_type ON fields(field_type);
CREATE INDEX idx_fields_order ON fields(card_id, order_index);

-- Note: Migration of existing data (checklist_items and card progress)
-- will be handled by the FieldMigration Java component during application startup
