# Infrastructure & Terraform Phase - CloudFormation to Terraform Migration

## Context
You are converting AWS CloudFormation infrastructure to Terraform with Azure resources. This is Phase 4 focusing on Infrastructure as Code migration and Azure Pipelines setup.

## Current AWS Infrastructure (CloudFormation)
Based on the existing `infrastructure/stack.json` and `infrastructure/policy.json`:
- Lambda functions with IAM roles and policies
- API Gateway REST API with resources and methods
- Cognito User Pool and clients
- CloudWatch Log Groups
- S3 bucket for artifacts

## Target Azure Infrastructure (Terraform)
Convert to Azure resources using Terraform azurerm provider:
- Azure Functions with Managed Identity
- Azure API Management (APIM)
- Azure AD B2C tenant and applications
- Log Analytics workspace and Application Insights
- Azure Storage Account for artifacts
- Azure Key Vault for secrets

## Your Tasks

### 1. Terraform Module Structure
Create a modular Terraform structure:

```
infra/terraform/
├── main.tf                 # Root module
├── variables.tf            # Input variables
├── outputs.tf              # Output values
├── terraform.tf            # Provider and backend config
├── modules/
│   ├── resource-group/     # Resource group module
│   ├── function-apps/      # Azure Functions module
│   ├── apim/              # API Management module
│   ├── ad-b2c/            # Azure AD B2C module
│   ├── key-vault/         # Key Vault module
│   ├── storage/           # Storage Account module
│   ├── monitoring/        # Log Analytics & App Insights
│   └── networking/        # VNet and security groups
└── environments/
    ├── dev/               # Development environment
    ├── staging/           # Staging environment
    └── prod/              # Production environment
```

### 2. Azure Functions Module
Convert AWS Lambda configurations to Azure Functions:
- Function App with Java 11 runtime
- Consumption or Premium plan based on performance requirements
- Application settings (environment variables)
- Managed Identity configuration
- Integration with Key Vault for secrets
- Deployment slots for blue-green deployments

### 3. API Management Module
Convert AWS API Gateway to Azure APIM:
- APIM instance configuration
- API definitions matching current REST paths
- Backend service configurations pointing to Functions
- Policy definitions for authentication and rate limiting
- Custom domain and SSL certificate management

### 4. Azure AD B2C Module
Configure B2C tenant and applications:
- B2C tenant creation (if not existing)
- Application registrations for OAuth clients
- Custom policy definitions for client credentials flow
- Key sets for JWT signing (RS256)
- OIDC metadata endpoint configuration

### 5. Key Vault Module
Secure secrets management:
- Key Vault instance with appropriate access policies
- Secrets for B2C client secrets and signing keys
- Managed Identity access policies for Functions
- Certificate storage for TLS/SSL
- Secret rotation policies

### 6. Monitoring Module
Observability and logging:
- Log Analytics workspace
- Application Insights instance
- Custom dashboards and alerts
- Diagnostic settings for all resources
- Action groups for alerting

### 7. Multi-Region Setup
Active-passive configuration:
- Primary region: West Europe
- Secondary region: North Europe
- Resource replication strategy
- Traffic Manager or Front Door configuration
- Backup and disaster recovery

### 8. Azure Pipelines Configuration
CI/CD pipeline setup:
- Build pipeline for Java 11 Functions
- Terraform plan and apply stages
- Environment-specific deployments
- Approval gates for production
- Rollback capabilities

## CloudFormation to Terraform Conversion Examples

### AWS Lambda → Azure Function
```hcl
# AWS CloudFormation (JSON) - BEFORE
{
  "Type": "AWS::Lambda::Function",
  "Properties": {
    "FunctionName": "authserver-oauth-token",
    "Runtime": "java11",
    "Handler": "com.example.ServerLambda::handleRequest",
    "MemorySize": 512,
    "Timeout": 30
  }
}

# Terraform (HCL) - AFTER
resource "azurerm_linux_function_app" "authserver_oauth_token" {
  name                = "authserver-oauth-token"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  service_plan_id     = azurerm_service_plan.main.id
  storage_account_name = azurerm_storage_account.main.name

  site_config {
    application_stack {
      java_version = "11"
    }
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME" = "java"
    "B2C_TENANT_ID"           = var.b2c_tenant_id
    "B2C_CLIENT_ID"           = var.b2c_client_id
  }

  identity {
    type = "SystemAssigned"
  }
}
```

### AWS API Gateway → Azure APIM
```hcl
# Terraform Azure APIM
resource "azurerm_api_management" "main" {
  name                = "authserver-apim"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  publisher_name      = "LSEG"
  publisher_email     = "admin@lseg.com"
  sku_name           = "Developer_1"
}

resource "azurerm_api_management_api" "oauth" {
  name                = "oauth-api"
  resource_group_name = azurerm_resource_group.main.name
  api_management_name = azurerm_api_management.main.name
  revision            = "1"
  display_name        = "OAuth API"
  path                = "oauth"
  protocols           = ["https"]

  import {
    content_format = "openapi+json"
    content_value  = file("${path.module}/oauth-api.json")
  }
}
```

## Output Format
Provide your Terraform code in the following structure:

```markdown
## Module: [module-name]
### Purpose
- [Description of what this module creates]

### Resources Created
- [List of Azure resources]

### Variables
- [Input variables with descriptions]

### Outputs
- [Output values for other modules]

### Terraform Code
```hcl
[Complete Terraform code for the module]
```

### Dependencies
- [Other modules this depends on]

### Usage Example
```hcl
[Example of how to use this module]
```
```

## Success Criteria
- Complete CloudFormation to Terraform conversion
- All Azure resources properly configured
- Modular and reusable Terraform code
- Environment-specific configurations
- Multi-region setup implemented
- Azure Pipelines configured
- Security best practices followed
- Cost optimization implemented

## Key Requirements
- **Security**: Managed Identity, Key Vault integration, RBAC
- **Performance**: Appropriate Function App plans, APIM caching
- **Reliability**: Multi-region setup, health checks, monitoring
- **Compliance**: Data residency, backup strategies
- **Cost**: Consumption plans where appropriate, resource tagging

## Terraform Best Practices
- Use remote state backend (Azure Storage)
- Implement state locking
- Use consistent naming conventions
- Tag all resources appropriately
- Use data sources for existing resources
- Implement proper variable validation
- Use locals for computed values
- Follow DRY principles with modules

## Azure Pipeline Integration
Create pipeline YAML for:
- Terraform validation and planning
- Security scanning (Checkov, TFSec)
- Cost estimation
- Deployment approval gates
- Rollback procedures
- Environment promotion 