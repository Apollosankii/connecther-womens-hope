from typing import Union
from schemas.models import Payload

from fastapi import FastAPI, Request

app = FastAPI()


@app.get("/")
def read_root():
    return {"Hello": "World"}


@app.get("/items/{item_id}")
def read_item(item_id: int, q: Union[str, None] = None):
    return {"item_id": item_id, "q": q}

@app.post("/test/",tags=["Test"])
async def add_user(payload:Request):
    result = await payload.json()
    print(payload.client.host)
    print(result)
    return 'Alaa'
# if __name__ == "__main__":
#     uvicorn.run(app, host="0.0.0.0", port=30000)