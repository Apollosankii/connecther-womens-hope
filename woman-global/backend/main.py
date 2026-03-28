import uvicorn
import json
import uuid
import requests
from typing import Annotated
from fastapi import FastAPI,Depends,File, UploadFile
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from fastapi.security import HTTPAuthorizationCredentials,HTTPBearer
from app.schemas.model import (SignUpSchema,LoginSchema,AdminLoginSchema,ProviderSignUpSchema,
                               ServiceSchema,ServiceUpdateSchema,DocTypeSchema,LiveLocSchema,SubSchema,
                               AdminProviderSignUp,Approve,Engage,Message,PriceSchema,ProviderUpdateSchema,ProviderQuery,RateJob,PasswordUpdate,
                               DeviceToken)
from app.schemas.dbUpdateModel import ServiceItem
from app.util.utils import get_hashed_password,verify_password,gen_user_id
from app.auth.jwt_handler import sign_JWT,decode_JWT
from app.auth.jwt_bearer import jwtBearer
from models.dbfx import (register_user,register_admin,login_user,login_admin,add_provider_info,admin_add_provider,
                         add_new_service,get_services,get_service,update_service,create_doc_type,fetch_user,
                         add_prof_pic,get_prof_pic,update_live_loc,get_docs,admin_register_docs,admin_update_loc,
                         provider_near_me,update_subscriptions,list_providers,list_app_users,list_approvals,
                         get_provider,approve_provider,load_chats,load_quotes,
                         generate_quote,start_chat,update_chat,emm_call,convert_quote,complete_jobs,pending_jobs
                         ,pay,all_complete_jobs,all_pending_jobs,get_location,get_provider_x,update_prov_x,search_prov,
                         rate_a_job,check_password,update_password,update_device_token)
from datetime import datetime
from sms.ujumbe import distress_call
# from cnxn.postg import 
app=FastAPI()
# # users=[]
# seekers=[]
app.mount("/static", StaticFiles(directory="service_img"), name="service_img")
# creators=[]
security = HTTPBearer() 

# @app.get("/users/seekers",dependencies=[Depends(jwtBearer())],tags=["List Users"])
# def greet():
#     global seekers
#     return {"User":seekers}
# @app.get("/users/creators",dependencies=[Depends(jwtBearer())],tags=["List Users"])
# def greet():
#     global creators
#     return {"User":creators}
####################################################################################################################################
################ User Registration #################################################################################
@app.post("/user/sign-up",tags=["Registration"])
def add_user(user:SignUpSchema):
    # global users
    # user.id=len(users)+1
    user.password=get_hashed_password(user.password)
    user.user_id=gen_user_id(user.phone)
    # Remove Spaces and Format numbers correctly
    user.phone='0'+user.phone.replace(" ","")[-9:]
    # seekers.append(user.model_dump())
    app_user=user.model_dump()
    # Tag "mobile" as registration platform
    # app_user["reg_platform"]='mobile'
    response=register_user(app_user)

    # token=sign_JWT(user.phone)
    return {"info":"Registration",
            "Response":response}

# ### Admin Sign Up
@app.post("/admin/sign-up",tags=["Administrator","Registration"])
def add_admin(user:SignUpSchema):
    # global users
    # user.id=len(users)+1
    user.password=get_hashed_password(user.password)
    user.user_id=gen_user_id(user.phone)
    # seekers.append(user.model_dump())
    app_user=user.model_dump()
    # Tag "mobile" as registration platform
    # app_user["reg_platform"]='mobile'
    response=register_admin(app_user)

    # token=sign_JWT(user.phone)
    return {"info":"Registration",
            "Response":response}


