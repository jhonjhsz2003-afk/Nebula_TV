@echo off
setlocal
cd /d "%~dp0"
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven nao foi encontrado no PATH.
  echo Instale/adicione o Maven e tente novamente.
  pause
  exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo Java nao foi encontrado no PATH.
  echo Instale o JDK 17 ou superior e tente novamente.
  pause
  exit /b 1
)
echo Iniciando Nebula TV...
mvn clean javafx:run
if errorlevel 1 pause
endlocal
