from models.models import sessionmaker
from cnxn.postg import engine
from models.models import (User,Service,DocType,LiveLocation,
                           Document,Subscription,Quote,Chat,Message,Job,Administrator,ScoreCard,UserDevice)
from datetime import datetime
from app.util.utils import gen_user_id,random_string
from sqlalchemy import or_
from Notifications.main import send_new_msg_notification,send_new_hire_notification
from sms.ujumbe import send_hire_sms

session=sessionmaker()(bind=engine)
####### User Registration ###################################
def register_user(user_dict_obj):
    user=User(user_dict_obj)
    
    # Check if user exists
    if session.query(User.phone).filter(User.phone==user.phone).scalar() is None:
        session.add(user)
        try:
            print("Try Block 1")
            session.commit()
            print("Try Block 2")
            user_id_FK=session.query(User.id).filter(User.user_id==user.user_id).scalar()
            loc=LiveLocation({'user_id':user_id_FK})
            session.add(loc)
            session.commit()
            print("Try Block 3")
            user_id=user_dict_obj['user_id']
            session.close()
                    
            return 'ok',user_id
        except Exception as e:
            print("Try Block E")
            return e
            # return {"Debug": "Unable to Save"}
    else:
        return 'User Already Exists'
########## Register Admin ##########################################
def register_admin(user_dict_obj):
    user=Administrator(user_dict_obj)
    
    # Check if user exists
    if session.query(Administrator.email).filter(Administrator.email==user.email).scalar() is None:
        session.add(user)
        try:
            session.commit()
            session.close()
                    
            return 'ok'
        except Exception as e:
            return e
            # return {"Debug": "Unable to Save"}
    else:
        return 'Account Already Exists'
            
    
def login_user(phone,**kwargs):
    target_user=session.query(User).filter(User.phone==phone).first()

    if target_user:
        return {"hash":target_user.password,"user_id":target_user.user_id,"provider":target_user.service_provider}
    else:
        return 'fail'
    
def login_admin(email,**kwargs):
    target_user=session.query(Administrator).filter(Administrator.email==email).first()

    if target_user:
        return {"hash":target_user.password,"user_id":target_user.user_id}
    else:
        return 'No Such User'

def get_user(phone):
    target_user=session.query(User).filter(User.phone==phone).first()

    if target_user:
        return target_user.password,target_user.user_id
    else:
        return 'No Such User'
    
def add_provider_info(update_dict,selector):
    # Updates missing information from user
    try:
        user= session.query(User).filter(User.user_id==selector).first()
        user.update(update_dict)
        session.add(user)
        session.commit()
        return 'ok'
    
    except Exception as e:
        print(e)
        return {"info":'Save Failed', "Error":e}
    
################# Admin Provider Registration ########################
def admin_add_provider(provider_dict):
    register=register_user(provider_dict)
    print(register)

    # return 'test'
    if type(register) is tuple:
        if register[0] =='ok':
            user_id=register[1]
            return 'ok', user_id
    else:
        session.query(Quote).filter(or_(Quote.provider_id==id,Quote.client_id==id),Quote.converted==False).order_by(Quote.id.desc()).all()
        print(register)
        return 'fail'




##############################################################
# Services Functions #########################################    
def add_new_service(service_dict):
    try:
        service=Service(service_dict)
        session.add(service)
        session.commit()
        return 'Service Added Successfully'
    except Exception as e:
        return {'Error':e}

    
def get_services():
    try:
        services_db=session.query(Service).all()
        services=[]

        for each in services_db:
            services.append(each.asdict())

        return services
    
    except Exception as e:
        return {'Error':e}
    
def get_service(service_id):
    try:
        service_db=session.query(Service).filter(Service.id==service_id).first()
        service=service_db.asdict()
        return service
    
    except Exception as e:
        return {'Error':e}

def update_service(service_id,update_dict):
    try:
        srvs2bUpdatedObj=session.query(Service).filter(Service.id==service_id).first()
        srvs2bUpdated=srvs2bUpdatedObj.updatedict()
        if update_dict==srvs2bUpdated:
            return {'msg':'No Update Required'}
        else:
            for key,value in update_dict.items():
                setattr(srvs2bUpdatedObj,key,value)
            session.add(srvs2bUpdatedObj)
            session.commit()
            # session.close()
            return {'msg':'Success','dict':update_dict}
    except Exception as e:
        return 'Errrr'
                

