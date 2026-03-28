@echo off
cd /d "%~dp0"
if not exist app.py (echo Run from woman-global\backend\AdminPortal & exit /b 1)
set FLASK_APP=app
set FLASK_ENV=development
echo ConnectHer Admin Portal - http://127.0.0.1:5020
echo Preview: http://127.0.0.1:5020/preview
python -m flask run --port 5020
