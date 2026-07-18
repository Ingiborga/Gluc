import json
from pathlib import Path

from sqlalchemy.ext.asyncio import create_async_engine

with open(Path(__file__).parent.parent / "config.json", "r", encoding="utf-8") as file:
    CONFIG = json.load(file)
DATABASE_URL = (f"postgresql+asyncpg://{CONFIG["username"]}:{CONFIG["password"]}"
                f"@{CONFIG["db_host"]}:{CONFIG["db_port"]}/{CONFIG["db_name"]}")
DEBUG = CONFIG["debug"] in ("True", "true", "1")

engine = create_async_engine(
    DATABASE_URL,
    echo=DEBUG,
    pool_size=20,
    max_overflow=10
)
