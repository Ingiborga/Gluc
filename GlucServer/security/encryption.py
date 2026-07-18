from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
import base64
import json
from typing import Any, Dict


ENCRYPTION_KEY = "fernet-key"  # TODO: изменить


class MedicalDataEncryptor:
    """
    Шифрует/расшифровывает медицинские данные.
    """

    def __init__(self):
        """Инициализация шифратора с ключом из настроек"""
        # TODO:  В продакшене ключ должен быть в HSM/Vault
        self.cipher = Fernet(ENCRYPTION_KEY.encode())

    def encrypt_value(self, value: float) -> str:
        """
        Шифрует одно значение глюкозы.
        """
        data = str(value).encode()
        encrypted = self.cipher.encrypt(data)
        return encrypted.decode()

    def decrypt_value(self, encrypted_value: str) -> float:
        """
        Расшифровывает значение глюкозы.
        """
        decrypted = self.cipher.decrypt(encrypted_value.encode())
        return float(decrypted.decode())

    def encrypt_dict(self, data: Dict[str, Any]) -> str:
        """
        Шифрует словарь данных (например, все показания сенсора).
        """
        json_data = json.dumps(data)
        encrypted = self.cipher.encrypt(json_data.encode())
        return encrypted.decode()

    def decrypt_dict(self, encrypted_data: str) -> Dict[str, Any]:
        """
        Расшифровывает словарь данных.
        """
        decrypted = self.cipher.decrypt(encrypted_data.encode())
        return json.loads(decrypted.decode())


encryptor = MedicalDataEncryptor()


def encrypt_glucose_data(value: float) -> str:
    """Быстрое шифрование значения глюкозы"""
    return encryptor.encrypt_value(value)


def decrypt_glucose_data(encrypted_value: str) -> float:
    """Быстрое расшифрование значения глюкозы"""
    return encryptor.decrypt_value(encrypted_value)
