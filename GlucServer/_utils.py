import json
from datetime import datetime, timedelta
import jwt
from fastapi import HTTPException

with open("config.json", 'r', encoding='utf-8') as file:
    config = json.load(file)


def create_jwt_token(user_id: int) -> str:
    """Создание токена при входе"""
    payload = {
        "user_id": user_id,
        "exp": datetime.now() + timedelta(hours=24),
        "iat": datetime.now(),
        "type": "access"
    }

    token = jwt.encode(payload, config["SECRET_KEY"], alg="HS256")
    return token


def verify_jwt_token(token: str) -> dict:
    """Проверка токена при каждом запросе"""
    try:
        payload = jwt.decode(token, config["SECRET_KEY"], algorithms=set("HS256"))
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Истекший токен")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Неверный токен")
