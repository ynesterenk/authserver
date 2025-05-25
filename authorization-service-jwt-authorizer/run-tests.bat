@echo off
echo Running JWT Authorizer Tests...

REM Set classpath
set CLASSPATH=target\classes;target\test-classes

REM Add Maven dependencies to classpath
for /r "%USERPROFILE%\.m2\repository" %%i in (*.jar) do (
    echo %%i | findstr /i "nimbus-jose-jwt\|json-smart\|accessors-smart\|asm\|slf4j\|log4j\|jackson" >nul
    if not errorlevel 1 set CLASSPATH=!CLASSPATH!;%%i
)

REM Enable delayed variable expansion
setlocal enabledelayedexpansion

REM Run the test
java -cp "!CLASSPATH!" jwt.core.TestRunner

echo.
echo Test execution completed.
pause 