"""
Эндпоинты аутентификации:
- POST /auth/register — регистрация
- POST /auth/login — вход
- POST /auth/refresh — обновление токенов
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from GlucServer.DB.session import get_db
from GlucServer.api.dependencies import get_current_user
from GlucServer.security.hashing import (
    hash_password,
    verify_password,
    needs_rehash,
)
from GlucServer.security.tokens import (
    create_token_pair,
    verify_refresh_token,
    create_access_token,
    create_refresh_token,
)
from GlucServer.DB.models import User
from GlucServer.schemas.auth import (
    UserRegisterRequest,
    UserLoginRequest,
    TokenResponse,
    RefreshTokenRequest,
    UserResponse,
)

router = APIRouter(prefix="/auth", tags=["Аутентификация"])


@router.post(
    "/register",
    response_model=TokenResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Регистрация нового пользователя",
    description="Создает аккаунт и возвращает access + refresh токены"
)
async def register(
        data: UserRegisterRequest,
        db: AsyncSession = Depends(get_db)
):
    """
    Регистрация нового пользователя.

    Процесс:
    1. Проверяем, что email не занят
    2. Хешируем пароль
    3. Создаем пользователя в БД
    4. Генерируем пару токенов
    5. Возвращаем токены
    """

    existing = await db.execute(
        select(User.id).where(User.email == data.email)
    )
    if existing.scalar_one_or_none():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Пользователь с таким email уже существует. "
                   "Используйте другой email или войдите в существующий аккаунт."
        )

    if data.phone:
        existing_phone = await db.execute(
            select(User.id).where(User.phone == data.phone)
        )
        if existing_phone.scalar_one_or_none():
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Пользователь с таким номером телефона уже существует."
            )

    hashed_password = hash_password(data.password)

    user = User(
        email=data.email,
        password_hash=hashed_password,
        name=data.name,
        phone=data.phone,
        min_threshold=data.min_threshold,
        max_threshold=data.max_threshold,
    )

    db.add(user)
    await db.commit()
    await db.refresh(user)

    tokens = create_token_pair(user.id)

    return TokenResponse(
        access_token=tokens.access_token,
        refresh_token=tokens.refresh_token,
        token_type="bearer"
    )


@router.post(
    "/login",
    response_model=TokenResponse,
    summary="Вход в систему",
    description="Авторизует пользователя и возвращает новую пару токенов"
)
async def login(
        data: UserLoginRequest,
        db: AsyncSession = Depends(get_db)
):
    """
    Вход в систему.

    Процесс:
    1. Ищем пользователя по email
    2. Проверяем пароль
    3. Если хеш устарел — обновляем
    4. Генерируем новую пару токенов
    """

    result = await db.execute(
        select(User).where(User.email == data.email)
    )
    user = result.scalar_one_or_none()

    if not user or not verify_password(data.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Неверный email или пароль.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if needs_rehash(user.password_hash):
        user.password_hash = hash_password(data.password)
        await db.commit()

    tokens = create_token_pair(user.id)

    return TokenResponse(
        access_token=tokens.access_token,
        refresh_token=tokens.refresh_token,
        token_type="bearer"
    )


@router.post(
    "/refresh",
    response_model=TokenResponse,
    summary="Обновление токенов",
    description="Получает новый access токен по refresh токену"
)
async def refresh_tokens(
        data: RefreshTokenRequest,
        db: AsyncSession = Depends(get_db)
):
    """
    Обновляет пару токенов.

    Когда использовать:
    - Access токен истек (обычно через 30 минут)
    - Клиент отправляет refresh токен
    - Сервер выдает НОВУЮ ПАРУ токенов
    """
    try:
        user_id = verify_refresh_token(data.refresh_token)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Неверный или истекший refresh токен: {str(e)}",
            headers={"WWW-Authenticate": "Bearer"},
        )

    result = await db.execute(
        select(User.id).where(User.id == user_id)
    )
    if not result.scalar_one_or_none():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Пользователь не найден",
        )

    tokens = create_token_pair(user_id)

    return TokenResponse(
        access_token=tokens.access_token,
        refresh_token=tokens.refresh_token,
        token_type="bearer"
    )


@router.get(
    "/me",
    response_model=UserResponse,
    summary="Текущий пользователь",
    description="Возвращает данные авторизованного пользователя"
)
async def get_current_user_info(
        current_user: User = Depends(get_current_user)  # TODO: перепроверить работу
):
    """
    Возвращает информацию о текущем пользователе.
    Полезно для:
    - Проверки валидности токена
    - Получения настроек пользователя
    - Отображения профиля в приложении
    """
    return current_user
