from passlib.context import CryptContext
import os
from datetime import datetime, timedelta
from typing import Union, Any
import hashlib
import random
import string

password_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def get_hashed_password(password: str) -> str:
    return password_context.hash(password)


def verify_password(password: str, hashed_pass: str) -> bool:
    try:
        return password_context.verify(password, hashed_pass)
    except:
        return False
    
def gen_user_id(paramater: str) -> str:
    # This function is used to create unique user_id from their phone numbers
    try:
        return hashlib.sha1(paramater.encode("UTF-8")).hexdigest()[:6]
    except Exception as e:
        return {'Error':e}
    
def random_string(length):
    return ''.join(random.choices(string.ascii_letters + string.digits, k=length))