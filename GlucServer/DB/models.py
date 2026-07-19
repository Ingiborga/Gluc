import json
from pathlib import Path

import sqlalchemy as sa
import sqlalchemy.orm as orm

with open(Path(__file__).parent.parent / "config.json", "r", encoding="utf-8") as file:
    CONFIG = json.load(file)
SCHEMA = CONFIG["schema_name"]


class Base(orm.DeclarativeBase):
    metadata = sa.MetaData(schema=SCHEMA)


class User(Base):
    """Модель таблицы пользователей для ORM"""

    __tablename__ = "users"
    id = sa.Column(sa.Integer, primary_key=True, index=True, autoincrement=True)
    email = sa.Column(sa.String(255), unique=True, nullable=False, index=True)
    password_hash = sa.Column(sa.String(255), nullable=False)
    name = sa.Column(sa.String(100), nullable=False)
    phone = sa.Column(sa.String(20), unique=True, nullable=True)
    min_threshold = sa.Column(sa.Float)
    max_threshold = sa.Column(sa.Float)
    created_at = sa.Column(sa.DateTime, server_default=sa.sql.func.now())
    updated_at = sa.Column(sa.DateTime, server_default=sa.sql.func.now(),
                           onupdate=sa.sql.func.now())

    devices = orm.relationship("Device", back_populates="user",
                               cascade="all, delete-orphan")
    measurements = orm.relationship("GlucoseMeasurement",
                                    back_populates="user",
                                    cascade="all, delete-orphan")

    def __repr__(self):
        return f"<User(id={self.id}, email='{self.email}', name='{self.name}'>"


class Device(Base):
    """Модель таблицы устройств для ORM"""

    __tablename__ = "devices"
    id = sa.Column(sa.Integer, primary_key=True, index=True, autoincrement=True)
    user_id = sa.Column(sa.Integer, sa.ForeignKey(f"{SCHEMA}.users.id", ondelete="CASCADE"),
                        nullable=False, index=True)
    device_serial = sa.Column(sa.String(100), nullable=False, unique=True)
    device_type = sa.Column(sa.String(50), nullable=False)
    api_key_encrypted = sa.Column(sa.Text, nullable=True)
    connection_status = sa.Column(sa.String(20),
                                  default="disconnected")  # "connected", "disconnected", "error"
    last_sync_at = sa.Column(sa.DateTime, nullable=True)
    battery_level = sa.Column(sa.Integer, nullable=True)
    created_at = sa.Column(sa.DateTime, server_default=sa.sql.func.now())

    user = orm.relationship("User", back_populates="devices")
    measurements = orm.relationship("GlucoseMeasurement",
                                    back_populates="device",
                                    cascade="all, delete-orphan")

    __table_args__ = (sa.CheckConstraint("""connection_status
                                         IN ('connected', 'disconnected', 'error')""",
                      name="check_connection_status"),
    )

    def __repr__(self):
        return f"<Device(id={self.id}, type='{self.device_type}', serial='{self.device_serial}'>"


class GlucoseMeasurement(Base):
    """Модель таблицы измерений глюкозы для ORM"""

    __tablename__ = "glucose_measurements"
    id = sa.Column(sa.Integer, primary_key=True, index=True, autoincrement=True)
    user_id = sa.Column(sa.Integer, sa.ForeignKey(f"{SCHEMA}.users.id", ondelete="CASCADE"),
                        nullable=False, index=True)
    device_id = sa.Column(sa.Integer, sa.ForeignKey(f"{SCHEMA}.devices.id", ondelete="CASCADE"),
                          nullable=False, index=True)
    value = sa.Column(sa.Float, nullable=False)  # TODO: будет зашифровано, мб поменять тип данных
    measured_at = sa.Column(sa.DateTime, nullable=False, index=True)
    raw_data = sa.Column(sa.Text, nullable=True)  # JSON
    trend = sa.Column(sa.String(20), nullable=True)  # "rising", "falling", "stable"
    created_at = sa.Column(sa.DateTime, server_default=sa.sql.func.now())

    user = orm.relationship("User", back_populates="measurements")
    device = orm.relationship("Device", back_populates="measurements")

    prediction = orm.relationship("MLPrediction", back_populates="measurement",
                                  uselist=False, cascade="all, delete-orphan")
    __table_args__ = (sa.Index("idx_user_measured", "user_id", "measured_at"),
                      sa.CheckConstraint("trend IN ('rising', 'falling', 'stable')",
                                         name="check_trend")
                      )

    def __repr__(self):
        return f"<Glucose(id={self.id}, value='{self.value}', time='{self.measured_at}'>"


class MLPrediction(Base):
    """Модель для таблицы прогнозов НС для ORM"""

    __tablename__ = "ml_predictions"
    id = sa.Column(sa.Integer, primary_key=True, index=True, autoincrement=True)
    measurement_id = sa.Column(sa.Integer,
                               sa.ForeignKey(f"{SCHEMA}.glucose_measurements.id", ondelete="CASCADE"),
                               nullable=False, unique=True)
    risk_level = sa.Column(sa.String(20), nullable=False)  # "Низкий", "Норма", "Высокий"
    confidence = sa.Column(sa.Float, nullable=True)  # 0.0 - 1.0
    trend_analysis = sa.Column(sa.Text, nullable=True)  # JSON
    recommendation = sa.Column(sa.Text, nullable=True)
    created_at = sa.Column(sa.DateTime, server_default=sa.sql.func.now())

    measurement = orm.relationship("GlucoseMeasurement", back_populates="prediction")
    __table_args__ = (sa.CheckConstraint("risk_level IN ('Низкий', 'Норма', 'Высокий')",
                                         name="check_risk_level"),
    )

    def __repr__(self):
        return f"<Prediction(id={self.id}, risk='{self.risk_level}'>"
