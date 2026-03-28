import k2connect
from decouple import config

your_client_id= config('kopo_application_key')
your_client_secret= config('kopo_secret')


k2connect.initialize('your_client_id', 'your_client_secret', 'https://sandbox.kopokopo.com/oauth/token')
token_service = k2connect.Tokens
access_token_request = token_service.request_access_token()
access_token = token_service.get_access_token(access_token_request)
access_token # =>  "myApplicationAccessToken"

# On success the response has JSON structured like this

{
  "access_token": "JCGQXLrlfuOqdUYdTcLz3rBiCZQDRvdWIUPkw++GMuGhkem9Bo",
  "token_type": "Bearer",
  "expires_in": 3600,
  "created_at": 1556012342
}
