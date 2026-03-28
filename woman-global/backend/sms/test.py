import requests


headers = {
    'x-authorization': 'M2EzZjkxYjc0NGEzNDBhOTJiYzlkZTc4MTc0ZmJh',
    'email': 'admin@womanshope.org',
    'Content-Type': 'application/json',
    # 'Cookie': 'XSRF-TOKEN=eyJpdiI6Ikw2TXNEc0VoWlpiRnBadGRYWTA0a2c9PSIsInZhbHVlIjoid0h0TDhjNmRUV1Z3K0Y2ZmRJSkJJcWdueExuRHNyXC9ETmRFa0M4WVdoTjR2djBaSUNYQ3ZtekV6RXNqWk9acVRPbEVMbStGdlJaZjhGM2xqS0NxMmVBPT0iLCJtYWMiOiJjYjUxMzNhODBiZDM4NmNiMjA3MWQ1MTk1MGVmZDU0MWZhYjAwNDEwNTExM2IwMTZjOGJkOTE4ZTVhZjA5ZjFhIn0%3D; ujumbe_session=eyJpdiI6IlUzNGtMU3NrSVk2ZTdrNEZpS1RiaEE9PSIsInZhbHVlIjoicDN6MmRxaFIxME5BZTdyRDlnakc2NWxSQnRieEdrbkROa1VuS3YxdWV6MmpmWTJLMHF5YXZMaVY2bjh0dkhoOGhMNFRIWU1QM2ZqeWtJdkRleU5LekE9PSIsIm1hYyI6IjA4MDQ3MTVhODJhZmE5OWVlMmI5Zjk2NDVkODRhOTcxNDY5OWY3MDUzYmE5YjM5NGM4NWU2ZjZkODhhNGRkZTgifQ%3D%3D',
}

json_data = {
    'data': [
        {
            'message_bag': {
                'numbers': '0711950607',
                'message': 'Hildah requires your urgent assistance. She is currently at: https://maps.google.com. ConnectHer',
                'sender': 'UJUMBESMS',
                'source_id': '12345_a unique_identifier_for_each_message',
                'delivery_report_endpoint': 'https://you_link_to_post_the_delivery_report',
            },
        },
    ],
}

response = requests.post('https://ujumbesms.co.ke/api/messaging',headers=headers, json=json_data)
print(response.json())