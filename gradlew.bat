@echo off
setlocal EnableExtensions

rem PocketDoor Gradle bootstrapper
rem Downloads the official Gradle 9.5.1 distribution on first use,
rem then reuses it from .gradle-local.

set "PROJECT_DIR=%~dp0"
set "GRADLE_VERSION=9.5.1"
set "GRADLE_DIR=%PROJECT_DIR%.gradle-local\gradle-%GRADLE_VERSION%"
set "GRADLE_BAT=%GRADLE_DIR%\bin\gradle.bat"
set "ZIP=%PROJECT_DIR%.gradle-local\gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_BAT%" goto runGradle

if not exist "%PROJECT_DIR%.gradle-local" mkdir "%PROJECT_DIR%.gradle-local"

if not exist "%ZIP%" (
    echo.
    echo ========================================
    echo PocketDoor - téléchargement de Gradle %GRADLE_VERSION%
    echo ========================================
    echo.
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP%'"
    if errorlevel 1 (
        echo.
        echo ERREUR : impossible de telecharger Gradle.
        exit /b 1
    )
)

echo Extraction de Gradle %GRADLE_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%ZIP%' -DestinationPath '%PROJECT_DIR%.gradle-local' -Force"
if errorlevel 1 (
    echo.
    echo ERREUR : impossible d'extraire Gradle.
    exit /b 1
)

if not exist "%GRADLE_BAT%" (
    echo.
    echo ERREUR : Gradle n'a pas ete trouve apres extraction.
    exit /b 1
)

del /q "%ZIP%" >nul 2>&1

:runGradle
call "%GRADLE_BAT%" %*
set "EXITCODE=%ERRORLEVEL%"
endlocal & exit /b %EXITCODE%
