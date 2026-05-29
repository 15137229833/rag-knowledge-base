@echo off
setlocal

set SCRIPT_DIR=%~dp0
set JAR_PATH=%SCRIPT_DIR%target\rag-knowledge-backend-0.0.1-SNAPSHOT.jar
set CONFIG_PATH=%SCRIPT_DIR%llm-local.properties

if not exist "%CONFIG_PATH%" (
  echo [ERROR] Config not found: %CONFIG_PATH%
  echo Copy llm-local.properties.example to llm-local.properties and set your API key.
  exit /b 1
)

if not exist "%JAR_PATH%" (
  echo [ERROR] Jar not found: %JAR_PATH%
  echo Run: mvn -DskipTests package
  exit /b 1
)

echo Starting backend with config: %CONFIG_PATH%
java -jar "%JAR_PATH%" --spring.config.additional-location=file:"%CONFIG_PATH%"
