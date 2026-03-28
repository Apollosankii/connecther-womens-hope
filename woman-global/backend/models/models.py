from sqlalchemy import (
    create_engine,
    Column,
    Integer,
    ForeignKey,
    String,
    Float,
    Boolean,
    func,Sequence)
from sqlalchemy.types import DateTime,Date
from sqlalchemy.ext.hybrid import hybrid_property
# from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import relationship,sessionmaker,declarative_base
from sqlalchemy.sql import func
import datetime 
from geoalchemy2 import Geometry
from datetime import date,datetime
from models.models import sessionmaker
from cnxn.postg import engine

modelSession=sessionmaker()(bind=engine)


# Creating a Base Class From Where we can derive our classes
Base=declarative_base()


# Define our Table Schema
# ###############################################################################################################

class User(Base):
    __tablename__='users'

    # Define properties
    id=Column(Integer(),Sequence('users_id_seq'),primary_key=True)
    user_id=Column(String(),nullable=True)
    # KYC
    title=Column(String(),nullable=False)
    first_name=Column(String(),nullable=False)
    last_name=Column(String(),nullable=False)
    phone=Column(String(),nullable=False)
    email=Column(String(),nullable=True)
    gender=Column(String(),nullable=True)
    birth_date=Column(Date(),nullable=True)
    password=Column(String(),nullable=False)
    # Location Details
    country=Column(String(),nullable=True)
    county=Column(String(),nullable=True)
    area_name=Column(String(),nullable=True)
    prof_pic=Column(String(),nullable=True)
   
    # Registration Documents (Foreign Keys)
    nat_id=Column(String(),nullable=True)
    # Operations
    WH_badge=Column(Boolean(),default=False)
    online=Column(Boolean(),default=False)
    service_provider=Column(Boolean(),default=False)
    reg_date=Column(DateTime(timezone=True),default=date.today())
    # Emmercgency Contact
    emm_cont_1=Column(String(),nullable=True)
    emm_cont_2=Column(String(),nullable=True)
    # Add Reg_plat as Registration Platform (Mobile,WHPortal)
    reg_platform=Column(String(),nullable=True)

    # Relationship
    # Back references
    subscriptions=relationship('Subscription',back_populates='user')
    # provider=relationship('Job',back_populates='provider')
    # client=relationship('Job',back_populates='client')
    documents=relationship('Document',back_populates='user')
    live_loc=relationship('LiveLocation',back_populates='live_loc')
    devices=relationship('UserDevice',back_populates='user')
    # jobs=relationship('Job',back_populates='user')

    # __Repr__
    def __repr__(self):
        return 'User Name: {}'.format(self.first_name)
    
    # Methods
    # initiator
    def __init__(self,dictionary):
        for key,value in dictionary.items():
            setattr(self,key,value)

    def update(self,newdata):
        for key,value in newdata.items():
            setattr(self,key,value)


    def asdict(self):
        # Returns User profile information
        return {
                'first_name':self.first_name,
                'last_name':self.last_name,
                'phone':self.phone,
                'gender':self.gender,
                'DoB':self.birth_date,
                'country':self.country,
                'county':self.county,
                'Nat ID':self.nat_id,
                'locality':self.area_name,
                'pic':self.prof_pic,
                'user_name': self.user_id,
                'WH-Badge': self.WH_badge,
                'emm_cont_1': self.emm_cont_1,
                'emm_cont_2': self.emm_cont_2,
                   
                    }
    def provider_profile(self):
        # Method returns a service provider's profile
        return {
                'first_name':self.first_name,
                'last_name':self.last_name,
                'title':self.title,
                'user_name':self.user_id,
                'phone':self.phone,
                'nat_id':self.nat_id,
                'dob':self.birth_date,
                'gender':self.gender,
                'pic':self.prof_pic,
                'WH Badge':self.WH_badge,
                'area_name':self.area_name,
                'country':self.country,
                'county':self.county,
                'emm_cont_1': self.emm_cont_1,
                'emm_cont_2': self.emm_cont_2,
                    }
    
    def provider_snippet(self):
        # Method returns a service provider details to load Admin Panel
        return {
                'first_name':self.first_name,
                'last_name':self.last_name,
                'WH_Badge':self.WH_badge,
                'email':self.email,
                'phone':self.phone,
                'nat_id':self.nat_id,
                'user_id':self.user_id
                    }
    
    def get_geo(self):
        try:
            geo=modelSession.query(LiveLocation.geo).filter(LiveLocation.user_id==self.id).scalar()
            return geo
        except Exception as e:
            return {'error':e}
        
    def providers_within_radius(self,service_id, radius=10000):
    #     """Return all providers within a given radius (10,000 in meters) of this user."""
        providers=modelSession.query(LiveLocation).filter(func.ST_DistanceSphere(LiveLocation.geo, self.get_geo()) < radius).all()
        provider_keys=[]
        # Type Casted Values of type Subscription

        filt_providers=[]
        # 
        for provider in providers:
            provider_keys.append(provider.user_id)

        for key in provider_keys:
            conv_list=[]
            select = modelSession.query(User).filter(User.id==key).first()
            for each in select.subscriptions:
                conv_list.append(each.__str__())

            if service_id in conv_list:
                filt_providers.append(key)

        return filt_providers
    
    def subscribe(self,service_list):
        # Current Subs
        # curr_subs=modelSession.query(Subscription.service_id).filter(Subscription.user_id==self.id).all()
        curr_subs=[sub.service_id for sub in self.subscriptions]
        # Check for new service subscription
        resultant_subs=set(service_list).union(set(curr_subs))
        if resultant_subs == set(curr_subs):
            return 'Subscriptions Active'
        else:
            # Fetch new subscriptions service ids
            resultant_subs=list(set(service_list)-set(curr_subs))
            # create dictionary to instantiate subscriptions
            update_sub_objects=[]
            for each in resultant_subs:
                update_sub_objects.append(Subscription({'service_id':each,'user_id':self.id}))
            if len(update_sub_objects) > 1:
                modelSession.add_all(update_sub_objects)
            else:
                modelSession.add(update_sub_objects[0])
            try:
                # Update service_provider Flag in Users
                if self.service_provider==False:
                    self.service_provider=True
                    modelSession.add(self)
                    
                modelSession.commit()
                return 'Subscriptions Updated'

            except:
                return 'Unable to Update subscriptions'
