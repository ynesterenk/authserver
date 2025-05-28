# Target Design Phase - Azure Architecture Design

## Context
You are designing the target Azure architecture for migrating the AWS authserver. This is Phase 2 focusing on creating the Azure component mapping and infrastructure design.

## AWS → Azure Component Mapping (from PRD)
| Current AWS | Target Azure | Migration Notes |
|-------------|--------------|-----------------|
| **Lambda (Java 11)** | Azure Functions (Java 11, Consumption Plan) | Keep handler signatures; convert env vars |
| **API Gateway (REST)** | Azure API Management (APIM) or Function Proxies | Use APIM for policy-based Basic-Auth fallback |
| **Cognito User Pool** | Azure AD B2C (custom policy) | Issue RS256 JWTs; publish OIDC metadata |
| **IAM policies** | Azure RBAC + System-Assigned Managed Identity | Inline policies → custom roles |
| **CloudFormation** | **Terraform (azurerm provider)** | One module per function + APIM instance |
| **CloudWatch Logs** | Azure Monitor + Log Analytics | Mirror dashboards |
| **S3 artifact bucket** | Azure Blob Storage | Stage Functions zip files |

## Requirements to Meet
- **Functional**: Identical REST paths and JSON payloads
- **Security**: RS256 JWTs from Azure AD B2C, secrets in Key Vault, PCI-DSS & SOX alignment
- **Performance**: ≤ 200ms warm p95 under 100 RPS; ≤ 500ms cold-start
- **Reliability**: 99.9% monthly SLA; active-passive across two Azure regions
- **Observability**: Structured logs to Log Analytics; distributed traces via Application Insights
- **Compliance**: Data residency in "westeurope"; backup in "northeurope"

## Your Tasks

### 1. Azure Functions Design
Design three Azure Functions to replace AWS Lambdas based on discovered architecture:

#### **AuthServer Function** (Replace ServerLambda/ProxyRequestHandler)
- **Handler**: `server.infrastructure.aws.lambda.ProxyRequestHandler`
- **Routes**: `/oauth/token` (client credentials), `/change-password`
- **Current Dependencies**: AWS Cognito Identity Provider, Lambda Runtime
- **Runtime**: Java 11, 512MB memory, 30s timeout
- **Triggers**: HTTP trigger with proxy integration
- **Environment Variables**: 
  - `AWS_COGNITO_USER_POOL_ID` → `B2C_TENANT_ID`
  - `AWS_COGNITO_CLIENT_ID` → `B2C_CLIENT_ID`
  - `AWS_REGION` → `AZURE_REGION`

#### **JwtAuthorizer Function** (Replace JwtAuthorizerLambda)
- **Handler**: `authorization.jwt.infrastructure.aws.lambda.AuthorizerRequestHandler`
- **Purpose**: JWT validation and IAM policy generation
- **Current Dependencies**: AWS IAM, Lambda Runtime, Remote JWKS
- **Runtime**: Java 11, 512MB memory, 30s timeout
- **Triggers**: Custom authorizer trigger
- **JWKS Source**: `https://cognito-idp.{region}.amazonaws.com/{poolId}/.well-known/jwks.json`
- **Environment Variables**:
  - `AWS_COGNITO_USER_POOL_ID` → `B2C_TENANT_ID`
  - `AWS_REGION` → `AZURE_REGION`

#### **BasicAuthenticator Function** (Replace BasicAuthenticatorLambda)
- **Handler**: `basic.infrastructure.aws.lambda.AuthorizerRequestHandler`
- **Purpose**: HTTP Basic Auth fallback for legacy clients
- **Current Dependencies**: AWS Cognito Identity Provider, Lambda Runtime
- **Runtime**: Java 11, 512MB memory, 30s timeout
- **Triggers**: Custom authorizer trigger
- **Environment Variables**:
  - `AWS_COGNITO_USER_POOL_ID` → `B2C_TENANT_ID`
  - `AWS_COGNITO_CLIENT_ID` → `B2C_CLIENT_ID`
  - `AWS_REGION` → `AZURE_REGION`

