from datetime import datetime
from typing import Optional

from fastapi import FastAPI
from starlette import status
from starlette.responses import Response
from pydantic_models import UserRegister, UserLogin, Token, GlucoseReading
from _utils import create_jwt_token, verify_jwt_token


app = FastAPI(
    title="Gluc API",
    description="Сервер для управления данными",
    version="0.0.1")


@app.post("/auth/register",
          response_model=Token,
          status_code=status.HTTP_201_CREATED)
async def register(user_data: UserRegister):
    """Регистрация нового пользователя"""
    # TODO: проверка на наличие email в БД
    # TODO: хеширование пароля
    # TODO: Сохранение email
    # TODO: Создание токена

    # Пример вывода
    return {
        "access_token": "eyJhbGiOiJIUzI1NiIs",
        "token_type": "bearer",
        "user_id": 1,
        "name": user_data.name
    }


@app.post("/auth/login", response_model=Token)
async def login(credentials: UserLogin):
    """Вход в систему. Возвращает JWT"""

    # TODO: поиск пользователя по email
    # TODO: проверка пароля (сравнение хешей)
    # TODO: генерация токена

    return {
        "access_token": "eyJhbGiOiJIUzI1NiIs",
        "token_type": "bearer",
        "user_id": 1,
        "name": "Тестовый пользователь"
    }


@app.get("/api/statistics")
async def get_statistics(
        user_id: int,
        from_date: Optional[datetime] = None,
        to_date: Optional[datetime] = None
):
    """Получение статистики показаний глюкозы за период"""

    # TODO: запрос к БД с фильтрацией по датам
    # TODO: расшифровка зашифрованных данных

    return {
        "test": "testData"
    }
