from passlib.context import CryptContext

pwd_context = CryptContext(
    schemes=["bcrypt"],
    deprecated="auto",
    bcrypt__rounds=12
)

MIN_PASSWORD_LENGTH = 8
MAX_PASSWORD_LENGTH = 128


def hash_password(plain_password: str) -> str:
    """Превращает пароль в хеш (с использованием соли)."""
    return pwd_context.hash(plain_password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Проверяет введенный пароль на соответствие хешу."""
    return pwd_context.verify(plain_password, hashed_password)


def validate_password_strength(password: str) -> tuple[bool, str]:
    """Проверяет пароль на соответствие политике безопасности."""
    if len(password) < MIN_PASSWORD_LENGTH:
        return False, f"Пароль должен содержать минимум {MIN_PASSWORD_LENGTH} символов."
    if len(password) > MAX_PASSWORD_LENGTH:
        return False, f"Пароль слишком длинный. (N > {MAX_PASSWORD_LENGTH})"
    if not any(c.isupper() for c in password):
        return False, "Пароль должен содержать хотя бы одну заглавную букву"
    if not any(c.islower() for c in password):
        return False, "Пароль должен содержать хотя бы одну строчную букву"
    if not any(c.isdigit() for c in password):
        return False, "Пароль должен содержать хотя бы одну букву"
    return True, ""


def needs_rehash(hashed_password: str) -> bool:
    """Проверяет необходимость перехешировать пароль."""
    return pwd_context.needs_update(hashed_password)
