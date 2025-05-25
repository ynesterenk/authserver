# Azure Target Architecture Design - Authserver Migration

## Azure Functions Architecture

### AuthServer Function (Replace ServerLambda/ProxyRequestHandler)

#### Configuration Details
```yaml
Function Name: authserver-server
Runtime: Java 11
Memory: 512MB
Timeout: 30 seconds
Plan: Consumption Plan (Y1)
OS: Linux
```

#### Triggers and Bindings
```java
@FunctionName("oauth-token")
public HttpResponseMessage oauthToken(
    @HttpTrigger(name = "req", 
                 methods = {HttpMethod.POST}, 
                 route = "oauth/token",
                 authLevel = AuthorizationLevel.ANONYMOUS) 
    HttpRequestMessage<Optional<String>> request,
    final ExecutionContext context) {
    // Implementation
}

@FunctionName("change-password")
public HttpResponseMessage changePassword(
    @HttpTrigger(name = "req", 
                 methods = {HttpMethod.POST}, 
                 route = "change-password",
                 authLevel = AuthorizationLevel.ANONYMOUS) 
    HttpRequestMessage<Optional<String>> request,
    final ExecutionContext context) {
    // Implementation
}
```

#### Environment Variables
```json
{
  "B2C_TENANT_ID": "authserver-b2c.onmicrosoft.com",
  "B2C_CLIENT_ID": "{b2c-application-client-id}",
  "AZURE_REGION": "westeurope",
  "KEY_VAULT_URL": "https://authserver-kv.vault.azure.net/",
  "FUNCTIONS_WORKER_RUNTIME": "java"
}
```

#### Managed Identity
- **Type**: System-assigned
- **Permissions**: Key Vault secrets reader, Storage blob reader

### JwtAuthorizer Function (Replace JwtAuthorizerLambda)

#### Configuration Details
```yaml
Function Name: authserver-jwt-authorizer
Runtime: Java 11
Memory: 512MB
Timeout: 30 seconds
Plan: Consumption Plan (Y1)
Purpose: JWT validation and Azure RBAC policy generation
```

#### Triggers and Bindings
```java
@FunctionName("jwt-authorize")
public AuthorizerResponse jwtAuthorize(
    @HttpTrigger(name = "req", 
                 methods = {HttpMethod.POST}, 
                 route = "authorize/jwt",
                 authLevel = AuthorizationLevel.FUNCTION) 
    HttpRequestMessage<AuthorizerRequest> request,
    final ExecutionContext context) {
    // JWT validation and policy generation
}
```

#### JWKS Integration
```java
// Azure AD B2C JWKS endpoint
String jwksUrl = String.format(
    "https://%s.b2clogin.com/%s/%s/discovery/v2.0/keys",
    tenantName, tenantId, policyName
);
```

#### Environment Variables
```json
{
  "B2C_TENANT_ID": "authserver-b2c.onmicrosoft.com",
  "B2C_POLICY_NAME": "B2C_1_client_credentials",
  "AZURE_REGION": "westeurope",
  "KEY_VAULT_URL": "https://authserver-kv.vault.azure.net/"
}
```

### BasicAuthenticator Function (Replace BasicAuthenticatorLambda)

#### Configuration Details
```yaml
Function Name: authserver-basic-authenticator
Runtime: Java 11
Memory: 512MB
Timeout: 30 seconds
Plan: Consumption Plan (Y1)
Purpose: HTTP Basic Auth fallback for legacy clients
```

#### Triggers and Bindings
```java
@FunctionName("basic-authorize")
public AuthorizerResponse basicAuthorize(
    @HttpTrigger(name = "req", 
                 methods = {HttpMethod.POST}, 
                 route = "authorize/basic",
                 authLevel = AuthorizationLevel.FUNCTION) 
    HttpRequestMessage<AuthorizerRequest> request,
    final ExecutionContext context) {
    // Basic auth validation and policy generation
}
```

#### Environment Variables
```json
{
  "B2C_TENANT_ID": "authserver-b2c.onmicrosoft.com",
  "B2C_CLIENT_ID": "{b2c-application-client-id}",
  "AZURE_REGION": "westeurope",
  "KEY_VAULT_URL": "https://authserver-kv.vault.azure.net/"
}
```

## Azure AD B2C Design

### OAuth 2.0 Client Credentials Flow

