UPDATE users
SET operation_password_hint = NULL
WHERE operation_password_hint IS NOT NULL;
