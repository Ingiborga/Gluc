from pydantic import BaseModel, EmailStr, Field, field_validator, model_validator, ConfigDict
from datetime import datetime
from typing import Optional


class UserRegisterRequest(BaseModel):
    """Данные, которые приходят от клиента при регистрации"""
    email: EmailStr = Field(..., description="Email пользователя")
    password: str = Field(
        ...,
        min_length=8,
        max_length=128,
        description="Пароль (мин. 8 символов, заглавная + строчная + цифра)"
    )
    name: str = Field(
        ...,
        min_length=2,
        max_length=100,
        description="Имя пользователя"
    )
    phone: Optional[str] = Field(None, description="Номер телефона (опционально)")

    min_threshold: float = Field(
        default=3.9,
        ge=2.0,
        le=10.0,
        description="Нижняя граница нормы глюкозы"
    )

    max_threshold: float = Field(
        default=10.0,
        ge=5.0,
        le=30.0,
        description="Верхняя граница нормы глюкозы"
    )

    @model_validator(mode='after')
    def max_must_be_greater_than_min(self) -> 'UserRegisterRequest':
        """Проверка логики: макс должен быть больше мин"""
        if self.max_threshold <= self.min_threshold:
            raise ValueError('max_threshold должен быть больше min_threshold')
        return self

    @field_validator('password')
    def validate_password_strength(cls, v):
        """Базовая проверка пароля на уровне схемы"""
        if not any(c.isupper() for c in v):
            raise ValueError('Пароль должен содержать хотя бы одну заглавную букву')
        if not any(c.islower() for c in v):
            raise ValueError('Пароль должен содержать хотя бы одну строчную букву')
        if not any(c.isdigit() for c in v):
            raise ValueError('Пароль должен содержать хотя бы одну цифру')
        return v

    model_config = ConfigDict(json_schema_extra={
            "example": {
                "email": "ivan@example.com",
                "password": "MyPassword123",
                "name": "Иван Петров",
                "phone": "+79161234567",
                "min_threshold": 3.9,
                "max_threshold": 10.0
            }
        })


class UserLoginRequest(BaseModel):
    """Данные для входа"""
    email: EmailStr = Field(..., description="Email")
    password: str = Field(..., description="Пароль")

    model_config = ConfigDict(json_schema_extra={
            "example": {
                "email": "ivan@example.com",
                "password": "MyPassword123"
            }
        })


class TokenResponse(BaseModel):
    """Ответ с токенами (после регистрации или входа)"""
    access_token: str = Field(..., description="Access JWT токен")
    refresh_token: str = Field(..., description="Refresh JWT токен")
    token_type: str = Field(default="bearer", description="Тип токена")

    model_config = ConfigDict(json_schema_extra={
            "example": {
                "access_token": "eyJhbGciOiJIUzI1NiIs...",
                "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
                "token_type": "bearer"
            }
        })


class UserResponse(BaseModel):
    """Данные пользователя для ответа"""
    id: int
    email: str
    name: str
    phone: Optional[str] = None
    min_threshold: float
    max_threshold: float
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class RefreshTokenRequest(BaseModel):
    """Запрос на обновление токенов"""
    refresh_token: str = Field(..., description="Refresh токен")
