from sqlalchemy import create_engine
from sqlalchemy.engine import URL
from urllib.parse import quote_plus


# engine=create_engine(url)
# "postgresql+psycopg2://scott:tiger@host/dbname" -> Connection String
# engine=create_engine("postgresql+psycopg2://app_user:%s@localhost:5432/reports_demo"% quote_plus("DeusVult!"),echo=False) -> Dev
# engine=create_engine("postgresql+psycopg2://app_user1:%s@localhost:5432/fem_hope"% quote_plus("DeusVult!"),echo=False)
engine=create_engine("postgresql+psycopg2://app_user1:%s@db:5432/fem_hope"% quote_plus("DeusVult%21"),echo=True)
# db_url="postgresql+psycopg2://app_user1:%s@localhost:5431/fem_hope"% quote_plus("DeusVult!")
# db_url="postgresql+psycopg2://app_user1:%s@localhost:5432/fem_hope"% quote_plus("DeusVult!")