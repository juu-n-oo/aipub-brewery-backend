-- Add base_image column to dockerfiles (NOT NULL; backfill existing rows from the FROM line)
ALTER TABLE dockerfiles ADD COLUMN base_image VARCHAR(512);
UPDATE dockerfiles
   SET base_image = COALESCE((regexp_match(content, '(?im)^\s*FROM\s+(\S+)'))[1], '')
 WHERE base_image IS NULL;
ALTER TABLE dockerfiles ALTER COLUMN base_image SET NOT NULL;
