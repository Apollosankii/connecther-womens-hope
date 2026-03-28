from twilio.rest import Client
from decouple import config

account_sid = config('twil_account_sid')
auth_token = config('twil_auth_token')

client = Client(account_sid, auth_token)

def distress_call():
    message = client.messages.create(

    body="Join Earth's mightiest heroes. Like Kevin Bacon.",

    from_="+254711950607",

    to="+254112363785",
)
    return message.body 