#### Custom Policy Configuration
```xml
<!-- B2C_1_client_credentials.xml -->
<TrustFrameworkPolicy xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
                      xmlns:xsd="http://www.w3.org/2001/XMLSchema" 
                      xmlns="http://schemas.microsoft.com/online/cpim/schemas/2013/06" 
                      PolicySchemaVersion="0.3.0.0" 
                      TenantId="authserver-b2c.onmicrosoft.com" 
                      PolicyId="B2C_1_client_credentials">
  
  <BasePolicy>
    <TenantId>authserver-b2c.onmicrosoft.com</TenantId>
    <PolicyId>B2C_1A_TrustFrameworkBase</PolicyId>
  </BasePolicy>
  
  <ClaimsProviders>
    <ClaimsProvider>
      <DisplayName>Client Credentials Provider</DisplayName>
      <TechnicalProfiles>
        <TechnicalProfile Id="ClientCredentials-Common">
          <DisplayName>Client Credentials</DisplayName>
          <Protocol Name="OAuth2" />
          <Metadata>
            <Item Key="grant_type">client_credentials</Item>
            <Item Key="scope">https://authserver-b2c.onmicrosoft.com/api/access</Item>
          </Metadata>
        </TechnicalProfile>
      </TechnicalProfiles>
    </ClaimsProvider>
  </ClaimsProviders>
</TrustFrameworkPolicy>
```

#### JWT Token Configuration
```json
{
  "token_configuration": {
    "algorithm": "RS256",
    "issuer": "https://authserver-b2c.b2clogin.com/authserver-b2c.onmicrosoft.com/v2.0/",
    "audience": "{b2c-client-id}",
    "claims": {
      "username": "preferred_username",
      "groups": "groups",
      "aud": "aud",
      "iss": "iss",
      "exp": "exp",
      "iat": "iat",
      "sub": "sub"
    },
    "token_lifetime": 3600
  }
}
```

#### User Pool Migration
```json
{
  "users": [
    {
      "username": "admin",
      "email": "boyarsky.vitaliy@live.com",
      "groups": ["ApiGatewayFullAccess"],
      "attributes": {
        "email_verified": true,
        "given_name": "Admin",
        "family_name": "User"
      }
    }
  ],
  "groups": [
    {
      "name": "ApiGatewayFullAccess",
      "description": "Full access to API Gateway resources",
      "permissions": ["api.read", "api.write", "api.execute"]
    }
  ]
}
```

#### Application Registration
```json
{
  "application": {
    "name": "authserver-api",
    "type": "confidential",
    "grant_types": ["client_credentials"],
    "scopes": ["https://authserver-b2c.onmicrosoft.com/api/access"],
    "redirect_uris": [],
    "secret_storage": "azure_key_vault",
    "secret_name": "b2c-client-secret"
  }
}
```

### JWKS Endpoint
```
https://authserver-b2c.b2clogin.com/authserver-b2c.onmicrosoft.com/B2C_1_client_credentials/discovery/v2.0/keys
```

## API Management Configuration

### API Definitions
```yaml
API Configuration:
  name: authserver-oauth-api
  path: /
  protocols: [https]
  subscription_required: false
  
Operations:
  - name: oauth-token
    method: POST
    url_template: /oauth/token
    backend: authserver-server-function
    
  - name: change-password
    method: POST
    url_template: /change-password
    backend: authserver-server-function
    
  - name: health-check
    method: GET
    url_template: /health
    backend: authserver-server-function
```

### Backend Service Configuration
```xml
<!-- Backend configuration for Azure Functions -->
<backends>
  <backend id="authserver-functions">
    <url>https://authserver-functions.azurewebsites.net/api</url>
    <protocol>http</protocol>
    <credentials>
      <header name="x-functions-key" value="{{function-key}}" />
    </credentials>
  </backend>
</backends>
```

### Authorization Policies

#### JWT Validation Policy
```xml
<policies>
  <inbound>
    <base />
    <validate-jwt header-name="Authorization" failed-validation-httpcode="401" failed-validation-error-message="Unauthorized">
      <openid-config url="https://authserver-b2c.b2clogin.com/authserver-b2c.onmicrosoft.com/B2C_1_client_credentials/v2.0/.well-known/openid_configuration" />
      <audiences>
        <audience>{{b2c-client-id}}</audience>
      </audiences>
      <required-claims>
        <claim name="groups" match="any">
          <value>ApiGatewayFullAccess</value>
        </claim>
      </required-claims>
    </validate-jwt>
  </inbound>
  <backend>
    <base />
  </backend>
  <outbound>
    <base />
  </outbound>
  <on-error>
    <base />
  </on-error>
</policies>
```

