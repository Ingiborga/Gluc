from pydantic import BaseModel, Field, field_validator, ConfigDict
from datetime import datetime
from typing import List, Optional


class GlucoseReading(BaseModel):
    """Одно показание глюкозы от сенсора"""
    glucose_value: str = Field(
        ...,
        description="Значение глюкозы (строка из сенсора, конвертируем во float)"
    )
    date: str = Field(
        ...,
        description="Дата и время измерения (формат: YYYY-MM-DD HH:MM:SS)"
    )

    @field_validator('glucose_value')
    def validate_glucose_value(cls, v):
        """Проверяем, что значение можно конвертировать в число"""
        try:
            value = float(v)
            if value < 0:
                raise ValueError('Значение глюкозы должно быть больше 0 mmol/L')
        except ValueError as e:
            if "could not convert" in str(e).lower():
                raise ValueError('glucose_value должен быть числом')
            raise e
        return v

    @field_validator('date')
    def validate_date_format(cls, v):
        """Проверяем формат даты"""
        try:
            datetime.strptime(v, "%Y-%m-%d %H:%M:%S")
        except ValueError:
            raise ValueError('Неверный формат даты. Используйте: YYYY-MM-DD HH:MM:SS')
        return v


class GlucoseDataRequest(BaseModel):
    """Данные, приходящие от устройства"""
    username: str = Field(
        ...,
        description="Имя пользователя или email (идентификатор)"
    )
    data: List[GlucoseReading] = Field(
        ...,
        min_items=1,
        max_items=288,  # Максимум показаний за сутки (каждые 5 минут)
        description="Список показаний глюкозы"
    )

    @field_validator('username')
    def username_not_empty(cls, v):
        if not v or not v.strip():
            raise ValueError('username не может быть пустым')
        return v.strip()

    model_config = ConfigDict(json_schema_extra={
            "example": {
                "username": "ivan@example.com",
                "data": [
                    {
                        "glucose_value": "8.8",
                        "date": "2026-06-14 17:17:03"
                    },
                    {
                        "glucose_value": "7.9",
                        "date": "2026-06-14 17:23:03"
                    }
                ]
            }
        })


class GlucoseStatsRequest(BaseModel):
    """Параметры запроса статистики"""
    from_date: Optional[datetime] = Field(
        None,
        description="Начало периода (ISO формат)"
    )
    to_date: Optional[datetime] = Field(
        None,
        description="Конец периода (ISO формат)"
    )
    limit: int = Field(
        default=288,
        ge=1,
        le=2016,  # Максимум за неделю (288 * 7)
        description="Максимальное количество показаний"
    )


class GlucoseReadingResponse(BaseModel):
    """Одно показание для ответа"""
    id: int
    value: float
    measured_at: datetime
    trend: Optional[str] = None
    device_id: int

    model_config = ConfigDict(from_attributes=True)


class GlucoseStatsResponse(BaseModel):
    """Статистика глюкозы за период"""
    total_readings: int = Field(..., description="Всего показаний")
    min_value: Optional[float] = Field(None, description="Минимальное значение")
    max_value: Optional[float] = Field(None, description="Максимальное значение")
    avg_value: Optional[float] = Field(None, description="Среднее значение")
    readings: List[GlucoseReadingResponse] = Field(
        default_factory=list,
        description="Список показаний"
    )
    period: dict = Field(
        default_factory=dict,
        description="Информация о периоде"
    )

    model_config = ConfigDict(json_schema_extra={
            "example": {
                "total_readings": 48,
                "min_value": 3.9,
                "max_value": 14.2,
                "avg_value": 6.7,
                "readings": [
                    {
                        "id": 1234,
                        "value": 5.7,
                        "measured_at": "2026-06-14T17:17:03",
                        "trend": "stable",
                        "device_id": 1
                    }
                ],
                "period": {
                    "from": "2026-06-14T00:00:00",
                    "to": "2026-06-14T23:59:59"
                }
            }
        })


class GlucoseSubmissionResponse(BaseModel):
    """Ответ на отправку данных с сенсора"""
    status: str = Field(..., description="Статус обработки")
    processed_count: int = Field(..., description="Сколько показаний обработано")
    errors: List[dict] = Field(
        default_factory=list,
        description="Ошибки при обработке"
    )
    message: str = Field(..., description="Сообщение о результате")
