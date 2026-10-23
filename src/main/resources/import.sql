-- This file allow to write SQL commands that will be emitted in test and dev.
-- The commands are commented as their support depends of the database
-- insert into myentity (id, field) values(1, 'field-1');
-- insert into myentity (id, field) values(2, 'field-2');
-- insert into myentity (id, field) values(3, 'field-3');
-- alter sequence myentity_seq restart with 4;

INSERT INTO categories (id, name) VALUES (nextval('categories_seq'), 'Alimentação') ON CONFLICT (name) DO NOTHING;
INSERT INTO categories (id, name) VALUES (nextval('categories_seq'), 'Lazer') ON CONFLICT (name) DO NOTHING;
INSERT INTO categories (id, name) VALUES (nextval('categories_seq'), 'Outros') ON CONFLICT (name) DO NOTHING;

ALTER SEQUENCE categories_seq RESTART WITH 4;