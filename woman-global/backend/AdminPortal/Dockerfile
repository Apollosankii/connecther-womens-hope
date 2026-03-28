# syntax=docker/dockerfile:1
FROM ubuntu:22.04

# install app dependencies
RUN apt-get update && apt-get install -y python3 python3-pip
WORKDIR /admin_portal

COPY requirements.txt .

RUN pip install -r requirements.txt

# install app
COPY . .

# final configuration
ENV FLASK_APP=app
# EXPOSE 5100
CMD ["gunicorn", "-w", "4", "-b","0.0.0.0:80", "app:app"]