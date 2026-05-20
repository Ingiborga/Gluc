from datetime import datetime

from pydantic import BaseModel, EmailStr, Field


class UserRegister(BaseModel):
    """Данные для регистрации"""
    email: EmailStr
    password: str = Field(..., min_length=8)
    name: str = Field(..., min_length=2, max_length=100)


class UserLogin(BaseModel):
    """Данные для входа"""
    email: EmailStr
    password: str


class Token(BaseModel):
    """Ответ с токеном"""
    access_token: str
    token_type: str = "bearer"
    user_id: int
    name: str


class GlucoseReading(BaseModel):
    """Показание глюкозы от сенсора"""
    value: float = Field(..., ge=0.0, le=35.0)
    timestamp: datetime
    device_id: str = Field(..., min_length=3)
