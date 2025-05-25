# Step 1: Shared Components and Infrastructure Adapters Migration

## Context
This is **Step 1 of 6** in the AWS to Azure migration. You are creating the foundational Azure infrastructure adapters and shared components that will be used by all three functions (AuthServer, JwtAuthorizer, BasicAuthenticator).

**Goal**: Establish the Azure foundation layer while maintaining AWS compatibility through adapters.

## What You're Building
- Azure SDK infrastructure adapters
- Request/Response conversion utilities
- Environment variable management
- Error handling adapters
- Logging infrastructure
- Backward compatibility layer

## Dependencies to Address
Based on the AWS analysis, you need to replace these shared dependencies:
```java
// AWS Lambda Runtime (shared across all functions)
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

// AWS Utilities (shared)
import com.amazonaws.util.StringUtils;
import com.amazonaws.regions.Regions;
```

## Your Tasks

### 1. Create Azure Functions Infrastructure Adapters

Create these adapter classes in `authorization-service-shared/src/main/java/shared/infrastructure/azure/adapters/`:

- **RequestResponseAdapter.java**: Convert Azure Functions requests/responses to AWS Lambda format
- **AwsContextAdapter.java**: Implement AWS Lambda Context interface using Azure ExecutionContext
- **AzureLambdaLoggerAdapter.java**: Implement AWS LambdaLogger using Azure Logger

### 2. Environment Variable Management

Create `AzureEnvironmentConfig.java` in `authorization-service-shared/src/main/java/shared/infrastructure/azure/config/`:
- Map AWS environment variables to Azure equivalents
- Support backward compatibility
- Generate B2C URLs (authority, JWKS)

### 3. Error Handling Infrastructure

Create `AzureExceptionAdapter.java` in `authorization-service-shared/src/main/java/shared/infrastructure/azure/exceptions/`:
- Convert MSAL exceptions to domain exceptions
- Maintain AWS error message compatibility
- Map Azure Resource Manager exceptions

### 4. Response Format Compatibility

Create `ResponseFormatAdapter.java` in `authorization-service-shared/src/main/java/shared/infrastructure/azure/adapters/`:
- Maintain exact AWS API Gateway response format
- Support error responses with AWS structure
- Preserve CORS headers and status codes

### 5. Azure Monitoring Integration

Create `ApplicationInsightsAdapter.java` in `authorization-service-shared/src/main/java/shared/infrastructure/azure/monitoring/`:
- Replace CloudWatch metrics with Application Insights
- Track requests, exceptions, dependencies
- Maintain metric naming compatibility

### 6. Update Maven Dependencies

Add Azure SDK dependencies to `authorization-service-shared/pom.xml`:
- Azure Functions Java SDK
- Azure Identity (Managed Identity)
- Azure Key Vault Secrets
- Azure Application Insights
- MSAL4J for Azure AD B2C
- Nimbus JOSE JWT

## Success Criteria
- ✅ All adapter classes compile and pass unit tests
- ✅ Environment configuration properly maps AWS to Azure variables
- ✅ Request/response conversion maintains AWS Lambda compatibility
- ✅ Error handling preserves AWS exception behavior
- ✅ Response format exactly matches AWS API Gateway structure
- ✅ Azure SDK dependencies properly integrated
- ✅ Application Insights logging functional
- ✅ No breaking changes to existing interfaces

## Testing Commands
```bash
# Run shared component tests
cd authorization-service-shared
mvn clean test

# Verify no compilation errors in dependent modules
cd ../authorization-service-server
mvn compile

cd ../authorization-service-jwt-authorizer  
mvn compile

cd ../authorization-service-basic-authenticator
mvn compile
```

## Next Steps
After completing this step:
1. All shared Azure infrastructure will be ready
2. Step 2 can begin migrating the AuthServer function
3. Existing AWS code remains functional during transition
4. Rollback capability maintained through adapters

## Risk Mitigation
- **Adapter Pattern**: Maintains AWS interfaces, minimizes breaking changes
- **Environment Compatibility**: Supports both AWS and Azure environment variables
- **Incremental Testing**: Each adapter can be tested independently
- **Backward Compatibility**: Response formats exactly match AWS API Gateway