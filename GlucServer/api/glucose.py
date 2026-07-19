"""
Эндпоинты для работы с данными глюкозы:
- POST /api/glucose/data — получение данных с сенсора
- GET /api/glucose/statistics — статистика за период
- GET /api/glucose/latest — последние показания
"""

from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, and_, desc
from datetime import datetime, timedelta
from typing import Optional

from GlucServer.DB.session import get_db
from GlucServer.api.dependencies import get_current_user
from GlucServer.DB.models import User, Device, GlucoseMeasurement
from GlucServer.schemas.glucose import (
    GlucoseDataRequest,
    GlucoseSubmissionResponse,
    GlucoseStatsRequest,
    GlucoseStatsResponse,
    GlucoseReadingResponse,
)

router = APIRouter(prefix="/api/glucose", tags=["Глюкоза"])


@router.post(
    "/data",
    response_model=GlucoseSubmissionResponse,
    summary="Получение данных с сенсора",
    description="Принимает массив показаний глюкозы от устройства"
)
async def submit_glucose_data(
        data: GlucoseDataRequest,
        db: AsyncSession = Depends(get_db),
        current_user: User = Depends(get_current_user)
):
    """
    Принимает данные глюкозы от сенсора.

    Процесс обработки:
    1. Проверяем авторизацию (JWT токен)
    2. Проверяем, что username совпадает с авторизованным пользователем
    3. Ищем или создаем устройство пользователя
    4. Обрабатываем каждое показание
    5. Сохраняем в БД
    6. Возвращаем результат
    """

    if data.username != current_user.email and data.username != current_user.name:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Username в запросе не соответствует авторизованному пользователю"
        )

    device_result = await db.execute(
        select(Device).where(
            and_(
                Device.user_id == current_user.id,
                Device.connection_status == "connected"
            )
        ).limit(1)
    )
    device = device_result.scalar_one_or_none()

    if not device:
        device_result = await db.execute(
            select(Device).where(
                Device.user_id == current_user.id
            ).limit(1)
        )
        device = device_result.scalar_one_or_none()

    if not device:
        device = Device(
            user_id=current_user.id,
            device_serial=f"import_{current_user.id}",
            device_type="manual_import",
            connection_status="active"
        )
        db.add(device)
        await db.flush()

    processed_count = 0
    errors = []

    for reading in data.data:
        try:
            glucose_value = float(reading.glucose_value)
            measured_at = datetime.strptime(reading.date, "%Y-%m-%d %H:%M:%S")
            if glucose_value < 0 or glucose_value > 35:
                errors.append({
                    "reading": reading.dict(),
                    "error": f"Значение {glucose_value} вне допустимого диапазона (0-35 mmol/L)"
                })
                continue
            if measured_at > datetime.now() + timedelta(minutes=5):
                errors.append({
                    "reading": reading.dict(),
                    "error": "Дата измерения не может быть в будущем"
                })
                continue

            measurement = GlucoseMeasurement(
                user_id=current_user.id,
                device_id=device.id,
                value=glucose_value,
                measured_at=measured_at,
                raw_data=None,  # TODO: Пока нет сырых данных
                trend=None  # TODO: Тренд будем вычислять позже
            )
            db.add(measurement)
            processed_count += 1

        except ValueError as e:
            errors.append({
                "reading": reading.dict(),
                "error": f"Ошибка формата данных: {str(e)}"
            })
        except Exception as e:
            errors.append({
                "reading": reading.dict(),
                "error": f"Неожиданная ошибка: {str(e)}"
            })

    if processed_count > 0:
        device.last_sync_at = datetime.now()
        await db.commit()

    message = f"Успешно обработано {processed_count} из {len(data.data)} показаний"
    if errors:
        message += f". Ошибок: {len(errors)}"

    return GlucoseSubmissionResponse(
        status="success" if processed_count > 0 else "error",
        processed_count=processed_count,
        errors=errors,
        message=message
    )