class UserDevice(Base):
    __tablename__='devices'
    id=Column(Integer(),Sequence('services_id_seq'),primary_key=True) 
    device= Column(String(),nullable=False)
    reg_token =Column(String(),nullable=False)
    reg_token_date = Column(DateTime(timezone=True),default=None)

    # Foreign Key
    user_id=Column(Integer(),ForeignKey('users.id'))
    # Backward References
    user=relationship('User',back_populates='devices')
    # initiator
    def __init__(self,dictionary):
        for key,value in dictionary.items():
            setattr(self,key,value)

class Service(Base):
    __tablename__='services'

    # Define properties
    id=Column(Integer(),Sequence('services_id_seq'),primary_key=True)
    name=Column(String(),nullable=False)
    description=Column(String(),nullable=True)
    min_price=Column(Float(),nullable=False)
    service_pic = Column(String(),nullable=True)

    # Relationship
    # Back Populates
    subscriptions=relationship('Subscription',back_populates='service')
    # jobs=relationship('Job',back_populates='service')
    quotes=relationship('Quote',back_populates='service')
    # jobs=relationship('Job',back_populates='user')

    # __Repr__
    def __repr__(self):
        return 'Service Name: {}'.format(self.name)
    
    # Methods
    # initiator
    def __init__(self,dictionary):
        for key,value in dictionary.items():
            setattr(self,key,value)

    def asdict(self):
        return {"service_id":self.id,"name":self.name,"description":self.description,
                "min_price":self.min_price,
                "pic":self.service_pic}

    def updatedict(self):
        return {"description":self.description,"min_price":self.min_price}

class Subscription(Base):
    __tablename__='subscriptions'

    # Define properties
    id=Column(Integer(),Sequence('subscriptions_id_seq'),primary_key=True)

    # Foreign Keys
    user_id=Column(Integer(),ForeignKey('users.id'))
    service_id=Column(Integer(),ForeignKey('services.id'))

    # Back References
    # jobs=relationship('Job',back_populates='user')
    user=relationship('User',back_populates='subscriptions')
    service=relationship('Service',back_populates='subscriptions')


    # __Repr__
    def __repr__(self):
        # Return Service ID to make it easier to filter 
        # Service providers
        return str(self.service_id)
        # return 'Subscription ID: {}'.format(self.id)
    
    # Methods
    # initiator
    def __init__(self,dictionary):
        for key,value in dictionary.items():
            setattr(self,key,value)