#### Basic Auth Fallback Policy
```xml
<policies>
  <inbound>
    <base />
    <choose>
      <when condition="@(context.Request.Headers.GetValueOrDefault("Authorization","").StartsWith("Basic"))">
        <send-request mode="new" response-variable-name="authResponse" timeout="30" ignore-error="false">
          <set-url>https://authserver-functions.azurewebsites.net/api/authorize/basic</set-url>
          <set-method>POST</set-method>
          <set-header name="Content-Type" exists-action="override">
            <value>application/json</value>
          </set-header>
          <set-body>@{
            return new JObject(
              new JProperty("authorizationToken", context.Request.Headers.GetValueOrDefault("Authorization","")),
              new JProperty("methodArn", context.Request.Url.ToString())
            ).ToString();
          }</set-body>
        </send-request>
        <choose>
          <when condition="@(((IResponse)context.Variables["authResponse"]).StatusCode != 200)">
            <return-response>
              <set-status code="401" reason="Unauthorized" />
              <set-body>{"error": "invalid_credentials"}</set-body>
            </return-response>
          </when>
        </choose>
      </when>
    </choose>
  </inbound>
</policies>
```

### Rate Limiting Policy
```xml
<policies>
  <inbound>
    <rate-limit calls="1000" renewal-period="60" />
    <quota calls="10000" renewal-period="3600" />
  </inbound>
</policies>
```

## Terraform Module Structure

### Root Module Structure
```
infra/terraform/
├── main.tf                 # Root module configuration
├── variables.tf            # Input variables
├── outputs.tf              # Output values
├── terraform.tf            # Provider and backend config
├── environments/
│   ├── dev/
│   │   ├── main.tf
│   │   ├── terraform.tfvars
│   │   └── backend.tf
│   ├── staging/
│   └── prod/
└── modules/
    ├── resource-group/
    ├── function-apps/
    ├── apim/
    ├── ad-b2c/
    ├── key-vault/
    ├── storage/
    ├── monitoring/
    └── networking/
```

### Resource Group Module
```hcl
# modules/resource-group/main.tf
resource "azurerm_resource_group" "primary" {
  name     = "${var.service_name}-${var.environment}-${var.primary_region}-rg"
  location = var.primary_region

  tags = {
    ServiceName   = var.service_name
    Environment   = var.environment
    CostCenter    = var.cost_center
    ManagedBy     = "terraform"
  }
}

resource "azurerm_resource_group" "secondary" {
  name     = "${var.service_name}-${var.environment}-${var.secondary_region}-rg"
  location = var.secondary_region

  tags = {
    ServiceName   = var.service_name
    Environment   = var.environment
    CostCenter    = var.cost_center
    ManagedBy     = "terraform"
    Region        = "secondary"
  }
}
```

### Function Apps Module
```hcl
# modules/function-apps/main.tf
resource "azurerm_service_plan" "main" {
  name                = "${var.service_name}-plan"
  resource_group_name = var.resource_group_name
  location            = var.location
  os_type             = "Linux"
  sku_name           = "Y1"  # Consumption plan

  tags = var.tags
}

resource "azurerm_linux_function_app" "authserver" {
  name                = "${var.service_name}-server"
  resource_group_name = var.resource_group_name
  location            = var.location
  service_plan_id     = azurerm_service_plan.main.id
  storage_account_name = var.storage_account_name

  site_config {
    application_stack {
      java_version = "11"
    }
    
    application_insights_key = var.application_insights_key
    
    cors {
      allowed_origins = ["*"]
    }
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME"     = "java"
    "B2C_TENANT_ID"               = var.b2c_tenant_id
    "B2C_CLIENT_ID"               = var.b2c_client_id
    "AZURE_REGION"                = var.location
    "KEY_VAULT_URL"               = var.key_vault_url
    "WEBSITE_RUN_FROM_PACKAGE"    = "1"
  }

  identity {
    type = "SystemAssigned"
  }

  tags = var.tags
}

resource "azurerm_linux_function_app" "jwt_authorizer" {
  name                = "${var.service_name}-jwt-authorizer"
  resource_group_name = var.resource_group_name
  location            = var.location
  service_plan_id     = azurerm_service_plan.main.id
  storage_account_name = var.storage_account_name

  site_config {
    application_stack {
      java_version = "11"
    }
    
    application_insights_key = var.application_insights_key
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME"     = "java"
    "B2C_TENANT_ID"               = var.b2c_tenant_id
    "B2C_POLICY_NAME"             = var.b2c_policy_name
    "AZURE_REGION"                = var.location
    "KEY_VAULT_URL"               = var.key_vault_url
    "WEBSITE_RUN_FROM_PACKAGE"    = "1"
  }

  identity {
    type = "SystemAssigned"
  }

  tags = var.tags
}

resource "azurerm_linux_function_app" "basic_authenticator" {
  name                = "${var.service_name}-basic-authenticator"
  resource_group_name = var.resource_group_name
  location            = var.location
  service_plan_id     = azurerm_service_plan.main.id
  storage_account_name = var.storage_account_name

  site_config {
    application_stack {
      java_version = "11"
    }
    
    application_insights_key = var.application_insights_key
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME"     = "java"
    "B2C_TENANT_ID"               = var.b2c_tenant_id
    "B2C_CLIENT_ID"               = var.b2c_client_id
    "AZURE_REGION"                = var.location
    "KEY_VAULT_URL"               = var.key_vault_url
    "WEBSITE_RUN_FROM_PACKAGE"    = "1"
  }

  identity {
    type = "SystemAssigned"
  }

  tags = var.tags
}
```

