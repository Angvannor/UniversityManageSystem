@echo off
rem ===========================================================================
rem  File: build.bat
rem  Purpose: compile and run the Campus Activity Management System
rem           (plain Java console program, JDBC + MySQL, no framework).
rem
rem  Usage:
rem    build.bat                  compile and run the console program
rem    build.bat compile          compile only
rem    build.bat DBUtil           compile then run DBUtil self-test
rem    build.bat clean            delete build output and recompile
rem
rem  Layout:
rem    src/                     Java source files (package com.hbk.activity)
rem    lib/                     third-party jars (MySQL JDBC driver)
rem    build/classes            compiled classes
rem ===========================================================================
setlocal enabledelayedexpansion

set "ROOT=%~dp0"
set "SRC=%ROOT%src"
set "LIB=%ROOT%lib"
set "OUT=%ROOT%build\classes"
set "CP=%OUT%"

rem build classpath: output dir first, then every jar in lib
if exist "%LIB%\*.jar" (
    for %%j in ("%LIB%\*.jar") do set "CP=!CP!;%%j"
)

if /i "%~1"=="clean" (
    if exist "%ROOT%build" rmdir /s /q "%ROOT%build"
    echo [clean] build directory removed
    exit /b 0
)

if not exist "%OUT%" mkdir "%OUT%"

rem collect all java sources into a list file (avoids very long command lines)
set "FILELIST=%ROOT%build\sources.txt"
if exist "%FILELIST%" del "%FILELIST%"
for /r "%SRC%" %%f in (*.java) do echo %%f>>"%FILELIST%"

if not exist "%FILELIST%" (
    echo [ERROR] no .java file found under src\
    exit /b 1
)

echo ============================================
echo  Compiling ...
echo ============================================
javac -encoding UTF-8 -d "%OUT%" -cp "%CP%" "@%FILELIST%"
if errorlevel 1 (
    echo.
    echo [ERROR] compilation failed, see messages above
    exit /b 1
)
echo Compile OK. Output: %OUT%

if /i "%~1"=="compile" exit /b 0

if /i "%~1"=="DBUtil" (
    echo.
    echo Running DBUtil self-test ...
    chcp 65001 >nul
    java -Dfile.encoding=UTF-8 -cp "%CP%" com.hbk.activity.util.DBUtil
    exit /b 0
)

echo.
echo ============================================
echo  Running console program ...
echo ============================================
chcp 65001 >nul
java -Dfile.encoding=UTF-8 -cp "%CP%" com.hbk.activity.ui.MainMenu

endlocal