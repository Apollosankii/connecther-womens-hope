import firebase_admin
from firebase_admin import credentials,messaging
import os

abs_path=os.path.abspath('Notifications/firebaseKey.json')
cred = credentials.Certificate(abs_path)
firebase_admin.initialize_app(cred)

# See documentation on defining a message payload.
# This registration token comes from the client FCM SDKs.

# See documentation on defining a message payload.
def send_new_msg_notification(payload):
    data = {
        "type": "new_message",
    }
    # print("Message Payload: ",payload)
    notifs=[]
    if len(payload["devices"])>1:
        for token in payload["devices"]:
            message = messaging.Message(
                notification=messaging.Notification(
                    title='New Message',
                    body = payload["name"]
                    ),token=token,
                    data=data
            )
            notifs.append(message)
        response=messaging.send_each(notifs)
        print("Push sent to: ",response)
        return response
    elif len(payload["devices"])==1:
        message = messaging.Message(
                notification=messaging.Notification(
                    title='New Message',
                    body = payload["name"]
                    ),token=payload["devices"][0],
                    data=data
            )
        response=messaging.send(message)
        print("Push sent to: ",response)
        return response
    else:
        pass

def send_new_hire_notification(payload):
    data = {
        "type": "hire",
    }
    # print("Message Payload: ",payload)
    notifs=[]
    if len(payload["devices"])>1:
        for token in payload["devices"]:
            message = messaging.Message(
                notification=messaging.Notification(
                    title='New Job Hire',
                    body = "{} hired you. Check app for Job directions".format(payload["name"])
                    ),token=token,
                    data=data,
                    
            )
            notifs.append(message)
        response=messaging.send_each(notifs)
        print("Push sent to: ",response)
        return response
    elif len(payload["devices"])==1:
        message = messaging.Message(
                notification=messaging.Notification(
                    title='New Job Hire',
                    body = "{} hired you. Check app for Job directions".format(payload["name"])
                    ),token=payload["devices"][0],
                    data=data
            )
        response=messaging.send(message)
        print("Push sent to: ",response)
        return response
    else:
        pass

    # Send a message to the device corresponding to the provided
    # registration token.
    # response = messaging.send(message)
# Response is a message ID string.
# print('Class Objects:')
# print(dir(messaging.send))