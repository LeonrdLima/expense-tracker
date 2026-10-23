-- This file allow to write SQL commands that will be emitted in test and dev.
INSERT INTO categories (id, name) VALUES (nextval('categories_seq'), 'Alimentação') ON CONFLICT (name) DO NOTHING;
INSERT INTO categories (id, name) VALUES (nextval('categories_seq'), 'Lazer') ON CONFLICT (name) DO NOTHING;
INSERT INTO categories (id, name) VALUES (nextval('categories_seq'), 'Outros') ON CONFLICT (name) DO NOTHING;

ALTER SEQUENCE categories_seq RESTART WITH 4;