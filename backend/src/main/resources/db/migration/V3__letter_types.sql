-- Reference data needed in every environment: the letter types and one lock
-- row per type. In the real system the types already exist; only the
-- letter_type_lock rows would be new, inserted from the existing type list.

INSERT INTO letter_types (letter_type, display_name) VALUES
  ('LETTER_A', 'Letter type A'),
  ('LETTER_B', 'Letter type B'),
  ('LETTER_C', 'Letter type C'),
  ('LETTER_D', 'Letter type D'),
  ('LETTER_E', 'Letter type E'),
  ('LETTER_F', 'Letter type F'),
  ('LETTER_G', 'Letter type G'),
  ('LETTER_H', 'Letter type H'),
  ('LETTER_I', 'Letter type I');

INSERT INTO letter_type_lock (letter_type)
SELECT letter_type FROM letter_types;
