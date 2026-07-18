from datetime import datetime, timedelta
from typing import Optional, Dict, Any

from jose import JWTError, jwt
from pydantic import BaseModel

# TODO: Возможно, засунуть все константы в config файл
#  (придумать как его правильно сделать)

ACCESS_TOKEN_EXPIRE_MINUTES = 30
REFRESH_TOKEN_EXPIRE_DAYS = 7
JWT_SECRET_KEY = "access-secret-key"  # TODO: изменить в prod
JWT_REFRESH_SECRET_KEY = "refresh-secret-key"  # TODO: изменить
JWT_ALGORITHM = "HS256"


class TokenPayload(BaseModel):
    """То, что хранится внутри токена (payload)"""
    sub: int  # subject — ID пользователя
    exp: datetime  # expiration — когда истекает
    iat: datetime  # issued at — когда создан
    type: str  # "access" или "refresh"


class TokenResponse(BaseModel):
    """То, что возвращаем клиенту"""
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


def create_access_token(
        user_id: int,
        expires_delta: Optional[timedelta] = None
) -> str:
    """
    Создает access токен для доступа к API.
    Access токен — короткоживущий (15-30 минут).

    Args:
        user_id: ID пользователя
        expires_delta: время жизни токена (по умолчанию из настроек)
    """

    now = datetime.now()

    if expires_delta:
        expire = now + expires_delta
    else:
        expire = now + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)

    payload = {
        "sub": str(user_id),
        "iat": now,
        "exp": expire,
        "type": "access"
    }

    token = jwt.encode(
        payload,
        JWT_SECRET_KEY,
        algorithm=JWT_ALGORITHM
    )

    return token


def create_refresh_token(
        user_id: int,
        expires_delta: Optional[timedelta] = None
) -> str:
    """
    Создает refresh токен для обновления access токена.
    """
    if expires_delta:
        expire = datetime.now() + expires_delta
    else:
        expire = datetime.now() + timedelta(days=REFRESH_TOKEN_EXPIRE_DAYS)

    payload = {
        "sub": str(user_id),
        "iat": datetime.now(),
        "exp": expire,
        "type": "refresh"
    }

    token = jwt.encode(
        payload,
        JWT_REFRESH_SECRET_KEY,
        algorithm=JWT_ALGORITHM
    )

    return token


def decode_token(token: str, secret_key: str = None) -> Dict[str, Any]:
    """
    Расшифровывает и проверяет JWT токен.

    Args:
        token: JWT токен
        secret_key: ключ для проверки (по умолчанию для access токенов)

    Returns:
        payload: данные из токена

    Raises:
        JWTError: если токен невалидный
    """
    if secret_key is None:
        secret_key = JWT_SECRET_KEY

    try:
        payload = jwt.decode(
            token,
            secret_key,
            algorithms=[JWT_ALGORITHM],
            options={"verify_exp": True}
        )
        return payload
    except jwt.ExpiredSignatureError:
        raise JWTError("Токен истек")
    except jwt.JWTClaimsError:
        raise JWTError("Неверные данные токена")
    except JWTError:
        raise JWTError("Неверный токен")


def verify_access_token(token: str) -> int:
    """
    Проверяет access токен и возвращает ID пользователя.
    """
    payload = decode_token(token, JWT_SECRET_KEY)

    if payload.get("type") != "access":
        raise JWTError("Неверный тип токена")

    user_id = payload.get("sub")
    if not user_id:
        raise JWTError("Токен не содержит ID пользователя")

    return int(user_id)


def verify_refresh_token(token: str) -> int:
    """
    Проверяет refresh токен.
    """
    payload = decode_token(token, JWT_REFRESH_SECRET_KEY)

    if payload.get("type") != "refresh":
        raise JWTError("Неверный тип токена")

    user_id = payload.get("sub")
    if not user_id:
        raise JWTError("Токен не содержит ID пользователя")

    return int(user_id)


def create_token_pair(user_id: int) -> TokenResponse:
    """
    Создает пару токенов (access + refresh).
    """
    access_token = create_access_token(user_id)
    refresh_token = create_refresh_token(user_id)

    return TokenResponse(
        access_token=access_token,
        refresh_token=refresh_token
    )