# ###########################################################
# Documents Endpoints #######################################
def create_doc_type(doc_dict):
    # This function Creates a document type e.g National ID, Good Conduct...etc
    doc=DocType(doc_dict)
    try: 
        session.add(doc)
        session.commit()
        session.close()
        return 'Document Updated Successfuly'  
    
    except Exception as e:
        return {'error':e}


def get_docs():
    # Function returns all document types created in case of
    # uploads. It also provides labels and ids for form elements
    try:
        docs_db=session.query(DocType).all()
        doc_types=[]

        for each in docs_db:
            doc_types.append(each.asdict())
        # print(doc_types)
        return doc_types
    
    except Exception as e:
        return {'Error':e}
    

###############Admin Upload Documents ########################
def admin_register_docs(docs_list,user_id):
    user_FK = session.query(User.id).filter(User.user_id==user_id).scalar()
    try:
        doc_objects =[]
        for each in docs_list:
            each["user_id"]=user_FK
            each["doc_type_id"]=int(each["doc_type_id"])
            each["verif_date"] = datetime.today()
            # Documents uploaded are Automatically Verified
            each["verified"]= True
            # Add Doc descriptions to Doc Object
            doc_objects.append(Document(each))
        
        session.add_all(doc_objects)
        session.commit()
        return 'Ok'
    
    except Exception as e:
        return 'Err Saving Documents'
##############################################################

##############################################################
def fetch_user(user_id):
    try:
        user=session.query(User).filter(User.user_id==user_id).first()
        return user.asdict()
    except Exception as e:
        return {"Debug":e}
    
def provider_near_me(user_id,service_id):
    try:
        user=session.query(User).filter(User.user_id==user_id).first()
        # Get User Object inorder to run its providers_within_radius() method
        providers=user.providers_within_radius(service_id)
        all_providers=session.query(User.id).filter(User.service_provider==True).all()
        all_providers=[each[0] for each in all_providers]
        # Filter all users near User and with Users that are actually Service Providers
        # Using set intersection A&B
        providers=list(set(providers) & set(all_providers))
        proximal_providers=[]
        for id in providers:
            user=session.query(User).filter(User.id==id).first()
            proximal_providers.append(user.provider_profile())

        return proximal_providers
    
    except Exception as e:
        return {"Debug":e}



def add_prof_pic(user_id,filename_dict):
    try:
        user=session.query(User).filter(User.user_id==user_id).first()
        user.update(filename_dict)
        session.add(user)
        session.commit()
        return 'Upload Successful'

    except Exception as e:
        return {"Debug":e}
    
def get_prof_pic(user_id):
    return session.query(User.prof_pic).filter(User.user_id==user_id).first()[0]
###############################################################################################
# get all service providers
def list_providers():
    providers=session.query(User).filter(User.service_provider==True).all()

    prov_list=[]

    for provider in providers:
        prov_list.append(provider.provider_snippet())

    return prov_list

def get_provider(user_id):
    provider=session.query(User).filter(User.user_id==user_id,User.service_provider==False,User.reg_platform=='mobile').first()
  
    return provider.provider_profile()

### Approve Provider ###########
def approve_provider(user_id):
    try:
        provider=session.query(User).filter(User.user_id==user_id).first()
        provider.service_provider=True
        session.add(provider)
        session.commit()
        return {'msg':'Approved'}
    except Exception as e:
        return {'msg':'Failed',
                'err':e}
############################################################################
################## Mapping Tools #########################################
###################################################################################
def get_location(user_id):
    user = session.query(User).filter(User.user_id==user_id).first()
    location = list(user.live_loc)
    location=location[0]
    
    return {'latitude':location.latitude,'longitude':location.longitude}


def update_live_loc(user_id,loc_dict):
    # This method initiates the first live location when someone signs up
    # IT also updates subsequent user live-location map updates 
    userFk=session.query(User.id).filter(User.user_id==user_id).scalar()
    loc=session.query(LiveLocation).filter(LiveLocation.user_id==userFk).first()
    loc.update_live_loc(loc_dict)
    try:
        session.add(loc)
        session.commit()
        session.close()

        return 'Live location Updated'
    except Exception as e:
        return {'Debug': e}
    
########## Admin Update Live Loc #############################
def admin_update_loc(user_id,loc_dict):

    return update_live_loc(user_id,loc_dict)
#################################################################

