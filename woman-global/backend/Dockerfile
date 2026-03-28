# syntax=docker/dockerfile:1
FROM ubuntu:22.04

# install app dependencies
RUN apt-get update && apt-get install -y python3 python3-pip
# install app dependencies
WORKDIR /connectHerAPI

COPY requirementsX.txt .

RUN pip install -r requirementsX.txt

# install app
COPY . .

# final configuration
# ENV FLASK_APP=app
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]