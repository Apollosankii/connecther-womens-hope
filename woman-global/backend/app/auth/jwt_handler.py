import time
from datetime import datetime,timedelta
import jwt
from decouple import config

JWT_SECRET_KEY=config("secret")
# JWT_REFRESH_SECRET_KEY=config("secret2")
ALGORITHM =config("algorithm")
ACCESS_TOKEN_EXPIRE_MINUTES= config("ACCESS_TOKEN_EXPIRE")
# REFRESH_TOKEN_EXPIRE_MINUTES = config("REFRESH_TOKEN_EXPIRE")

# This function returns generated tokens
def token_response(token: str):
    return {"access_token":token}

def sign_JWT(userID: str,**kwargs):

    if kwargs['role']:
        payload ={"userID":userID,
                  'role': kwargs['role'],
                "expiry":time.time() + 2592000000
                }
        token=jwt.encode(payload,JWT_SECRET_KEY,algorithm=ALGORITHM)
        return token
    else:
        payload ={"userID":userID,
                "expiry":time.time() + 2592000000
                }
        token=jwt.encode(payload,JWT_SECRET_KEY,algorithm=ALGORITHM)
        return token
     

def decode_JWT(token: str):
    try:
        decode_token=jwt.decode(token,JWT_SECRET_KEY,algorithms=ALGORITHM)
        return decode_token if decode_token["expiry"]>=time.time() else 'Expired Login Again'
    except Exception as e:
        return e



# def create_access_token(subject: Union[str, Any], expires_delta: int = None) -> str:
#     if expires_delta is not None:
#         expires_delta = datetime.now() + expires_delta
#     else:
#         expires_delta = datetime.now() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    
#     to_encode = {"exp": expires_delta, "sub": str(subject)}
#     encoded_jwt = jwt.encode(to_encode, JWT_SECRET_KEY, ALGORITHM)
#     return encoded_jwt

# def create_refresh_token(subject: Union[str, Any], expires_delta: int = None) -> str:
#     if expires_delta is not None:
#         expires_delta = datetime.now() + expires_delta
#     else:
#         expires_delta = datetime.now() + timedelta(minutes=REFRESH_TOKEN_EXPIRE_MINUTES)
    
#     to_encode = {"exp": expires_delta, "sub": str(subject)}
#     encoded_jwt = jwt.encode(to_encode, JWT_REFRESH_SECRET_KEY, ALGORITHM)
#     return encoded_jwt