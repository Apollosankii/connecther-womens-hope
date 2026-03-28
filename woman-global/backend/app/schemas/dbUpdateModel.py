from pydantic import BaseModel

# Administrative Tasks
# I will transfer this to the other schemas
# Updating Service Des
class ServiceItem(BaseModel):
    description: str | None= None
    min_price: float | None= None
