@echo off
rem ===========================================================================
rem  File: build.bat
rem  Purpose: compile and run the Campus Activity Management System
rem           (plain Java console program, JDBC + MySQL, no framework).
rem
rem  Usage:
rem    build.bat                           compile then run the console program
rem    build.bat compile                   compile only
rem    build.bat run com.hbk.xxx.SomeClass compile then run the given class
rem    build.bat test-all                  compile then run all automated tests
rem    build.bat web                       start the REST API server (port 8080)
rem    build.bat DBUtil                    compile then run DBUtil self-test
rem    build.bat clean                     delete build output
rem
rem  Layout:
rem    src/                  Java source files (package com.hbk.activity)
rem    lib/                  third-party jars (MySQL JDBC driver)
rem    build/classes         compiled classes
rem ===========================================================================
setlocal enabledelayedexpansion

set "ROOT=%~dp0"
set "SRC=%ROOT%src"
set "LIB=%ROOT%lib"
set "OUT=%ROOT%build\classes"
set "CP=%OUT%"

rem classpath: compiled classes first, then every jar in lib
if exist "%LIB%\*.jar" (
    for %%j in ("%LIB%\*.jar") do set "CP=!CP!;%%j"
)

if /i "%~1"=="clean" (
    if exist "%ROOT%build" rmdir /s /q "%ROOT%build"
    echo [clean] build directory removed
    exit /b 0
)

if not exist "%OUT%" mkdir "%OUT%"

rem collect all java sources under src into a list file
set "FILELIST=%ROOT%build\sources.txt"
if exist "%FILELIST%" del "%FILELIST%"
for /r "%SRC%" %%f in (*.java) do echo %%f>>"%FILELIST%"

if not exist "%FILELIST%" (
    echo [ERROR] no .java file found under src\
    exit /b 1
)

echo ============================================
echo  Compiling all sources under src\ ...
echo ============================================
javac -encoding UTF-8 -d "%OUT%" -cp "%CP%" "@%FILELIST%"
if errorlevel 1 (
    echo.
    echo [ERROR] compilation failed, see messages above
    exit /b 1
)
echo Compile OK.

if /i "%~1"=="compile" exit /b 0

chcp 65001 >nul

if /i "%~1"=="run" (
    if "%~2"=="" (
        echo [ERROR] usage: build.bat run com.hbk.activity.tool.UserDaoTest
        exit /b 1
    )
    echo.
    echo Running %~2 ...
    echo --------------------------------------------
    java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "%CP%" %~2
    exit /b 0
)

if /i "%~1"=="DBUtil" (
    echo.
    echo Running DBUtil self-test ...
    echo --------------------------------------------
    java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "%CP%" com.hbk.activity.util.DBUtil
    exit /b 0
)

rem ---------------------------------------------------------------------------
rem  test-all: run every automated test class in sequence (V2.0)
rem           Requires MySQL running and db/schema.sql already executed.
rem           ApiV2SmokeTest is NOT included here because it needs the API
rem           server running; run it separately with 'build.bat run'.
rem ---------------------------------------------------------------------------
if /i "%~1"=="test-all" (
    echo.
    echo ============================================
    echo  Running all automated tests ...
    echo ============================================
    for %%t in (
        com.hbk.activity.tool.UserDaoTest
        com.hbk.activity.tool.ActivityDaoTest
        com.hbk.activity.tool.RegistrationDaoTest
        com.hbk.activity.tool.DaoV2SmokeTest
        com.hbk.activity.tool.AuthServiceTest
        com.hbk.activity.tool.ActivityServiceTest
        com.hbk.activity.tool.RegistrationServiceTest
        com.hbk.activity.tool.ServiceV2SmokeTest
        com.hbk.activity.tool.RegressionV2Test
    ) do (
        echo.
        echo --------------------------------------------
        echo  %%t
        echo --------------------------------------------
        java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "%CP%" %%t
    )
    echo.
    echo ============================================
    echo  All tests finished.
    echo  ApiV2SmokeTest needs the API server running:
    echo    terminal 1: build.bat web
    echo    terminal 2: build.bat run com.hbk.activity.tool.ApiV2SmokeTest
    echo ============================================
    exit /b 0
)

rem ---------------------------------------------------------------------------
rem  web: start the V2.0 REST API server (JDK built-in HttpServer, port 8080)
rem       frontend dev server proxies /api here
rem ---------------------------------------------------------------------------
if /i "%~1"=="web" (
    echo.
    echo Starting web API server on port 8080 ...
    echo --------------------------------------------
    java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "%CP%" com.hbk.activity.web.WebServerMain
    exit /b 0
)

echo.
echo ============================================
echo  Running console program ...
echo ============================================
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "%CP%" com.hbk.activity.ui.MainMenu

endlocal