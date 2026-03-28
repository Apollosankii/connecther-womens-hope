from fastapi import Request,HTTPException
from fastapi.security import HTTPBearer,HTTPAuthorizationCredentials
from app.auth.jwt_handler import decode_JWT

class jwtBearer(HTTPBearer):
    def __init__(self,auto_Error: bool =True):
        super(jwtBearer,self).__init__(auto_error=auto_Error)

    async def __call__(self,request:Request):
        credentials: HTTPAuthorizationCredentials =await super(jwtBearer,self).__call__(request)
        if credentials:
            if not credentials.scheme == "Bearer":
                raise HTTPException(status_code=403, details="Expired or Invalid Token")
            
            return credentials.credentials
        else:
            raise HTTPException(status_code=403, details="Expired or Invalid Token")
        
    def verify_jwt(self,jwtoken: str):
        isTokenValid: bool = False #False Flag
        payload=decode_JWT(jwtoken)
        if payload:
            isTokenValid = True

        return isTokenValid