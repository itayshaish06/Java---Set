@echo off

:: Compile the project
echo Compiling the project...
call mvn clean compile
if %errorlevel% neq 0 (
    echo Compilation failed.
    pause
    exit /b %errorlevel%
)

:: Run the project
echo Running the project...
call java -cp target/classes bguspl.set.Main
if %errorlevel% neq 0 (
    echo Project execution failed.
    pause
    exit /b %errorlevel%
)

echo.
echo Set terminated

:: Clean the project
echo Cleaning the project files...
call mvn clean
if %errorlevel% neq 0 (
    echo mvn clean failed.
    pause
    exit /b %errorlevel%
)

pause