### APIM Module
```hcl
# modules/apim/main.tf
resource "azurerm_api_management" "main" {
  name                = "${var.service_name}-apim"
  location            = var.location
  resource_group_name = var.resource_group_name
  publisher_name      = "LSEG"
  publisher_email     = "admin@lseg.com"
  sku_name           = var.apim_sku

  identity {
    type = "SystemAssigned"
  }

  tags = var.tags
}

resource "azurerm_api_management_api" "oauth" {
  name                = "oauth-api"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  revision            = "1"
  display_name        = "OAuth API"
  path                = ""
  protocols           = ["https"]
  service_url         = var.backend_url

  import {
    content_format = "openapi+json"
    content_value = jsonencode({
      openapi = "3.0.0"
      info = {
        title   = "Authserver OAuth API"
        version = "1.0.0"
      }
      paths = {
        "/oauth/token" = {
          post = {
            operationId = "oauth-token"
            responses = {
              "200" = {
                description = "Success"
              }
            }
          }
        }
        "/change-password" = {
          post = {
            operationId = "change-password"
            responses = {
              "200" = {
                description = "Success"
              }
            }
          }
        }
      }
    })
  }
}

resource "azurerm_api_management_backend" "functions" {
  name                = "authserver-functions"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  protocol            = "http"
  url                 = var.backend_url

  credentials {
    header = {
      "x-functions-key" = var.function_key
    }
  }
}
```

### Key Vault Module
```hcl
# modules/key-vault/main.tf
data "azurerm_client_config" "current" {}

resource "azurerm_key_vault" "main" {
  name                = "${var.service_name}-kv"
  location            = var.location
  resource_group_name = var.resource_group_name
  tenant_id           = data.azurerm_client_config.current.tenant_id
  sku_name           = "standard"

  network_acls {
    default_action = "Deny"
    bypass         = "AzureServices"
  }

  tags = var.tags
}

resource "azurerm_key_vault_access_policy" "functions" {
  for_each = var.function_principal_ids

  key_vault_id = azurerm_key_vault.main.id
  tenant_id    = data.azurerm_client_config.current.tenant_id
  object_id    = each.value

  secret_permissions = [
    "Get",
    "List"
  ]

  key_permissions = [
    "Get",
    "List",
    "Verify"
  ]
}

resource "azurerm_key_vault_secret" "b2c_client_secret" {
  name         = "b2c-client-secret"
  value        = var.b2c_client_secret
  key_vault_id = azurerm_key_vault.main.id

  depends_on = [azurerm_key_vault_access_policy.functions]
}
```

