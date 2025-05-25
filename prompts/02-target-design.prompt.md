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
Design three Azure Functions to replace AWS Lambdas:
- **AuthServer Function**: Replace ServerLambda/ProxyRequestHandler
- **JwtAuthorizer Function**: Replace JwtAuthorizerLambda  
- **BasicAuthenticator Function**: Replace BasicAuthenticatorLambda

For each function, specify:
- Runtime configuration (Java 11, memory allocation)
- Trigger types and bindings
- Environment variable mapping
- Managed Identity assignments
- Integration with Azure AD B2C

### 2. Azure AD B2C Configuration
Design the B2C tenant setup:
- Custom policy structure for OAuth 2.0 client credentials flow
- JWT token configuration (RS256, claims structure)
- OIDC metadata endpoint publishing
- Integration with existing client applications
- User flow design (if applicable)

### 3. API Management (APIM) Design
Design APIM configuration:
- API definitions matching current AWS API Gateway paths
- Policy configurations for Basic Auth fallback
- Rate limiting and throttling policies
- Request/response transformation policies
- Backend service routing to Azure Functions

### 4. Terraform Infrastructure Modules
Design Terraform module structure:
- Resource group organization
- Function App configurations
- APIM instance setup
- Azure AD B2C tenant configuration
- Key Vault for secrets management
- Storage Account for artifacts
- Log Analytics workspace
- Application Insights configuration

### 5. Security Architecture
Design security implementation:
- Managed Identity assignments and RBAC roles
- Key Vault access policies
- Network security (VNet integration if needed)
- TLS/SSL certificate management
- Secret rotation strategies

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
- [Backward compatibility requirements]
- [Rollback strategies]
- [Testing approaches]
```

## Success Criteria
- Complete Azure architecture design
- Terraform module structure defined
- Security and compliance requirements addressed
- Performance and reliability targets achievable
- Clear migration path from AWS components
- Multi-region setup designed

## Key Considerations
- Maintain identical API contracts for client compatibility
- Ensure JWT token format compatibility during transition
- Design for minimal downtime during cutover
- Plan for gradual traffic migration capabilities
- Consider cost optimization opportunities 