### 2. Azure AD B2C Configuration
Design B2C tenant to replace AWS Cognito User Pool functionality:

#### **OAuth 2.0 Client Credentials Flow**
- **Current Cognito Flow**: `AdminInitiateAuth` with `ADMIN_NO_SRP_AUTH`
- **B2C Equivalent**: Custom policy for service-to-service authentication
- **Authentication Parameters**: Username/password → Client ID/Secret
- **Challenge Handling**: `NEW_PASSWORD_REQUIRED` challenge support

#### **JWT Token Configuration**
- **Algorithm**: RS256 (maintain compatibility)
- **Claims Structure**: Preserve existing claim names and values
  - `username` claim for principal identification
  - `groups` claim for role-based access
  - `aud` (audience) claim for client validation
- **Token Lifetime**: Match current Cognito settings
- **JWKS Endpoint**: `https://{tenant}.b2clogin.com/{tenant}.onmicrosoft.com/{policy}/discovery/v2.0/keys`

#### **User Pool Migration**
- **Admin User**: `admin` with email `yevgen2005@gmail.com`
- **Groups**: `ApiGatewayFullAccess` group with appropriate permissions
- **Password Policy**: Minimum 8 chars, require upper/lower/numbers (match Cognito)
- **MFA**: Disabled (match current Cognito configuration)

#### **Application Registration**
- **Client Type**: Confidential client for server-to-server
- **Grant Types**: Client credentials flow
- **Redirect URIs**: Not applicable for client credentials
- **Secret Management**: Store in Azure Key Vault

### 3. API Management (APIM) Design
Design APIM to replace AWS API Gateway functionality:

#### **API Definitions**
- **Base Path**: `/` (root level, same as AWS API Gateway)
- **Proxy Resource**: `{proxy+}` pattern for catch-all routing
- **HTTP Methods**: `ANY` method support (GET, POST, PUT, DELETE)
- **Stage**: `v1` (match current AWS API Gateway stage)

#### **Backend Service Configuration**
- **AuthServer Function**: Route `/oauth/token` and `/change-password`
- **Custom Authorizers**: 
  - JWT Authorizer for protected endpoints
  - Basic Auth Authorizer for legacy client fallback
- **Health Check**: `/health` endpoint for monitoring

#### **Authorization Policies**
- **JWT Validation Policy**: 
  ```xml
  <validate-jwt header-name="Authorization" failed-validation-httpcode="401">
    <openid-config url="https://{tenant}.b2clogin.com/{tenant}.onmicrosoft.com/{policy}/v2.0/.well-known/openid_configuration" />
    <audiences>
      <audience>{client-id}</audience>
    </audiences>
  </validate-jwt>
  ```
- **Basic Auth Fallback**: Custom policy to invoke BasicAuthenticator Function
- **Rate Limiting**: Match current AWS throttling (if any)

#### **Request/Response Transformation**
- **Proxy Integration**: Maintain AWS Lambda proxy request/response format
- **Headers**: Preserve all security and correlation headers
- **Error Handling**: Map Azure Function errors to appropriate HTTP status codes

### 4. Terraform Infrastructure Modules
Design Terraform modules based on current CloudFormation structure:

#### **Resource Group Module** (`modules/resource-group/`)
- **Primary RG**: `authserver-rg` (West Europe)
- **Secondary RG**: `authserver-secondary-rg` (North Europe)
- **Naming Convention**: `{service-name}-{environment}-{region}-rg`
- **Tags**: ServiceName, Environment, CostCenter

#### **Function Apps Module** (`modules/function-apps/`)
- **Service Plan**: Linux Consumption Plan (Y1 SKU)
- **Runtime Stack**: Java 11
- **Functions**:
  - `authserver-server` (512MB, 30s timeout)
  - `authserver-jwt-authorizer` (512MB, 30s timeout)  
  - `authserver-basic-authenticator` (512MB, 30s timeout)
- **Application Settings**: Environment variables from discovery
- **Managed Identity**: System-assigned for Key Vault access

#### **APIM Module** (`modules/apim/`)
- **SKU**: Developer_1 (for dev/test) or Standard_1 (for prod)
- **Publisher**: LSEG
- **API**: OAuth API with proxy integration
- **Policies**: JWT validation, Basic Auth fallback
- **Custom Domain**: Optional for production