def update_subscriptions(user_id,sub_list):
    user_FK = session.query(User.id).filter(User.user_id==user_id).scalar()
    current_subs = session.query(Subscription.service_id).filter(Subscription.user_id==user_FK).all()
    current_subs_list = [each[0] for each in current_subs]
    current_subs_list = set(current_subs_list)
    print("Already Subbed to: ",current_subs_list)
    sub_list = [int(each) for each in sub_list]
    sub_list = set(sub_list)
    print("Want to Sub to: ",sub_list)
    print("Performing Set Difference")
    subs=list(sub_list.difference(current_subs_list))
    print(subs)

    sub_objects = []

    for each in subs:
        init_dict = {}
        init_dict["user_id"]=user_FK
        init_dict["service_id"]= int(each)
        sub_objects.append(Subscription(init_dict))
    
    session.add_all(sub_objects)
    session.commit()
    session.close()

# def add_to_subscriptions(user_id,sub_list):
#     user_FK = session.query(User.id).filter(User.user_id==user_id).scalar()
    
#     sub_objects = []

#     for each in sub_list:
#         init_dict = {}
#         init_dict["user_id"]=user_FK
#         init_dict["service_id"]= int(each)
#         sub_objects.append(Subscription(init_dict))
    
#     session.add_all(sub_objects)
#     session.commit()
#     session.close()

######################################################################
def list_app_users():
    app_users=session.query(User).filter(User.service_provider!=True).all()

    app_list=[]

    for user in app_users:
        app_list.append(user.provider_snippet())

    return app_list

def list_approvals():
    app_users=session.query(User).filter(User.service_provider==False,User.reg_platform=='mobile').all()

    app_list=[]

    for user in app_users:
        app_list.append(user.provider_snippet())

    return app_list

############################################################
#####Quote Manenos

def generate_quote(quote_items):
    provider=quote_items['provider']
    seeker=quote_items['seeker']
    service_id=quote_items['service_id']

    seeker_id=session.query(User.id).filter(User.user_id==seeker).scalar()
    provider_id=session.query(User.id).filter(User.user_id==provider).scalar()
    # Check to Test Whether there are Open quotes with user
    similar_exist = session.query(Quote).filter(Quote.provider_id==provider_id,Quote.client_id==seeker_id,Quote.converted==False,Quote.service_id==service_id).first()
    if similar_exist:
        # Load Conversation on App
        return 'exists'
    else:

        service_id=quote_items['service_id']
        quote_code = gen_user_id(random_string(10))

        payload ={'quote_code':quote_code,
                'client_id':seeker_id,
                'provider_id':provider_id,
                'service_id':service_id

        }
        quote = Quote(payload)
        session.add(quote)
        session.commit()

        id= session.query(Quote.id).filter(Quote.quote_code==quote_code).scalar()
        quote_dict = {
            "id":id,
            "code":quote_code

        }

        return quote_dict

def start_chat(quote_code):

    chat_code=gen_user_id(random_string(10))
    payload ={
        'chat_code':chat_code,
        'quote_id':quote_code
    }
    chat=Chat(payload)
    session.add(chat)
    session.commit()

    return chat_code
def get_msg_rcpt(sender_id,chat_id):
    user_id = session.query(User.id).filter(User.user_id==sender_id).scalar()
    chat = session.query(Chat).filter(Chat.id==chat_id).first()
    quote = session.query(Quote).filter(Quote.id==chat.quote_id).first()

    # Continue here...Get quote then get provider or client whose id
    # corresponds to user_id, The receipient is the one that doesnt match
    if quote.provider.user_id == sender_id:
        # Get rcpt id
        user=session.query(User).filter(User.user_id==quote.client.user_id).first()
        devices=session.query(UserDevice.reg_token).filter(UserDevice.user_id==user.id).all()
        clean_dev =[each[0] for each in devices]
        
        return {'name':quote.provider.first_name,'devices':clean_dev}
    if quote.client.user_id == sender_id:
        user=session.query(User).filter(User.user_id==quote.provider.user_id).first()
        devices=session.query(UserDevice.reg_token).filter(UserDevice.user_id==user.id).all()
        clean_dev =[each[0] for each in devices]

        return {'name':quote.client.first_name,'devices':clean_dev}

def update_chat(payload,chat_code):
    chat=session.query(Chat).filter(Chat.chat_code==chat_code).first()
    chat_id=chat.id
    payload['chat_id']=chat_id
    message=Message(payload)

    session.add(message)
    session.commit()
    # Notify Recipient
    # Distinguish who the sender is from rcpt
    sender=payload["sender_id"]
    recipient=get_msg_rcpt(sender,chat_id)
    print(recipient)
    send=send_new_msg_notification(recipient)
    print(send)
    return 'Message Posted'