class Quote(Base):
    __tablename__='quotes'
    id=Column(Integer(),Sequence('quotes_id_seq'),primary_key=True)
    date=Column(DateTime(timezone=True),default=date.today())
    conversion_date=Column(DateTime(timezone=True),default=None)
    final_price=Column(Float(),nullable=True)
    quote_code =Column(String(),nullable=True)
    converted = Column(Boolean(),default=False)
    # ForeignKeys
    client_id=Column(Integer(),ForeignKey('users.id'))
    provider_id=Column(Integer(),ForeignKey('users.id'))
    service_id=Column(Integer(),ForeignKey('services.id'))
    # Back References
    # jobs=relationship('Job',back_populates='user')
    # client=relationship('User',back_populates='client')
    # provider=relationship('User',back_populates='provider')
    client=relationship('User',foreign_keys=[client_id])
    provider=relationship('User',foreign_keys=[provider_id])
    service=relationship('Service',back_populates='quotes') 
    job=relationship('Job',back_populates='quote')  
    chat= relationship('Chat',back_populates='quote')

    # __Repr__
    def __repr__(self):
        return 'Quote ID: {}'.format(self.id)
    
    # Methods
    # initiator
    def __init__(self,dictionary):
        for key,value in dictionary.items():
            setattr(self,key,value)
    
 
class Chat(Base):
    __tablename__='chats'
    id=Column(Integer(),Sequence('chats_id_seq'),primary_key=True)
    # This is added to assist in chaining record creation
    # Quote -> Chat ID
    chat_code =Column(String(),nullable=True)
    # Foreign Keys
    quote_id=Column(Integer(),ForeignKey('quotes.id'))
    
    # Back Refs
    quote=relationship('Quote',back_populates='chat')  
    messages=relationship('Message',back_populates='chat')

    # Methods
    # initiator
    def __init__(self,dictionary):
        for key,value in dictionary.items():
            setattr(self,key,value)

class Message(Base):
    __tablename__='messages'
    id=Column(Integer(),Sequence('messages_id_seq'),primary_key=True)
    # Sender_id == user_id
    sender_id=Column(String(),nullable=True)
    content =Column(String(),nullable=True)
    time=Column(DateTime(timezone=True),default=datetime.now())

    # Foreign Key
    chat_id=Column(Integer(),ForeignKey('chats.id'))
    # Backrefs
    chat=relationship('Chat',back_populates='messages')

    # __Repr__
    def __repr__(self):
        return  'message: {}'.format(self.content)
                 
    # initiator
    def __init__(self,dictionary):
        for key,value in dictionary.items():
            setattr(self,key,value)
    
  

class Job(Base):
    __tablename__='jobs'

    # Define properties
    id=Column(Integer(),Sequence('jobs_id_seq'),primary_key=True)
    # Google Maps string
    location=Column(String())
    price=Column(Float(),nullable=True)
    date=Column(DateTime(timezone=True),default=date.today())
    payment_mode = Column(String(),nullable=True)
    paid = Column(Boolean(),default=False)
    complete = Column(Boolean(),default=False)
    # job_code = Column(String(),nullable=True)

    # Foreign Keys
    # client_id=Column(Integer(),ForeignKey('users.id'))
    # provider_id=Column(Integer(),ForeignKey('users.id'))
    quote_id=Column(Integer(),ForeignKey('quotes.id'))

    # Back References
    # jobs=relationship('Job',back_populates='user')
    # client=relationship('User',back_populates='client')
    # provider=relationship('User',back_populates='provider')
    # client=relationship('User',foreign_keys=[client_id])
    # provider=relationship('User',foreign_keys=[provider_id])
    # service=relationship('Service',back_populates='jobs')
    score=relationship('ScoreCard',back_populates='job')
    quote=relationship('Quote',back_populates='job') 

    # __Repr__
    def __repr__(self):
        return 'Job ID: {}'.format(self.id)
    
    # Methods
    # initiator
    def __init__(self,dictionary):
        for key,value in dictionary.items():
            setattr(self,key,value)

class ScoreCard(Base):
    __tablename__='job_score_card'

    # Define properties
    id=Column(Integer(),Sequence('job_score_card_id_seq'),primary_key=True)
    rate=Column(Integer(),default=1)

    # Foreign Keys
    job_id=Column(Integer(),ForeignKey('jobs.id'))

    # Back References
    # jobs=relationship('Job',back_populates='user')
    job=relationship('Job',back_populates='score')

    # __Repr__
    def __repr__(self):
        return 'Job ID: {}'.format(self.id)
    
    # Methods
    # initiator
    # def __init__(self,dictionary):
    #     for key,value in dictionary.items():
    #         setattr(self,key,value)