### Monitoring Module
```hcl
# modules/monitoring/main.tf
resource "azurerm_log_analytics_workspace" "main" {
  name                = "${var.service_name}-logs"
  location            = var.location
  resource_group_name = var.resource_group_name
  sku                 = "PerGB2018"
  retention_in_days   = 30

  tags = var.tags
}

resource "azurerm_application_insights" "main" {
  name                = "${var.service_name}-insights"
  location            = var.location
  resource_group_name = var.resource_group_name
  workspace_id        = azurerm_log_analytics_workspace.main.id
  application_type    = "java"

  tags = var.tags
}

resource "azurerm_monitor_action_group" "main" {
  name                = "${var.service_name}-alerts"
  resource_group_name = var.resource_group_name
  short_name          = "authserver"

  email_receiver {
    name          = "admin"
    email_address = var.alert_email
  }

  tags = var.tags
}

resource "azurerm_monitor_metric_alert" "high_error_rate" {
  name                = "${var.service_name}-high-error-rate"
  resource_group_name = var.resource_group_name
  scopes              = var.function_app_ids
  description         = "Alert when error rate exceeds 2%"
  severity            = 2
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.Web/sites"
    metric_name      = "Http5xx"
    aggregation      = "Total"
    operator         = "GreaterThan"
    threshold        = 2
  }

  action {
    action_group_id = azurerm_monitor_action_group.main.id
  }

  tags = var.tags
}

resource "azurerm_monitor_metric_alert" "high_response_time" {
  name                = "${var.service_name}-high-response-time"
  resource_group_name = var.resource_group_name
  scopes              = var.function_app_ids
  description         = "Alert when p95 response time exceeds 200ms"
  severity            = 2
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.Web/sites"
    metric_name      = "HttpResponseTime"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 200
  }

  action {
    action_group_id = azurerm_monitor_action_group.main.id
  }

  tags = var.tags
}
```

## Security Implementation

### Managed Identity & RBAC

#### Custom RBAC Roles
```json
{
  "AuthServer-Function-Role": {
    "Name": "AuthServer Function Role",
    "Description": "Custom role for AuthServer Function App",
    "Actions": [
      "Microsoft.KeyVault/vaults/secrets/read",
      "Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read",
      "Microsoft.Web/sites/functions/read"
    ],
    "NotActions": [],
    "AssignableScopes": [
      "/subscriptions/{subscription-id}/resourceGroups/authserver-rg"
    ]
  },
  "JwtAuthorizer-Function-Role": {
    "Name": "JWT Authorizer Function Role", 
    "Description": "Custom role for JWT Authorizer Function App",
    "Actions": [
      "Microsoft.KeyVault/vaults/secrets/read",
      "Microsoft.KeyVault/vaults/keys/read",
      "Microsoft.KeyVault/vaults/keys/verify/action",
      "Microsoft.Web/sites/functions/read"
    ],
    "NotActions": [],
    "AssignableScopes": [
      "/subscriptions/{subscription-id}/resourceGroups/authserver-rg"
    ]
  },
  "BasicAuth-Function-Role": {
    "Name": "Basic Auth Function Role",
    "Description": "Custom role for Basic Auth Function App", 
    "Actions": [
      "Microsoft.KeyVault/vaults/secrets/read",
      "Microsoft.Web/sites/functions/read"
    ],
    "NotActions": [],
    "AssignableScopes": [
      "/subscriptions/{subscription-id}/resourceGroups/authserver-rg"
    ]
  }
}
```

#### IAM Policy Translation
```json
{
  "policy_mappings": {
    "aws_policy": "arn:aws:iam::account:role/authorization-service-server-lambda-role",
    "azure_equivalent": {
      "role_definition": "AuthServer-Function-Role",
      "scope": "/subscriptions/{sub}/resourceGroups/authserver-rg",
      "permissions": {
        "aws_cognito_access": "Microsoft.KeyVault/vaults/secrets/read",
        "aws_s3_access": "Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read",
        "aws_logs_access": "Microsoft.Insights/logs/read"
      }
    }
  }
}
```

### Key Vault Security Configuration
```hcl
resource "azurerm_key_vault" "main" {
  name                = "authserver-kv"
  location            = var.location
  resource_group_name = var.resource_group_name
  tenant_id           = data.azurerm_client_config.current.tenant_id
  sku_name           = "standard"

  # Network security
  network_acls {
    default_action = "Deny"
    bypass         = "AzureServices"
    
    # Allow specific Azure services
    virtual_network_subnet_ids = []
    ip_rules = []
  }

  # Enable logging
  enable_rbac_authorization = false
  purge_protection_enabled  = true
  soft_delete_retention_days = 7

  tags = var.tags
}

# Diagnostic settings for audit logging
resource "azurerm_monitor_diagnostic_setting" "key_vault" {
  name               = "key-vault-diagnostics"
  target_resource_id = azurerm_key_vault.main.id
  log_analytics_workspace_id = var.log_analytics_workspace_id

  enabled_log {
    category = "AuditEvent"
  }

  metric {
    category = "AllMetrics"
    enabled  = true
  }
}
```

