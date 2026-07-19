from typing import Optional

from fastapi import Depends, Header, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from GlucServer.DB import get_db
from GlucServer.security.tokens import verify_access_token
from GlucServer.DB.models import User


async def get_current_user(
        authorization: str = Header(
            ...,
            description="Bearer токен в формате: Bearer <token>"
        ),
        db: AsyncSession = Depends(get_db)
) -> User:
    """
    Извлекает текущего пользователя из JWT токена.

    Что проверяет:
    1. Наличие заголовка Authorization
    2. Формат токена (Bearer ...)
    3. Валидность JWT токена
    4. Существование пользователя в БД
    """

    if not authorization:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Отсутствует заголовок Authorization",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Неверный формат токена. Используйте: Bearer <token>",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = authorization.replace("Bearer ", "")

    try:
        user_id = verify_access_token(token)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Ошибка авторизации: {str(e)}",
            headers={"WWW-Authenticate": "Bearer"},
        )

    result = await db.execute(
        select(User).where(User.id == user_id)
    )
    user = result.scalar_one_or_none()

    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Пользователь не найден. Возможно, аккаунт удален.",
        )

    return user


async def get_optional_user(
        authorization: Optional[str] = Header(None),
        db: AsyncSession = Depends(get_db)
) -> Optional[User]:
    """
    Опциональная авторизация.
    Если токен есть — возвращает пользователя.
    Если нет — возвращает None (для публичных эндпоинтов).
    """
    if not authorization or not authorization.startswith("Bearer "):
        return None

    try:
        token = authorization.replace("Bearer ", "")
        user_id = verify_access_token(token)

        result = await db.execute(
            select(User).where(User.id == user_id)
        )
        return result.scalar_one_or_none()
    except Exception:
        return None
