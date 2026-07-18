-- Migration: Rename percentage_unit to percentage_description in fields table
-- Author: Aú Desviante - Lucas Godoy
-- Date: 2026-06-29
-- Reason: "unit" was misleading; the field stores a freeform description shown below the field title

ALTER TABLE fields RENAME COLUMN percentage_unit TO percentage_description;