@app.post("/user/provider/sign-up",tags=["Registration"])
def add_provider(user:ProviderSignUpSchema,credentials:Annotated[HTTPAuthorizationCredentials,Depends(jwtBearer())]):
    decoded=decode_JWT(credentials)
    # decoded=credentials

    user=user.model_dump()
    selector=decoded["userID"]
    # Append Mobile as Service Provider Registration Channel
    user['reg_platform']='mobile'
    # Resolve Date issue
    user['birth_date']=datetime.strptime(user['birth_date'],'%d/%m/%Y')
    db_msg=add_provider_info(user,selector)
    return {"msg": db_msg}

@app.post("/user/provider/testoken",tags=["Registration"])
def add_provider(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    decoded=decode_JWT(credentials.credentials)
    return {"scheme": credentials.scheme, "credentials": credentials.credentials,"decoded":decoded}
# ####Admin Creating a Service Provider####################################################################################
#  
@app.post("/add/provider",tags=["Administration"])
def add_provider(user:AdminProviderSignUp):
    print(user.model_dump())
    kyc={
        'first_name': user.first_name,
        'last_name': user.last_name,
        'phone': user.phone,
        'email': user.email,
        'title': user.title,
        'gender':user.gender,
        'birth_date': user.birth_date,
        'country': user.country,
        'county':user.county,
        'area_name': user.area_name,
        'nat_id': user.nat_id,
        'emm_cont_1':user.emm_cont_1,
        'emm_cont_2':user.emm_cont_2,
        'prof_pic':user.prof_pic
    }
    documents = user.documents
    location = user.location
    subs = user.subs

    # print(kyc)
    # print(documents)
    # print(location)
    # print(subs)
    # return 'test'
    # user_dict =user.model_dump()['kyc']
    # Collect Provider Data to create user
    try:
        kyc['user_id']=gen_user_id(kyc["phone"])
        # User Password initialized to user Phone number
        kyc['password']=get_hashed_password(kyc["phone"])
        kyc['WH_badge']=True
        kyc['service_provider']=True
        kyc['birth_date']=datetime.strptime(kyc['birth_date'],'%d/%m/%Y')
        # Inject the registration Platform
        kyc['reg_platform']='WHportal'

    except:
        kyc['user_id']=gen_user_id(kyc["phone"])
        kyc['password']=get_hashed_password(kyc["phone"])
        kyc['WH_badge']=True
        kyc['service_provider']=True
        kyc['birth_date']=datetime.strptime('1/1/1990','%d/%m/%Y')
        # Inject the registration Platform
        kyc['reg_platform']='WHportal'
    
    response = admin_add_provider(kyc)
    # print(kyc)
    # Update Document Upload Information
    user_id = response[1]
    if documents:
        admin_register_docs(docs_list=documents,user_id=user_id)
    # Update Location
    if location:
        admin_update_loc(user_id=user_id,loc_dict=location)
    # Update Subscriptions
    if subs:
        update_subscriptions(user_id=user_id,sub_list=subs)

    # user_docs=user.docs.model_dump()
    return response[0]
#####################################################################
############## Admin List All Providers #############################
@app.get("/list/providers",tags=['Administration'])
def get_providers(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # print("Iam Legend")  
    return list_providers()
########################################################################################################################################
# @app.post("/user/creator/sign-up",tags=["Creator"])
# def add_creator(user:CreatorSignUpSchema):
#     # global users
#     # user.id=len(users)+1
#     user.password=get_hashed_password(user.password)
#     creators.append(user.model_dump())
#     token=sign_JWT(user.phone)
#     return {"info":"User Added Successfully",
#             "token":token}
@app.post("/login",tags=["Login"])
def login(user:LoginSchema):
    # Pass Login Values to Endpoint
    # REctify user phone
    user.phone='0'+user.phone.replace(" ","")[-9:]

    reg_user_pass=login_user(user.phone)
    if reg_user_pass == 'fail':
        return {"info":"Username or Password Incorrect"}
    else:
        if verify_password(user.password,reg_user_pass["hash"]):
            token=sign_JWT(reg_user_pass["user_id"],role='user')
            return {"info":"Login Successfull",
                            "token":token,
                            "provider":reg_user_pass["provider"]}
        else:
            return {"info":"Username or Password Incorrect"}

############################ Admin Login #############################
@app.post("/admin/login",tags=["Login"])
def login(user:AdminLoginSchema):
    # Pass Login Values to Endpoint
    reg_user_pass=login_admin(user.email)

    if verify_password(user.password,reg_user_pass["hash"]):
        token=sign_JWT(reg_user_pass["user_id"],role='admin')
        # print(token)
        return {"info":"success",
                        "token":token}
    else:
        return {"info":"Username or Password Incorrect"}
    
@app.post("/update/password",tags=["Change Password"])
def password_change(password:PasswordUpdate,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # Pass Login Values to Endpoint
    
    decoded=decode_JWT(credentials.credentials)
    user_id = decoded["userID"]
    reg_user_pass=check_password(user_id)

    if verify_password(password.old,reg_user_pass):
        key=get_hashed_password(password.new)
        return update_password(user_id,key)
        # print(token)
        return {"info":"success",
                        "token":token}
    else:
        return {"info":"Username or Password Incorrect"}


############################################################################################
########################Services#################################
@app.post("/add/service",tags=["Administration"])
def add_service(service:ServiceSchema):
    # Pass Login Values to Endpoint
    svr_resp=add_new_service(service.model_dump())
    # x=service.model_dump()
    # print(service)
    # print(x)
    # print(type(service.model_dump()))
    return {'msg':svr_resp}


@app.get("/services",tags=["Administration"])
def services():
    # Get All Services
    return {'services':get_services()}

@app.get("/service/{service_id}",tags=["Administration"])
def service(service_id: int):
    # Get Specific Service
    return {'service':get_service(int(service_id))}

@app.post("/service/{service_id}/update",tags=["Administration"])
def service_update(service:ServiceItem,service_id):
    # Get Specific Service
    # print(service.model_dump())
    response=update_service(service_id,service.model_dump())

    return {'Status':response}
# Return Images
@app.get("/service/images",tags=["User Operations"])
async def read_service_pictures(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    
    IMAGEDIR="profpics/"
    # get random file from the image directory
    decoded=decode_JWT(credentials.credentials)
    img_path=get_prof_pic(decoded["userID"])
    path = "profpics/"+img_path
     
    return FileResponse(path)

################################################################
############## Documents ######################################
@app.post("/create/doc",tags=["Administration"])
def create_docs(document:DocTypeSchema):
    # Pass Login Values to Endpoint
    doc=document.model_dump()

    return {'msg':create_doc_type(doc)}


@app.get("/documents",tags=["Administration"])
def docs():
    return get_docs()

#################################################################
##################### App User ##################################
@app.get("/users/me",tags=["User Operations"])
def load_profile(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
     # Pass Login Values to Endpoint
    decoded=decode_JWT(credentials.credentials)
    print(decoded)
    print("TYPE: {}".format(type(decoded)))

    user=fetch_user(decoded['userID'])

    return {'profile':user}
# Images Endpoint
@app.post("/profpic/upload/",tags=["User Operations"])
async def create_upload_file(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)],file: UploadFile = File(...)):
    IMAGEDIR = "profpics/"
    file.filename = f"{uuid.uuid4()}.jpg"
    contents = await file.read()
    decoded=decode_JWT(credentials.credentials)
    #save the file
    try:
        with open(f"{IMAGEDIR}{file.filename}", "wb") as f:
            f.write(contents)

        return add_prof_pic(decoded["userID"],{"prof_pic":file.filename})
    except Exception as e:
        return {"Debug": e}

@app.get("/profpic/me",tags=["User Operations"])
async def read_prof_pic(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    
    IMAGEDIR="profpics/"
    # get random file from the image directory
    decoded=decode_JWT(credentials.credentials)
    img_path=get_prof_pic(decoded["userID"])
    path = "profpics/"+img_path
     
    return FileResponse(path)

@app.get("/user/profpic/{user_id}",tags=["User Operations"])
async def read_prof_pic(user_id:str,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    
    IMAGEDIR="profpics/"
    # get random file from the image directory
    decoded=decode_JWT(credentials.credentials)
    img_path=get_prof_pic(user_id)
    if img_path !=None:
        path = "profpics/"+img_path
        return FileResponse(path)
    else:
        return 'No Image'

@app.post("/provider/engage",tags=["User Operations"])
def engage_provider(engagement:Engage,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
     # Pass Login Values to Endpoint
    decoded=decode_JWT(credentials.credentials)
    print(decoded['userID'])
    seeker=decoded['userID']
    provider=engagement.provider_id
    service = engagement.service_id

    # print("Seeker: {}".format(seeker))
    # print("Proviider: {}".format(provider))
    # print("Service: {}".format(service))
    # print("TYPE: {}".format(type(decoded)))
    payload ={
        'provider':provider,
        'seeker':seeker,
        'service_id':service
    }

    quote=generate_quote(payload)
    if quote == 'exists':
        return 'exist'
    else: 

        chat_code=start_chat(quote["id"])

        session = {
            'quote_code': quote["code"],
            'chat_code':chat_code
        }

        return session

    user=fetch_user(decoded['userID'])

    return {'profile':user}
@app.get("/quotes/",tags=["User Operations"])
def get_quotes(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # Endpoint Called when sending texts about a quote
    # return [('db49ca',)]
    return load_quotes(decode_JWT(credentials.credentials)['userID'])

# This endpoint supports sending messages against a quote 
# Based on the Chat
@app.post("/chat/{chat_code}",tags=["User Operations"])
def chat(message:Message,chat_code:str,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # Endpoint Called when sending texts about a quote
    payload ={
        'content':message.content,
        'sender_id':decode_JWT(credentials.credentials)['userID'] 
    }

    return update_chat(payload,chat_code)

@app.get("/chats/{quote_code}",tags=["User Operations"])
def engage_provider(quote_code:str,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # Endpoint Called when sending texts about a quote

    return load_chats(quote_code)

@app.post("/hire/{quote_code}",tags=["User Operations"])
def hire(quote_code:str,price:PriceSchema,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # Endpoint Called when sending texts about a quote

    return convert_quote(quote_code,price.price)

@app.post("/job/pay/{job_id}",tags=["User Operations"])
def pay_for_service(job_id:int,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # Endpoint Called when sending texts about a quote

    return pay(job_id)

@app.get("/jobs/complete",tags=["User Operations"])
def list_jobs_complete(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # Endpoint Called when sending texts about a quote
    user_id = decode_JWT(credentials.credentials)['userID'] 
    return complete_jobs(user_id)

@app.get("/jobs/pending",tags=["User Operations"])
def list_jobs_pending(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # Endpoint Called when sending texts about a quote

    user_id = decode_JWT(credentials.credentials)['userID']
    return pending_jobs(user_id)

@app.post("/rate/job",tags=["User Operations"])
def rate_job(rate:RateJob,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # Endpoint Called when sending texts about a quote

    return rate_a_job(rate.job_id,rate.rate)
############################################################################################
########## Admin Load App Users ############################################################
@app.get("/list/app_users",tags=['Administration'])
def get_app_users(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    decoded=decode_JWT(credentials.credentials)
    print(decoded)

    return list_app_users()

# Return Specific provider Details
@app.get("/get/provider/{user_id}",tags=["Administrator","Registration"])
def get_a_provider(user_id:str,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # seekers.append(user.model_dump())
    app_user=get_provider_x(user_id)
    # Tag "mobile" as registration platform
    # app_user["reg_platform"]='mobile'
    # token=sign_JWT(user.phone)
    return app_user

# Nikama hii word provider(singular) iko na shida
@app.get("/provider/{user_id}",tags=["Administration"])
def provider_details(user_id: str,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # Get Specific Service
    return get_provider(user_id)

@app.post("/edit/provider",tags=["Administration"])
def edit_provider(provider:ProviderUpdateSchema,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # Get Specific Service
    print(provider.model_dump())
    response = update_prov_x(provider.model_dump())
    print(response)
    return {"response":response}

# # List Users for Approval
# # Nikama hii word provider(singular) iko na shida
@app.get("/providers/approvals",tags=['Administration'])
def get_approvals(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    print("Akianango")
    return list_approvals()

@app.post("/provider/approve",tags=['Administration'])
def approve(user:Approve,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # print("Akianango")

    return approve_provider(user.user_id)

@app.get("/all_jobs/pending/",tags=['Administration'])
def every_pending_jobs(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # print("Akianango")

    return all_pending_jobs()

@app.get("/all_jobs/complete/",tags=['Administration'])
def every_complete_job(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    print("Akianango")

    return all_complete_jobs()


###########################################################################################################
########### Location Operations ###########################################################################
@app.get("/location/me",tags=["User Operations"])
async def get_my_loc(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    decoded=decode_JWT(credentials.credentials)
    response=get_location(decoded["userID"])
    print(response)
    return response

@app.post("/liveloc/me",tags=["User Operations"])
async def set_live_loc(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)],loc:LiveLocSchema):
    decoded=decode_JWT(credentials.credentials)
    response=update_live_loc(decoded["userID"],loc.model_dump())

    return {'msg':response}


@app.get("/provider/near/me/{service_id}",tags=["User Operations"])
async def provider_proximity(service_id: str,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):
    # get user
    decoded=decode_JWT(credentials.credentials)
    
    return provider_near_me(decoded["userID"],service_id)

@app.post("/update/subscriptions/",tags=["User Operations"])
def update_subs(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)],subs:SubSchema):
    # get user
    decoded=decode_JWT(credentials.credentials)
    
    return subs


@app.get("/my/subscriptions/",tags=["User Operations"])
def update_subs(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)],subs:SubSchema):
    # get user
    decoded=decode_JWT(credentials.credentials)
    
    return subs


# @app.post("/admin/login",tags=["Login"])
# def login(user:LoginSchema):
#     # Pass Login Values to Endpoint
#     reg_user_pass=login_user(user.phone)
    
#     if verify_password(user.password,reg_user_pass):
#         token=sign_JWT(user.phone)
#         return {"info":"Login Successfull",
#                         "token":token}
#     else:
#         return {"info":"Username or Password Incorrect"}
# @app.post("/creator/login",tags=["user"])
# def login_user(user:LoginSchema):
#     # global users
#     # user.id=len(users)+1
#     # user.password=get_hashed_password(user.password)
#     select_user=None
#     for each in creators:
#         if each["phone"] == user.phone:
#             if verify_password(user.password,each["password"]): 
#                 token=sign_JWT(user.phone)
#                 return {"info":"Login Successfull",
#                         "token":token}
#             else:
#                 return {"info":"Username or Password Incorrect"}

# Panic Button
@app.get("/help",tags=['User Operations'])
def panic_button(credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):

    user=decode_JWT(credentials.credentials)['userID']

    call = distress_call(emm_call(user))
    # print(call.json())

    return 'Help is on the Way'

@app.post("/search/",tags=['User Operations'])
def search(query:ProviderQuery,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):

    # user=decode_JWT(credentials.credentials)['userID']
    x=query.args
    print(x)
    return search_prov(x)
    # return 'Executed'

@app.post("/update/device_token",tags=['User Operations'])
def device_reg_token(device:DeviceToken,credentials:Annotated[HTTPAuthorizationCredentials, Depends(security)]):

    user_id=decode_JWT(credentials.credentials)['userID']
    response = update_device_token(user=user_id,token=device.reg_token,device_name=device.device)

    return response
    
    
    