def load_quotes(user_id):
    id = session.query(User.id).filter(User.user_id==user_id).scalar()
    quote_list=[]
    # Return List of quotes organized in descending order, last to first
    quotes= session.query(Quote).filter(or_(Quote.provider_id==id,Quote.client_id==id),Quote.converted==False).order_by(Quote.id.desc()).all()

    for each in quotes:
        # 1. Check Prevents User from sending their own quote
        if not each.chat:
            # quote ={
            # 'quote_code':each.quote_code,
            # 'provider':each.provider.first_name,
            # 'client':each.client.first_name,
            # 'service':each.service.name,
            # 'text':'Greet Client..',#Get last messages associated with this quote
            # 'time':'',#Get last messages time of this quote
            # }
            continue
        elif each.chat[0].messages:
            quote ={
            'quote_code':each.quote_code,
            'chat_id': each.chat[0].chat_code,#.chat_code,
            'provider':each.provider.first_name,
            'client':each.client.first_name,
            'service':each.service.name,
            'text':each.chat[0].messages[-1].content,#Get last messages associated with this quote
            'time':each.chat[0].messages[-1].time.strftime('%H:%M %p'),#Get last messages time of this quote
        }
        else:
            quote ={
            'quote_code':each.quote_code,
            'chat_id': each.chat[0].chat_code,#.chat_code,
            'provider':each.provider.first_name,
            'client':each.client.first_name,
            'service':each.service.name,
            'text':'Start Chat..',#Get last messages associated with this quote
            'time':'',#Get last messages time of this quote
            }
            

        quote_list.append(quote)

    return quote_list

def load_chats(quote_code):
    
    
    quote_id=session.query(Quote.id).filter(Quote.quote_code==quote_code).scalar()
    chat= session.query(Chat).filter(Chat.quote_id==quote_id).first()

    threads=chat.messages
    conversation=[]

    for each in threads:
        thread ={
            'time':each.time,
            'sender_id':each.sender_id,
            'message': each.content,
        }
        conversation.append(thread)

    return conversation

def convert_quote(quote_code,final_price):
    quote = session.query(Quote).filter(Quote.quote_code==quote_code).first()
    # Update status of quote
    quote.final_price=final_price
    quote.converted=True
    quote.conversion_date=datetime.now()

    # Create Location String
    location = session.query(LiveLocation.latitude,LiveLocation.longitude).filter(LiveLocation.user_id==quote.client_id).first()
    location = location._to_tuple_instance()
    GmapsStr='https://www.google.com/maps/search/?api=1&query={}%2C{}'.format(location[0],location[1])

    job_init ={
        'location':GmapsStr,
        'price':final_price,
        'quote_id': quote.id
    }
    try:
        job = Job(job_init)
        session.add_all([quote,job])
        session.commit()
        # Bake Notifications here
        reg_tokens=session.query(UserDevice.reg_token).filter(UserDevice.user_id==quote.provider_id).all()
        devices =[each[0] for each in reg_tokens]

        recipient={'name':quote.client.first_name,'devices':devices,'phone':quote.provider.phone}
        print(recipient)
        send_new_hire_notification(recipient)
        # Add SMS here
        send_hire_sms(recipient)
        return {'msg':'ok'}
    except Exception as e:
        print(e)
        return {'msg':'Error Creating Job'}
##############JObs ##################################
def complete_jobs(user_id):
    id = session.query(User.id).filter(User.user_id==user_id).scalar()
    quotes = session.query(Quote.id).filter(or_(Quote.provider_id==id,Quote.client_id==id),Quote.converted==True).all()
    # Create a List of quote_ids to filter jobs
    quotes = [each[0] for each in quotes]
    jobs = session.query(Job).filter(Job.quote_id.in_(quotes),Job.complete==True).all()

    jobz=[]

    for each in jobs:
        if each.score:
            jobz.append(
            {
                'client':each.quote.client.first_name,
                'provider':each.quote.provider.first_name,
                'Price':each.price,
                'Service': each.quote.service.name,
                'location':each.location,
                'job_id': each.id,
                'rated':True,
                'score': each.score[0].rate,
                'date':each.date


            }
        )
        else:
            jobz.append(
            {
                'client':each.quote.client.first_name,
                'provider':each.quote.provider.first_name,
                'Price':each.price,
                'Service': each.quote.service.name,
                'location':each.location,
                'job_id': each.id,
                'rated':False,
                'date':each.date


            }
        )

    return jobz

