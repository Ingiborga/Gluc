import json
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from GlucServer.DB import init_db
from GlucServer.api import auth, glucose

with open(Path(__file__).parent / "config.json", "r", encoding="utf-8") as file:
    CONFIG = json.load(file)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Жизненный цикл приложения.
    При запуске — создаем таблицы в БД.
    При остановке — закрываем соединения.
    """
    await init_db()
    yield


app = FastAPI(
    title="Gluc API",
    description="Сервер для управления данными глюкозы",
    version="0.1.0",
    lifespan=lifespan
)

# Настройка CORS (чтобы Android мог подключаться)
app.add_middleware(
    CORSMiddleware,
    allow_origins=CONFIG["ALLOWED_ORIGINS"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(glucose.router)


@app.get("/")
async def root():
    """Корневой эндпоинт (health check)"""
    return {
        "status": "running",
        "app": "Gluc API",
        "version": "0.1.0",
        "docs": "/docs"
    }


@app.get("/health")
async def health_check():
    """Проверка здоровья сервера"""
    return {"status": "healthy"}
