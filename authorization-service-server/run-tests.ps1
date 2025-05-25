Write-Host "Running Authorization Server Tests..." -ForegroundColor Green

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
    "$env:USERPROFILE\.m2\repository\log4j\log4j\1.2.17\log4j-1.2.17.jar",
    "$env:USERPROFILE\.m2\repository\com\amazonaws\aws-lambda-java-core\1.1.0\aws-lambda-java-core-1.1.0.jar",
    "$env:USERPROFILE\.m2\repository\com\amazonaws\aws-java-sdk-cognitoidp\1.11.123\aws-java-sdk-cognitoidp-1.11.123.jar",
    "$env:USERPROFILE\.m2\repository\com\amazonaws\aws-java-sdk-core\1.11.123\aws-java-sdk-core-1.11.123.jar",
    "$env:USERPROFILE\.m2\repository\org\apache\velocity\velocity\1.7\velocity-1.7.jar",
    "$env:USERPROFILE\.m2\repository\commons-collections\commons-collections\3.2.1\commons-collections-3.2.1.jar",
    "$env:USERPROFILE\.m2\repository\commons-lang\commons-lang\2.4\commons-lang-2.4.jar",
    "$env:USERPROFILE\.m2\repository\org\apache\httpcomponents\httpclient\4.5.2\httpclient-4.5.2.jar",
    "$env:USERPROFILE\.m2\repository\org\apache\httpcomponents\httpcore\4.4.4\httpcore-4.4.4.jar",
    "$env:USERPROFILE\.m2\repository\commons-codec\commons-codec\1.9\commons-codec-1.9.jar",
    "$env:USERPROFILE\.m2\repository\com\fasterxml\jackson\core\jackson-databind\2.15.2\jackson-databind-2.15.2.jar",
    "$env:USERPROFILE\.m2\repository\com\fasterxml\jackson\core\jackson-annotations\2.15.2\jackson-annotations-2.15.2.jar",
    "$env:USERPROFILE\.m2\repository\com\fasterxml\jackson\core\jackson-core\2.15.2\jackson-core-2.15.2.jar",
    "$env:USERPROFILE\.m2\repository\org\testng\testng\6.10\testng-6.10.jar",
    "$env:USERPROFILE\.m2\repository\com\beust\jcommander\1.48\jcommander-1.48.jar",
    "$env:USERPROFILE\.m2\repository\org\mockito\mockito-core\2.18.0\mockito-core-2.18.0.jar",
    "$env:USERPROFILE\.m2\repository\net\bytebuddy\byte-buddy\1.8.3\byte-buddy-1.8.3.jar",
    "$env:USERPROFILE\.m2\repository\net\bytebuddy\byte-buddy-agent\1.8.3\byte-buddy-agent-1.8.3.jar",
    "$env:USERPROFILE\.m2\repository\org\objenesis\objenesis\2.6\objenesis-2.6.jar"
) -join ";"

Write-Host "Classpath: $classpath" -ForegroundColor Yellow

# Run the test
try {
    java -cp $classpath server.core.ServerTestRunner
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