## Multi-Region Architecture

### Primary Region (West Europe)
```hcl
# Primary region configuration
module "primary_region" {
  source = "./modules"
  
  location            = "westeurope"
  resource_group_name = "authserver-primary-rg"
  environment         = var.environment
  is_primary_region   = true
  
  # Full capacity configuration
  apim_sku           = "Standard_1"
  function_plan_sku  = "Y1"
  
  # Primary region specific settings
  enable_traffic_manager = true
  traffic_manager_priority = 1
}
```

### Secondary Region (North Europe)
```hcl
# Secondary region configuration  
module "secondary_region" {
  source = "./modules"
  
  location            = "northeurope"
  resource_group_name = "authserver-secondary-rg"
  environment         = var.environment
  is_primary_region   = false
  
  # Reduced capacity for passive region
  apim_sku           = "Developer_1"
  function_plan_sku  = "Y1"
  
  # Secondary region specific settings
  enable_traffic_manager = true
  traffic_manager_priority = 2
  
  # Data replication from primary
  replicate_from_primary = true
  primary_region_rg     = module.primary_region.resource_group_name
}
```

### Traffic Manager Configuration
```hcl
resource "azurerm_traffic_manager_profile" "main" {
  name                = "authserver-tm"
  resource_group_name = var.primary_resource_group_name
  
  traffic_routing_method = "Priority"
  
  dns_config {
    relative_name = "authserver"
    ttl          = 30
  }
  
  monitor_config {
    protocol                     = "HTTPS"
    port                        = 443
    path                        = "/health"
    interval_in_seconds         = 30
    timeout_in_seconds          = 10
    tolerated_number_of_failures = 3
  }

  tags = var.tags
}

# Primary endpoint
resource "azurerm_traffic_manager_azure_endpoint" "primary" {
  name               = "primary"
  profile_id         = azurerm_traffic_manager_profile.main.id
  priority           = 1
  target_resource_id = var.primary_apim_id
  
  custom_header {
    name  = "host"
    value = var.primary_apim_hostname
  }
}

# Secondary endpoint
resource "azurerm_traffic_manager_azure_endpoint" "secondary" {
  name               = "secondary"
  profile_id         = azurerm_traffic_manager_profile.main.id
  priority           = 2
  target_resource_id = var.secondary_apim_id
  
  custom_header {
    name  = "host"
    value = var.secondary_apim_hostname
  }
}
```

### Data Replication Strategy
```hcl
# Key Vault replication
resource "azurerm_key_vault" "secondary" {
  name                = "authserver-kv-secondary"
  location            = "northeurope"
  resource_group_name = var.secondary_resource_group_name
  tenant_id           = data.azurerm_client_config.current.tenant_id
  sku_name           = "standard"
  
  # Replicate secrets from primary
  depends_on = [azurerm_key_vault.primary]

  tags = merge(var.tags, {
    Region = "secondary"
  })
}

# Storage Account with geo-replication
resource "azurerm_storage_account" "secondary" {
  name                     = "authserversecondary"
  resource_group_name      = var.secondary_resource_group_name
  location                 = "northeurope"
  account_tier             = "Standard"
  account_replication_type = "GRS"
  
  # Cross-region replication
  blob_properties {
    change_feed_enabled = true
    versioning_enabled  = true
  }

  tags = merge(var.tags, {
    Region = "secondary"
  })
}
```

## High-Risk Migration Areas Solutions

### 1. Cognito User Pool → Azure AD B2C Migration

#### Challenge Mitigation
```java
// Adapter class to maintain Cognito-like interface
public class B2CUserPoolAdapter implements UserPool {
    private final ConfidentialClientApplication msalApp;
    private final String tenantId;
    private final String clientId;
    
    @Override
    public String authenticate(String username, String password) throws UserPoolException {
        try {
            // Mimic AdminInitiateAuth behavior
            UserNamePasswordParameters parameters = 
                UserNamePasswordParameters.builder(
                    Collections.singleton(clientId + "/.default"),
                    username,
                    password.toCharArray())
                .build();
                
            CompletableFuture<IAuthenticationResult> future = 
                msalApp.acquireToken(parameters);
            IAuthenticationResult result = future.get();
            
            return result.idToken();
        } catch (Exception e) {
            throw new UserPoolException("Authentication failed", e);
        }
    }
    
    @Override
    public void changePassword(String username, String previousPassword, String proposedPassword) 
            throws UserPoolException {
        // Implement password change via B2C Graph API
        // Handle NEW_PASSWORD_REQUIRED challenge equivalent
    }
}
```