class DocType(Base):
    __tablename__='document_type'

    # Define properties
    id=Column(Integer(),Sequence('document_type_id_seq'),primary_key=True)
    name=Column(String(),nullable=False)
    # Back References
    # jobs=relationship('Job',back_populates='user')
    documents=relationship('Document',back_populates='docType')

    # __Repr__
    def __repr__(self):
        return 'Document Name: {}'.format(self.name)
    
    # Methods
    # initiator
    def __init__(self,dictionary):
        for key,value in dictionary.items():
            setattr(self,key,value)

    def asdict(self):
        return {"docType":self.id,"name":self.name}

class Document(Base):
    __tablename__='documents'

    # Define properties
    id=Column(Integer(),Sequence('documents_id_seq'),primary_key=True)
    name=Column(String(),nullable=False)
    verified=Column(Boolean(),default=False)
    # name=Column(String(),nullable=False)
    verif_date=Column(DateTime(timezone=True),nullable=True)

    # FOreign Keys
    doc_type_id=Column(Integer(),ForeignKey('document_type.id'))
    user_id=Column(Integer(),ForeignKey('users.id'))
    # Back References
    # jobs=relationship('Job',back_populates='user')
    docType=relationship('DocType',back_populates='documents')
    user=relationship('User',back_populates='documents')

    # __Repr__
    def __repr__(self):
        return 'Document Name: {}'.format(self.name)
    
    # Methods
    # initiator
    def __init__(self,dictionary):
        for key,value in dictionary.items():
            setattr(self,key,value)
######################################################################################################################################################################

class LiveLocation(Base):
    __tablename__='live_location'

    # Define properties
    id=Column(Integer(),Sequence('live_location_id_seq'),primary_key=True)
    latitude=Column(Float(),nullable=True)
    longitude=Column(Float(),nullable=True)
    geo=Column(Geometry(geometry_type="POINT"),nullable=True)

    # Foreign Key
    user_id=Column(Integer(),ForeignKey('users.id'))

#     # Relationship
#     # Back Populates
    live_loc=relationship('User',back_populates='live_loc')

    # __Repr__
    def __repr__(self):
        return  'Lat: {}, Long: {}'.format(self.latitude,self.longitude)
    
    # Methods
    # initiator
    def __init__(self,dictionary,):
        for key,value in dictionary.items():
            setattr(self,key,value)
        
    def update_live_loc(self,location_dict):
        for key,value in location_dict.items():
            setattr(self,key,value)
        self.geo='POINT({} {})'.format(self.longitude, self.latitude)

    # def providers_within_radius(self, radius=100000):
    #     """Return all cities within a given radius (in meters) of this city."""

    #     return session.query(LiveLocation).filter(func.ST_Distance_Sphere(LiveLocation.geo, self.geo) < radius).all()
# class Creator(Base):
#     __tablename__='creator'

#     # Define properties
#     id=Column(Integer(),primary_key=True)
#     first_name=Column(String(),nullable=False)
#     last_name=Column(String(),nullable=False)
#     title=Column(String(),nullable=True)
#     phone=Column(String(),nullable=False)
#     email=Column(String(),nullable=False)
#     password=Column(String(),nullable=False)
    
#     # Relationship
#     # Back Populates
#     # jobs=relationship('Job',back_populates='user')

#     # __Repr__
#     def __repr__(self):
#         return 'User Name: {}'.format(self.first_name)
    
#     # Methods
#     # initiator
#     def __init__(self,dictionary):
#         for key,value in dictionary.items():
#             setattr(self,key,value)
# ###############################################################################################################

# class Job(Base):
#     __tablename__='jobs'

#     # Define properties
#     id=Column(Integer(),primary_key=True)
#     name=Column(String(40),nullable=False)
#     username=Column(String(20),nullable=False)
    

#     # Relationships
#     user_id=Column(Integer(),ForeignKey('users.id'))
#     # Back References
#     user=relationship('User',back_populates='jobs')
    

#     # Methods
#     # initiator
#     def __init__(self,dictionary):
#         for key,value in dictionary.items():
#             setattr(self,key,value)

#     def __repr__(self):
#         return 'Job Name: {}'.format(self.name)
    
# ###############################################################################################################
# Transactions
class Administrator(Base):
    __tablename__='administrators'

    # Define properties
    id=Column(Integer(),Sequence('administrators_id_seq'),primary_key=True)
    first_name=Column(String(),nullable=True)
    last_name=Column(String(),nullable=True)
    email=Column(String(),nullable=True)
    phone = Column(String(),nullable=False)
    last_login = Column(DateTime(timezone=True),default=date.today())
    password = Column(String(),nullable=False)
    user_id = Column(String(),nullable=False)

     # Methods
    # initiator
    def __init__(self,dictionary,):
        for key,value in dictionary.items():
            setattr(self,key,value)
     
    