def pending_jobs(user_id):
    id = session.query(User.id).filter(User.user_id==user_id).scalar()
    quotes = session.query(Quote.id).filter(or_(Quote.provider_id==id,Quote.client_id==id),Quote.converted==True).all()
    # Create a List of quote_ids to filter jobs
    quotes = [each[0] for each in quotes]
    jobs = session.query(Job).filter(Job.quote_id.in_(quotes),Job.complete==False).all()

    jobz=[]

    for each in jobs:
        jobz.append(
            {
                'client':each.quote.client.first_name,
                'provider':each.quote.provider.first_name,
                'Service': each.quote.service.name,
                'Price':each.price,
                'location':each.location,
                'job_id': each.id


            }
        )
    return jobz 
#
# 
# Manage Payments
def pay(job_id):
    try:
        job = session.query(Job).filter(Job.id==job_id).first()
        job.complete = True
        session.add(job)
        session.commit()
        return {'status':'Payment Request Made',}

    except Exception as e:
        print(e)
        return {'status':'Payment Failed'}


def rate_a_job(job_id,rate):
    rating_exist = session.query(ScoreCard).filter(ScoreCard.job_id==job_id).first()
    if rating_exist:
        return 'exists'
    else:
        score=ScoreCard()
        score.job_id = job_id
        score.rate = rate
        session.add(score)
        session.commit()

        return 'Rating Posted'
#     
# This Function returns the two Emmergency Contacts
def emm_call(user_id):
    damsel=session.query(User).filter(User.user_id==user_id).first()
    list_of_contacts=[damsel.emm_cont_1,damsel.emm_cont_2]
    clean_contacts =[]
    location = list(damsel.live_loc)
    location =[location[0].latitude,location[0].longitude]

    first_name= damsel.first_name


    for each in list_of_contacts:
        if each:
            clean_contacts.append(each)

    return clean_contacts,location,first_name

#Returns All Jobs based on Flags(Either True or False)
def all_jobs(Flag):
    jobs = session.query(Job).filter(Job.complete==Flag).all()
    # Create a List of quote_ids to filter jobs
    # quotes = [each[0] for each in quotes]
    # jobs = session.query(Job).filter(Job.complete==False).all()

    jobz=[]
    if len(jobs)<1:
        return []
    else:
        for job in jobs:
            jobz.append(
                {
                    'quote':job.quote.quote_code,
                    'client':job.quote.client.first_name,
                    'provider':job.quote.provider.first_name,
                    'price':job.price,
                    'date':job.date,
                    'service':job.quote.service.name,
                    
                }
            )
    return jobz

def all_pending_jobs():
    return all_jobs(False)


def all_complete_jobs():
    return all_jobs(True)

def get_provider_x(user_id):
    user=session.query(User).filter(User.user_id==user_id).first()

    return user.provider_profile()

def update_prov_x(payload):
    try:
        user=session.query(User).filter(User.user_id==payload['user_id']).first()

        for key,value in payload.items():
            setattr(user,key,value)
        session.add(user)
        session.commit()
            # session.close()
        # Commit Subscriptions
        if payload['subs']:
            try:
                update = update_subscriptions(payload['user_id'],payload['subs'])
                return {'msg':'Success',}
            except Exception as e:
                return 'Subscrptions not Updated'
        else:
            pass
        # return user.emm_cont_2
        return {'msg':'Provider Updated',}
    except Exception as e:
        return e
        return 'Errrr'
def search_prov(query):
    results = session.query(User).filter((User.first_name.icontains(query)|\
                                          User.last_name.icontains(query)),\
                                        User.service_provider==True ).order_by(User.id.asc()).all()
    prov_list=[]

    for provider in results:
        prov_list.append(provider.provider_snippet())
        
    return prov_list

def check_password(user_id):

    return session.query(User.password).filter(User.user_id==user_id).scalar()

def update_password(user_id,new_password):
    user=session.query(User).filter(User.user_id==user_id).first()
    user.password=new_password
    session.add(user)
    session.commit()

    return 'Password Updated'

def update_device_token(user,token,device_name):
    # Get user's ID
    user_id = session.query(User.id).filter(User.user_id==user).scalar()
    # Check if record exists
    token_record = session.query(UserDevice).filter(UserDevice.user_id==user_id,UserDevice.device==device_name).first()
    if token_record:
        token_record.reg_token=token
        token_record.reg_token_date=datetime.now()
        session.add(token_record)
        session.commit()
        return 'Token Updated'
    else:
        init_dict={'reg_token':token,
                   'reg_token_date':datetime.now(),
                   'device':device_name,
                   'user_id':user_id}
        
        dev_rec=UserDevice(init_dict)
        session.add(dev_rec)
        session.commit()
        return 'New Token Created'