### 2. IAM Policy → Azure RBAC Translation

#### Policy Translation Logic
```java
public class PolicyTranslator {
    
    public AzureRBACPolicy translateIAMPolicy(String awsPolicyJson, Claims jwtClaims) {
        // Parse AWS IAM policy
        Policy awsPolicy = Policy.fromJson(awsPolicyJson);
        
        // Create Azure RBAC equivalent
        AzureRBACPolicy azurePolicy = new AzureRBACPolicy();
        
        for (Statement statement : awsPolicy.getStatements()) {
            AzureRoleAssignment roleAssignment = new AzureRoleAssignment();
            
            // Map AWS actions to Azure operations
            for (String action : statement.getActions()) {
                String azureOperation = mapAwsActionToAzureOperation(action);
                roleAssignment.addOperation(azureOperation);
            }
            
            // Map AWS resources to Azure resource IDs
            for (Resource resource : statement.getResources()) {
                String azureResourceId = mapAwsResourceToAzureResource(resource.getId());
                roleAssignment.addScope(azureResourceId);
            }
            
            // Apply user/group context from JWT claims
            roleAssignment.setPrincipal(jwtClaims.getUsername());
            roleAssignment.setGroups(jwtClaims.getGroups());
            
            azurePolicy.addRoleAssignment(roleAssignment);
        }
        
        return azurePolicy;
    }
    
    private String mapAwsActionToAzureOperation(String awsAction) {
        Map<String, String> actionMappings = Map.of(
            "execute-api:Invoke", "Microsoft.ApiManagement/service/gateways/action",
            "s3:GetObject", "Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read",
            "logs:CreateLogStream", "Microsoft.Insights/logs/write"
        );
        
        return actionMappings.getOrDefault(awsAction, awsAction);
    }
}
```

### 3. Lambda Handler → Azure Functions Conversion

#### Handler Adapter
```java
// Adapter to maintain AWS Lambda handler interface
public class AzureFunctionAdapter {
    
    @FunctionName("oauth-token")
    public HttpResponseMessage handleOAuthToken(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "oauth/token") 
            HttpRequestMessage<Optional<String>> azureRequest,
            final ExecutionContext azureContext) {
        
        // Convert Azure request to AWS-compatible format
        ProxyRequest awsRequest = convertAzureToAwsRequest(azureRequest);
        Context awsContext = convertAzureToAwsContext(azureContext);
        
        // Use existing AWS handler logic
        ProxyRequestHandler handler = new ProxyRequestHandler();
        ProxyResponse awsResponse = handler.handleRequest(awsRequest, awsContext);
        
        // Convert AWS response back to Azure format
        return convertAwsToAzureResponse(awsResponse, azureRequest);
    }
    
    private ProxyRequest convertAzureToAwsRequest(HttpRequestMessage<Optional<String>> azureRequest) {
        ProxyRequest proxyRequest = new ProxyRequest();
        proxyRequest.setHttpMethod(azureRequest.getHttpMethod().toString());
        proxyRequest.setPath(azureRequest.getUri().getPath());
        proxyRequest.setQueryStringParameters(parseQueryParameters(azureRequest.getUri()));
        proxyRequest.setHeaders(convertHeaders(azureRequest.getHeaders()));
        proxyRequest.setBody(azureRequest.getBody().orElse(null));
        return proxyRequest;
    }
    
    private Context convertAzureToAwsContext(ExecutionContext azureContext) {
        return new Context() {
            @Override
            public String getAwsRequestId() {
                return azureContext.getInvocationId();
            }
            
            @Override
            public String getLogGroupName() {
                return azureContext.getFunctionName();
            }
            
            @Override
            public String getLogStreamName() {
                return azureContext.getFunctionName() + "-" + 
                       Instant.now().toString();
            }
            
            @Override
            public String getFunctionName() {
                return azureContext.getFunctionName();
            }
            
            @Override
            public String getFunctionVersion() {
                return "1.0";
            }
            
            @Override
            public String getInvokedFunctionArn() {
                return "arn:azure:functions:" + azureContext.getFunctionName();
            }
            
            @Override
            public CognitoIdentity getIdentity() {
                return null;
            }
            
            @Override
            public ClientContext getClientContext() {
                return null;
            }
            
            @Override
            public int getRemainingTimeInMillis() {
                return 30000; // 30 seconds default
            }
            
            @Override
            public int getMemoryLimitInMB() {
                return 512;
            }
            
            @Override
            public LambdaLogger getLogger() {
                return new LambdaLogger() {
                    @Override
                    public void log(String message) {
                        azureContext.getLogger().info(message);
                    }
                    
                    @Override
                    public void log(byte[] message) {
                        azureContext.getLogger().info(new String(message));
                    }
                };
            }
        };
    }
}
```

