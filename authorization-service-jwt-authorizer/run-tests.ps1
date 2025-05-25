Write-Host "Running JWT Authorizer Tests..." -ForegroundColor Green

# Build classpath with correct JAR versions
$classpath = @(
    "target\classes",
    "target\test-classes",
    "..\authorization-service-shared\target\classes",
    "$env:USERPROFILE\.m2\repository\com\nimbusds\nimbus-jose-jwt\4.30\nimbus-jose-jwt-4.30.jar",
    "$env:USERPROFILE\.m2\repository\net\minidev\json-smart\2.4.8\json-smart-2.4.8.jar",
    "$env:USERPROFILE\.m2\repository\net\minidev\accessors-smart\2.4.8\accessors-smart-2.4.8.jar",
    "$env:USERPROFILE\.m2\repository\org\ow2\asm\asm\5.1\asm-5.1.jar",
    "$env:USERPROFILE\.m2\repository\org\slf4j\slf4j-api\1.7.36\slf4j-api-1.7.36.jar",
    "$env:USERPROFILE\.m2\repository\org\slf4j\slf4j-log4j12\1.7.21\slf4j-log4j12-1.7.21.jar",
    "$env:USERPROFILE\.m2\repository\log4j\log4j\1.2.17\log4j-1.2.17.jar"
) -join ";"

Write-Host "Classpath: $classpath" -ForegroundColor Yellow

# Run the test
try {
    java -cp $classpath jwt.core.TestRunner
    $exitCode = $LASTEXITCODE
    
    if ($exitCode -eq 0) {
        Write-Host "`nTests completed successfully!" -ForegroundColor Green
    } else {
        Write-Host "`nTests failed with exit code: $exitCode" -ForegroundColor Red
    }
} catch {
    Write-Host "Error running tests: $_" -ForegroundColor Red
}

Write-Host "`nPress any key to continue..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") 