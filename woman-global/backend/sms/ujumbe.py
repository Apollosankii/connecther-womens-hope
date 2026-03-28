import requests
from decouple import config
import json

def distress_call(details):
    woman_hope = '0722666296'
    numbers = details[0]
    # numbers.append(woman_hope)
    location = details[1]
    name = details[2]
    print(location)
    GmapsStr='https://www.google.com/maps/search/?api=1&query={}%2C{}'.format(location[0],location[1])

    headers ={"x-authorization":config("sms_token"),
              'Content-Type': 'application/json',
              "email":config("admin_email")}
    
    message="{} requires your urgent assistance! ".format(name)
    if len(numbers) > 1:
        numbers = ",".join(numbers)
    else:
        numbers =numbers[0]

    payload = {
    'data': [
        {
            'message_bag': {
                'numbers': numbers,
                'message': message +' ' + GmapsStr + '. ConnectHer',
                'sender': 'UJUMBESMS',
                'source_id': '12345_a unique_identifier_for_each_message',
                'delivery_report_endpoint': 'https://you_link_to_post_the_delivery_report',
            },
        },
    ],
}
    # payload=json.dumps(payload)
    url='http://ujumbesms.co.ke/api/messaging'

    response = requests.post('https://ujumbesms.co.ke/api/messaging',headers=headers, json=payload)
    print("Ujumbe API Response")
    print(response.json())
    
    return 'SOS SENT'

def send_hire_sms(payload):
    headers ={"x-authorization":config("sms_token"),
              'Content-Type': 'application/json',
              "email":config("admin_email")}
    
    message="{} has hired you. Check app for more details and directions".format(payload["name"])
    payload = {
    'data': [
        {
            'message_bag': {
                'numbers': payload["phone"],
                'message': message + '. ConnectHer',
                'sender': 'UJUMBESMS',
                'source_id': '12345_a unique_identifier_for_each_message',
                'delivery_report_endpoint': 'https://you_link_to_post_the_delivery_report',
                            },
            },
            ],
                }
    url='http://ujumbesms.co.ke/api/messaging'

    response = requests.post('https://ujumbesms.co.ke/api/messaging',headers=headers, json=payload)
    # print("Ujumbe API Response")
    # print(response.json())
    return response