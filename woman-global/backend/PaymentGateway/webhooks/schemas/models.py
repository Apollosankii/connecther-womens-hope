from pydantic import BaseModel,Field,EmailStr,constr
from datetime import datetime
from typing import List, Optional


class Resource(BaseModel):
    id: str
    amount: float
    status: str
    system: str
    currency: str
    reference: str
    till_number: str
    sender_phone_number: str
    hashed_sender_phone : str
    origination_time: str
    sender_last_name : str
    sender_first_name : str
    sender_middle_name: str =Field(default=None)

class Event(BaseModel):
    type: str
    resource: Resource

class Link(BaseModel):
    self: str
    resource: str



class Payload(BaseModel):
    topic: str
    id: str
    created_at: str
    event: Event
    links: Link 
