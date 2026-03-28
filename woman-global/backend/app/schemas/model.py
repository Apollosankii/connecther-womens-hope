from pydantic import BaseModel,Field,EmailStr,constr
from datetime import datetime
from typing import List, Optional

class SignUpSchema(BaseModel):
    # id: int
    first_name:str =Field(default=None)
    last_name: str =Field(default=None)
    phone: str = Field(default=None)
    email: str = Field(default=None | EmailStr) 
    title: str =Field(default=None)
    user_id: str =Field(default=None)
    password: str = Field(default=None)
    # password: str = Field(default=None) | constr(min_length=8,max_length=20)
    
    # class Config:
    #     json_schema_extra={
    #         "sign_Up_demo":{
    #             "Phone":"0712345678",
    #             "Email":"Saints@xyz.com",
    #             "Password":"weak_password",

    #         }
    #     }

class ProviderSignUpSchema(BaseModel):
    # id: int
    # first_name:str =Field(default=None)
    # last_name: str =Field(default=None)
    # phone: str = Field(default=None)
    # email: EmailStr
    gender: str = Field(default=None)
    birth_date:str =Field(default=None)
    country:str =Field(default='Kenya')
    county:str =Field(default=None)
    area_name:str = Field(default=None)
    prof_pic:str = Field(default=None)
    nat_id:str = Field(default=None) 
    emm_cont_1: str =Field(default=None)
    emm_cont_2: str =Field(default=None)
    
    # class Config:
    #     json_schema_extra={
    #         "sign_Up_demo":{
    #             "Phone":"0712345678",
    #             "Email":"Saints@xyz.com",
    #             "Password":"weak_password",

    #         }
    #     }
# This schema models Expected input from Admin 
# creating a Service provider 
class AdminProviderSignUp(ProviderSignUpSchema,SignUpSchema):
    documents: list = Field(default=None)
    location: dict = Field(default=None)
    subs :list = Field(default=None)
    prof_pic: str = Field(default=None)

class LoginSchema(BaseModel):
    phone: str
    password: str

    class config:
        json_schema_extra={
            "sign_Up_demo":{
                "Phone":"0712345678",
                "Password":"weak_password",

            }
        }
class AdminLoginSchema(BaseModel):
    email: EmailStr
    password: str

    class config:
        json_schema_extra={
            "sign_Up_demo":{
                "Phone":"0712345678",
                "Password":"weak_password",

            }
         }


class UserProfileSchema(BaseModel):
    
    first_name: str =Field(default=None)
    last_name: str =Field(default=None)
    gender: str = Field(default=None)
    location: dict = Field(default=None)
    phone: str = Field(default=None)
    email: str =Field(default=None)
    birth_date: datetime =Field(default=None)
    # ppic: str =Field(default=None)

    class Config:
        json_schema_extra={
            "sign_demo":{
                "first_name":"Saint Sebastian",
                "last_name":"Saints",
                "gender":"Female",

            }
        }

class JobSchema(BaseModel):
    id: int = Field(default=None)
    name: str =Field(default=None)
    user_id: int = Field(default=None)

    class Config:
        json_schema_extra={
            "post_demo":{
                "name":" Job 1",
                "user_id":11   
            }}
class ServiceSchema(BaseModel):
    # id: int
    name:str =Field(default=None)
    description:str =Field(default=None)
    min_price:str =Field(default=None)
    service_pic:str =Field(default=None)
        
    # class Config:
    #     json_schema_extra={
    #         "sign_Up_demo":{
    #             "Phone":"0712345678",
    #             "Email":"Saints@xyz.com",
    #             "Password":"weak_password",

    #         }
    #     }
# 
class ServiceUpdateSchema(BaseModel):
    # id: int
    description:str =Field(default=None)
    min_price:str =Field(default=None)
        
# 
class DocTypeSchema(BaseModel):
    # id: int
    name:str =Field(default=None)
    
 
#  ############################################
class LiveLocSchema(BaseModel):
    # id: int
    latitude:float =Field(default=None)
    longitude: float =Field(default=None)
    
class SubSchema(BaseModel):
    # id: int
    subs:Optional[List[int]] = None
    # longitude: float =Field(default=None)
class Approve(BaseModel):
    # id: int
    user_id:str =Field(default=None)

class Engage(BaseModel):
    # id: int
    provider_id:str =Field(default=None)
    service_id:int =Field(default=None)

class Message(BaseModel):
    # id: int
    content:str =Field(default=None)
    
class PriceSchema(BaseModel):
    # id: int
    price: float =Field(default=None)

class ProviderUpdateSchema(BaseModel):
    first_name: str =Field(default=None)
    user_id: str =Field()
    last_name: str =Field(default=None)
    area_name: str =Field(default=None)
    phone: str = Field(default=None)
    email: str =Field(default=None)
    nat_id: str =Field(default=None)
    county: str =Field(default=None)
    emm_cont_1: str =Field(default=None)
    emm_cont_2: str =Field(default=None)
    subs: list = Field(default=None)

class ProviderQuery(BaseModel):
    args: str =Field(default=None)

class RateJob(BaseModel):
    job_id: int =Field(default=None)
    rate:float =Field(default=None)

class PasswordUpdate(BaseModel):
    old: str
    new: str
    confirm: str

class DeviceToken(BaseModel):
    device: str
    reg_token: str
  