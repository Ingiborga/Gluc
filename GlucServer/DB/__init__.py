from .models import Base, User, Device, GlucoseMeasurement, MLPrediction
from .engine import engine
from .session import AsyncSessionLocal, get_db


async def init_db():
    """Создает все таблицы БД (если их нет.
    Вызывается при запуске приложения."""

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


async def drop_db():
    """Удаляет все таблицы"""

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