### 4. JWKS Integration Changes

#### JWKS URL Update
```java
public class AzureB2CJwtValidator {
    private final String tenantName;
    private final String tenantId; 
    private final String policyName;
    
    public AzureB2CJwtValidator(String tenantName, String tenantId, String policyName) {
        this.tenantName = tenantName;
        this.tenantId = tenantId;
        this.policyName = policyName;
    }
    
    public JWKSource<SecurityContext> createJWKSource() throws MalformedURLException {
        // Azure AD B2C JWKS endpoint format
        String jwksUrl = String.format(
            "https://%s.b2clogin.com/%s/%s/discovery/v2.0/keys",
            tenantName, tenantId, policyName
        );
        
        return new RemoteJWKSet<>(new URL(jwksUrl));
    }
    
    public Claims validateToken(String token) throws JwtVerificationException {
        try {
            JWKSource<SecurityContext> jwkSource = createJWKSource();
            
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = 
                new DefaultJWTProcessor<>();
            jwtProcessor.setJWKSource(jwkSource);
            jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT));
            
            JWSAlgorithm expectedJWSAlg = JWSAlgorithm.RS256;
            JWSKeySelector<SecurityContext> keySelector = 
                new JWSVerificationKeySelector<>(expectedJWSAlg, jwkSource);
            jwtProcessor.setJWSKeySelector(keySelector);
            
            SecurityContext ctx = null;
            JWTClaimsSet claimsSet = jwtProcessor.process(token, ctx);
            
            return new Claims(claimsSet);
        } catch (Exception e) {
            throw new JwtVerificationException("Token validation failed", e);
        }
    }
}
```

## Migration Considerations

### Backward Compatibility Implementation

#### API Response Format Preservation
```java
@Component
public class ResponseFormatAdapter {
    
    public HttpResponseMessage createCompatibleResponse(
            HttpRequestMessage<?> request, 
            Object responseBody, 
            HttpStatus status) {
        
        // Maintain exact AWS API Gateway response format
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", status.value());
        response.put("headers", createCompatibleHeaders());
        response.put("body", JsonUtils.toJson(responseBody));
        response.put("isBase64Encoded", false);
        
        return request.createResponseBuilder(status)
            .header("Content-Type", "application/json")
            .body(JsonUtils.toJson(response))
            .build();
    }
    
    private Map<String, String> createCompatibleHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
        headers.put("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        return headers;
    }
}
```

#### JWT Claims Compatibility
```java
public class ClaimsCompatibilityAdapter {
    
    public Claims adaptB2CClaimsToAwsFormat(JWTClaimsSet b2cClaims) {
        Claims awsCompatibleClaims = new Claims();
        
        // Map B2C claims to AWS Cognito format
        awsCompatibleClaims.setUsername(b2cClaims.getStringClaim("preferred_username"));
        awsCompatibleClaims.setGroups(b2cClaims.getStringListClaim("groups"));
        awsCompatibleClaims.setAudience(b2cClaims.getAudience());
        awsCompatibleClaims.setIssuer(b2cClaims.getIssuer());
        awsCompatibleClaims.setExpiration(b2cClaims.getExpirationTime());
        awsCompatibleClaims.setIssuedAt(b2cClaims.getIssueTime());
        awsCompatibleClaims.setSubject(b2cClaims.getSubject());
        
        // Preserve any custom claims
        for (String claimName : b2cClaims.getClaims().keySet()) {
            if (!isStandardClaim(claimName)) {
                awsCompatibleClaims.addCustomClaim(claimName, b2cClaims.getClaim(claimName));
            }
        }
        
        return awsCompatibleClaims;
    }
}
```

This comprehensive Azure target architecture design addresses all the requirements from the PRD and provides specific solutions for the high-risk migration areas identified in the AWS dependencies analysis. The design maintains backward compatibility while leveraging Azure-native services for improved security, performance, and reliability. 