#### **Azure AD B2C Module** (`modules/ad-b2c/`)
- **Tenant**: New B2C tenant or existing tenant configuration
- **Custom Policies**: Client credentials flow
- **Application Registration**: Confidential client
- **User Flows**: Sign-in flow for admin users
- **Identity Providers**: Local account provider

#### **Key Vault Module** (`modules/key-vault/`)
- **SKU**: Standard
- **Access Policies**: Function Apps managed identities
- **Secrets**:
  - B2C client secrets
  - JWT signing keys
  - Database connection strings (if any)
- **Network Access**: Restrict to Azure services

#### **Storage Module** (`modules/storage/`)
- **Account Type**: Standard_LRS (primary), Standard_GRS (secondary)
- **Containers**: 
  - `function-artifacts` (for deployment packages)
  - `logs-backup` (for log archival)
- **Lifecycle Policies**: Auto-delete old artifacts

#### **Monitoring Module** (`modules/monitoring/`)
- **Log Analytics Workspace**: Centralized logging
- **Application Insights**: Performance monitoring
- **Action Groups**: Alert notifications
- **Dashboards**: Mirror current CloudWatch dashboards
- **Alerts**: 
  - High error rate (>2%)
  - High response time (>200ms p95)
  - Function failures

### 5. Security Architecture
Design security implementation based on current AWS security model:

#### **Managed Identity & RBAC**
- **System-Assigned Managed Identity**: Each Function App
- **Custom RBAC Roles**: 
  - `AuthServer-Function-Role` (replace `authorization-service-server-lambda-role`)
  - `JwtAuthorizer-Function-Role` (replace `authorization-service-jwt-authorizer-lambda-role`)
  - `BasicAuth-Function-Role` (replace `authorization-service-basic-authenticator-lambda-role`)
- **Permissions**:
  - Key Vault: `secrets/get`, `keys/verify`
  - Storage: `blobs/read` (for artifacts)
  - B2C: `application/read` (for JWKS)

#### **IAM Policy Translation**
- **Current AWS Policy**: `ApiGatewayFullAccess` role
- **Azure Equivalent**: Custom role with API Management permissions
- **Policy Mapping**:
  ```json
  AWS: "execute-api:Invoke" → Azure: "Microsoft.ApiManagement/service/gateways/action"
  AWS: "arn:aws:execute-api:*" → Azure: "/subscriptions/{sub}/resourceGroups/{rg}/providers/Microsoft.ApiManagement/*"
  ```

#### **Key Vault Security**
- **Access Policies**: Managed Identity-based (no service principals)
- **Network Access**: Restrict to Azure services only
- **Secrets**:
  - `b2c-client-secret`: B2C application secret
  - `jwt-signing-key`: RS256 private key (if custom signing)
- **Audit Logging**: Enable Key Vault logging to Log Analytics

#### **Network Security**
- **TLS**: Minimum TLS 1.2 for all endpoints
- **HTTPS Only**: Enforce HTTPS for Function Apps and APIM
- **CORS**: Configure for web clients if needed
- **IP Restrictions**: Optional for production environments

#### **Certificate Management**
- **APIM Custom Domain**: Use Azure-managed certificates
- **Function Apps**: Azure-provided certificates (*.azurewebsites.net)
- **B2C**: Azure-managed certificates for JWKS endpoint

### 6. Multi-Region Setup
Design active-passive configuration:
- Primary region: West Europe
- Secondary region: North Europe
- Data replication strategy
- Failover mechanisms
- DNS and traffic routing

## Output Format
Provide your design in the following structure:

```markdown
## Azure Functions Architecture
### AuthServer Function
- [Configuration details, triggers, bindings]

### JwtAuthorizer Function  
- [Configuration details, triggers, bindings]

### BasicAuthenticator Function
- [Configuration details, triggers, bindings]

## Azure AD B2C Design
- [Custom policy structure and configuration]
- [JWT token specifications]
- [OIDC metadata requirements]

## API Management Configuration
- [API definitions and policies]
- [Routing and transformation rules]

## Terraform Module Structure
```
modules/
├── resource-group/
├── function-apps/
├── apim/
├── ad-b2c/
├── key-vault/
├── storage/
├── monitoring/
└── networking/
```

## Security Implementation
- [RBAC role definitions]
- [Key Vault policies]
- [Network security design]

## Multi-Region Architecture
- [Primary/secondary region setup]
- [Failover strategy]
- [Data replication approach]

## Migration Considerations

### **Backward Compatibility Requirements**
- **API Endpoints**: Maintain exact same paths (`/oauth/token`, `/change-password`)
- **Request/Response Format**: Preserve JSON structure and HTTP status codes
- **JWT Claims**: Keep same claim names and values for client compatibility
- **Error Messages**: Maintain consistent error response format
- **Authentication Flow**: Preserve OAuth 2.0 client credentials behavior

### **Critical Dependencies to Address**
- **AWS SDK Replacements**:
  - `com.amazonaws.services.lambda.runtime.*` → Azure Functions Java SDK
  - `com.amazonaws.services.cognitoidp.*` → Azure AD B2C/MSAL4J
  - `com.amazonaws.services.identitymanagement.*` → Azure Resource Manager
  - `com.amazonaws.auth.policy.*` → Custom Azure RBAC policy objects
- **Utility Libraries**: Replace `com.amazonaws.util.*` with standard Java libraries
- **JWKS Integration**: Update from Cognito JWKS URL to B2C JWKS URL

### **Testing Approaches**
- **Unit Tests**: Update mocks for Azure SDK instead of AWS SDK
- **Integration Tests**: Test against Azure AD B2C tenant
- **Contract Tests**: Verify API response compatibility
- **Performance Tests**: Validate <200ms p95 and <500ms cold start requirements
- **Security Tests**: Verify JWT validation and RBAC functionality

### **Rollback Strategies**
- **Blue-Green Deployment**: Keep AWS environment running during migration
- **Traffic Splitting**: Gradual traffic migration with monitoring
- **Database Compatibility**: Ensure no breaking changes to user data
- **Configuration Rollback**: Ability to revert DNS and routing changes
```

## High-Risk Migration Areas (from AWS Analysis)

### **1. Cognito User Pool → Azure AD B2C Migration**
- **Challenge**: Different authentication patterns and API structures
- **Solution**: 
  - Create custom B2C policies that mimic Cognito `AdminInitiateAuth` behavior
  - Implement password challenge handling for `NEW_PASSWORD_REQUIRED` scenarios
  - Ensure JWT claim structure compatibility

### **2. IAM Policy → Azure RBAC Translation**
- **Challenge**: Different policy formats and permission models
- **Solution**:
  - Create custom Azure roles that map to current IAM policies
  - Implement policy translation logic in JWT Authorizer function
  - Maintain resource-level permissions granularity

### **3. Lambda Handler → Azure Functions Conversion**
- **Challenge**: Different request/response objects and execution context
- **Solution**:
  - Create adapter classes to maintain current handler signatures
  - Map AWS Lambda Context to Azure Functions ExecutionContext
  - Preserve proxy integration request/response format

### **4. JWKS Integration Changes**
- **Challenge**: Different JWKS endpoint URLs and formats
- **Solution**:
  - Update JWKS URL from Cognito to B2C format
  - Ensure RS256 algorithm compatibility
  - Maintain JWT validation logic with new key source

## Success Criteria
- Complete Azure architecture design addressing all high-risk areas
- Terraform module structure defined with specific AWS→Azure mappings
- Security and compliance requirements addressed (PCI-DSS, SOX)
- Performance and reliability targets achievable (≤200ms p95, 99.9% SLA)
- Clear migration path for all identified AWS dependencies
- Multi-region setup designed (West Europe primary, North Europe secondary)
- Backward compatibility maintained for all client applications

## Key Considerations
- Maintain identical API contracts for client compatibility
- Ensure JWT token format compatibility during transition
- Design for minimal downtime during cutover
- Plan for gradual traffic migration capabilities
- Consider cost optimization opportunities 