@echo off
setlocal EnableExtensions
chcp 65001 >nul
title SRE Platform - Launcher

set "ROOT=%~dp0"
cd /d "%ROOT%"

rem =====================================================================
rem  SRE Platform launcher  -  Author: Bishow Pandey
rem  Runs the full cloud SRE & security platform on Windows.
rem
rem  Design: "continue on failure" - if any service fails to start, a
rem  warning is printed and the remaining services are still started.
rem
rem  Option 1 = Full stack via Docker Compose (observability included)
rem  Option 2 = Local, no Docker: Java jars + Vite dev dashboard
rem  Option 3 = Stop everything started here
rem  Option 4 = Status of the services
rem =====================================================================

:menu
cls
echo.
echo   =============================================================
echo    SRE Platform - cloud reliability ^& security (Bishow Pandey)
echo   =============================================================
echo.
echo    1) Start full stack  (Docker Compose: services + observability + dashboard)
echo    2) Start local, no Docker  (Java jars + Vite dev on :5173)
echo    3) Stop everything
echo    4) Status
echo    5) Exit
echo.
set /p choice="  Choose an option (1-5): "
if errorlevel 1 exit /b 0

if "%choice%"=="1" goto docker_up
if "%choice%"=="2" goto local_up
if "%choice%"=="3" goto stop_all
if "%choice%"=="4" goto status
if "%choice%"=="5" exit /b 0
goto menu

rem ---------------------------------------------------------------------
rem  Option 1 - Docker Compose
rem ---------------------------------------------------------------------
:docker_up
call :check docker
if errorlevel 1 (
  echo   [WARN] Docker not found. Compose cannot start. Try option 2 (local).
  pause
  goto menu
)
echo.
echo  Building and starting the Docker Compose stack...
docker compose up --build -d
if errorlevel 1 echo  [WARN] docker compose reported an error - continuing with health checks.
echo.
call :wait_all_http "Docker stack" "http://localhost:8083/actuator/health" 45
if "%UP_COUNT%"=="0" echo  [WARN] No services responded yet - inspect with: docker compose ps
echo.
echo  URLs:
echo    Dashboard:       http://localhost:8080
echo    Payments API:    http://localhost:8081/actuator/health
echo    Orders API:      http://localhost:8082/actuator/health
echo    Incident engine: http://localhost:8083/incidents
echo    Prometheus:      http://localhost:9090
echo    Grafana:         http://localhost:3000  (admin/admin)
start "" "http://localhost:8080"
pause
goto menu

rem ---------------------------------------------------------------------
rem  Option 2 - Local, no Docker
rem  Every service is started independently; a failure never stops the
rem  others from being started.
rem ---------------------------------------------------------------------
:local_up
call :check java
if errorlevel 1 goto menu
call :check mvn
if errorlevel 1 goto menu
call :check node
if errorlevel 1 goto menu

echo.
echo  Building Java services (this can take a minute)...
mvn -f services\pom.xml package -DskipTests
if errorlevel 1 echo  [WARN] Maven build failed - trying to start existing jars, if any.

echo.
echo  Starting orders-api on :8082 ...
start "sre-orders-api" cmd /k "java -jar services\orders-api\target\orders-api.jar --server.port=8082"

echo  Starting payments-api on :8081 ...
start "sre-payments-api" cmd /k "java -jar services\payments-api\target\payments-api.jar --server.port=8081 --app.orders-url=http://localhost:8082"

echo  Starting incident-engine on :8083 ...
start "sre-incident-engine" cmd /k "java -jar services\incident-engine\target\incident-engine.jar --server.port=8083 --app.monitored-services=payments-api=http://localhost:8081,6,1.8.3;orders-api=http://localhost:8082,3,2.4.1"

echo  Starting Vite dev dashboard on :5173 ...
start "sre-dashboard" cmd /k "npm run dev --prefix dashboard"

echo.
echo  Waiting for services to come up (each is checked independently)...
set "UP_COUNT=0"
call :wait_all_http "orders-api"      "http://localhost:8082/actuator/health" 40
call :wait_all_http "payments-api"    "http://localhost:8081/actuator/health" 40
call :wait_all_http "incident-engine" "http://localhost:8083/actuator/health" 50
call :wait_all_http "vite dashboard"  "http://localhost:5173" 40

echo.
echo   ============================================================
echo    Startup summary  (%UP_COUNT% of 4 responded)
echo   ============================================================
call :probe "orders-api"      "http://localhost:8082/actuator/health"
call :probe "payments-api"    "http://localhost:8081/actuator/health"
call :probe "incident-engine" "http://localhost:8083/actuator/health"
call :probe "vite dashboard"  "http://localhost:5173"
echo   ============================================================
if not "%UP_COUNT%"=="4" echo  Some services are down. Open their console windows to see the error.
echo.
start "" "http://localhost:5173"
pause
goto menu

rem ---------------------------------------------------------------------
rem  Option 3 - Stop
rem ---------------------------------------------------------------------
:stop_all
echo.
echo  Stopping Docker Compose stack (if running)...
docker compose down >nul 2>&1
echo  Stopping local Java services and Vite (sre-platform)...
powershell -NoProfile -Command "$c=Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java|node' -and $_.CommandLine -match 'sre-platform|orders-api|payments-api|incident-engine|vite' }; $c | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }; 'Stopped ' + $c.Count + ' process(es).'"
echo.
pause
goto menu

rem ---------------------------------------------------------------------
rem  Option 4 - Status
rem ---------------------------------------------------------------------
:status
echo.
echo  Checking service status...
call :probe "Docker stack"   "http://localhost:8080" "dashboard (compose)"
call :probe "Docker backend" "http://localhost:8083/actuator/health" "incident-engine (compose)"
call :probe "Orders API"     "http://localhost:8082/actuator/health" "orders-api"
call :probe "Payments API"   "http://localhost:8081/actuator/health" "payments-api"
call :probe "Incident engine" "http://localhost:8083/actuator/health" "incident-engine"
call :probe "Vite dev"       "http://localhost:5173" "vite dashboard"
echo.
pause
goto menu

rem ---------------------------------------------------------------------
rem  Wait for one endpoint, bounded; never aborts.
rem  %1 = label, %2 = url, %3 = max tries (x ~2s)
rem ---------------------------------------------------------------------
:wait_all_http
set "n=0"
:wait_loop
set /a n+=1
curl.exe -s -o nul --max-time 2 "%~2"
if not errorlevel 1 (
  echo   [OK] %~1 is UP after %n% check(s)
  set /a UP_COUNT+=1
  goto :eof
)
if %n% geq %~3 (
  echo   [FAIL] %~1 did not respond after %~3 checks - continuing to next service.
  goto :eof
)
ping -n 2 127.0.0.1 >nul
goto wait_loop

:probe
curl.exe -s -o nul --max-time 3 "%~2"
if errorlevel 1 (
  echo   %~1 : DOWN
) else (
  echo   %~1 : UP
)
exit /b 0

:check
where %1 >nul 2>&1
if errorlevel 1 (
  echo   [ERROR] Required tool "%1" was not found on PATH.
  echo           Install it and try again.
  exit /b 1
)
exit /b 0

endlocal