@router.get(
    "/statistics",
    response_model=GlucoseStatsResponse,
    summary="Статистика глюкозы",
    description="Возвращает статистику показаний за выбранный период"
)
async def get_glucose_statistics(
        from_date: Optional[datetime] = Query(
            None,
            description="Начало периода (ISO формат). По умолчанию: 24 часа назад"
        ),
        to_date: Optional[datetime] = Query(
            None,
            description="Конец периода (ISO формат). По умолчанию: сейчас"
        ),
        limit: int = Query(
            288,
            ge=1,
            le=2016,
            description="Максимальное количество показаний"
        ),
        db: AsyncSession = Depends(get_db),
        current_user: User = Depends(get_current_user)
):
    """
    Возвращает статистику глюкозы за период.

    Если даты не указаны:
    - from_date = сейчас - 24 часа
    - to_date = сейчас

    Возвращает:
    - Общее количество показаний
    - Минимальное, максимальное, среднее значения
    - Список показаний (для графика)
    - Информацию о периоде
    """

    now = datetime.now()
    if not to_date:
        to_date = now
    if not from_date:
        from_date = now - timedelta(hours=24)

    stats_query = select(
        func.count(GlucoseMeasurement.id).label("total"),
        func.min(GlucoseMeasurement.value).label("min_val"),
        func.max(GlucoseMeasurement.value).label("max_val"),
        func.avg(GlucoseMeasurement.value).label("avg_val")
    ).where(
        and_(
            GlucoseMeasurement.user_id == current_user.id,
            GlucoseMeasurement.measured_at >= from_date,
            GlucoseMeasurement.measured_at <= to_date
        )
    )

    stats_result = await db.execute(stats_query)
    stats = stats_result.first()

    readings_query = (
        select(GlucoseMeasurement)
        .where(
            and_(
                GlucoseMeasurement.user_id == current_user.id,
                GlucoseMeasurement.measured_at >= from_date,
                GlucoseMeasurement.measured_at <= to_date
            )
        )
        .order_by(GlucoseMeasurement.measured_at.asc())
        .limit(limit)
    )

    readings_result = await db.execute(readings_query)
    readings = readings_result.scalars().all()

    return GlucoseStatsResponse(
        total_readings=stats.total or 0,
        min_value=round(stats.min_val, 1) if stats.min_val else None,
        max_value=round(stats.max_val, 1) if stats.max_val else None,
        avg_value=round(stats.avg_val, 1) if stats.avg_val else None,
        readings=[
            GlucoseReadingResponse.from_orm(r) for r in readings
        ],
        period={
            "from": from_date.isoformat(),
            "to": to_date.isoformat(),
            "hours": round((to_date - from_date).total_seconds() / 3600, 1)
        }
    )


@router.get(
    "/latest",
    response_model=dict,
    summary="Последние показания",
    description="Возвращает последние N показаний"
)
async def get_latest_readings(
        count: int = Query(
            10,
            ge=1,
            le=100,
            description="Количество последних показаний"
        ),
        db: AsyncSession = Depends(get_db),
        current_user: User = Depends(get_current_user)
):
    """
    Возвращает последние показания для быстрого отображения.

    Используется:
    - На главном экране приложения
    - Для быстрой проверки текущего состояния
    - Без указания периода (только последние N)
    """

    query = (
        select(GlucoseMeasurement)
        .where(GlucoseMeasurement.user_id == current_user.id)
        .order_by(desc(GlucoseMeasurement.measured_at))
        .limit(count)
    )

    result = await db.execute(query)
    readings = result.scalars().all()

    current_status = None
    if readings:
        last_value = readings[0].value
        if last_value < current_user.min_threshold:
            current_status = "low"
        elif last_value > current_user.max_threshold:
            current_status = "high"
        else:
            current_status = "normal"

    return {
        "current_status": current_status,
        "last_value": readings[0].value if readings else None,
        "thresholds": {
            "min": current_user.min_threshold,
            "max": current_user.max_threshold
        },
        "readings": [
            GlucoseReadingResponse.from_orm(r) for r in readings
        ]
    }
