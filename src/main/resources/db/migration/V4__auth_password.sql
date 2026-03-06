-- Allow nullable password_hash for Cognito users
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Update seed user with BCrypt hash for password 'memoryvault'
UPDATE users
SET password_hash = '$2b$12$nDUurP9RwJl.cj36vBkDn.jRLEF/y/4V2aQ1VVJfQ05o/pcvjLXt6'
WHERE id = '00000000-0000-0000-0000-000000000001';
