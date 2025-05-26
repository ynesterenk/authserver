# API Management instance (equivalent to AWS API Gateway)
resource "azurerm_api_management" "main" {
  name                = "${var.name_prefix}-apim"
  location            = var.location
  resource_group_name = var.resource_group_name
  publisher_name      = var.publisher_name
  publisher_email     = var.publisher_email
  sku_name            = var.sku_name
  virtual_network_type = "External"
  tags                = var.tags

  # VNet integration for internal APIM
  dynamic "virtual_network_configuration" {
    for_each = var.subnet_id != null ? [1] : []
    content {
      subnet_id = var.subnet_id
    }
  }

  # Security configuration
  security {
    enable_backend_ssl30  = false
    enable_backend_tls10  = false
    enable_backend_tls11  = false
    enable_frontend_ssl30 = false
    enable_frontend_tls10 = false
    enable_frontend_tls11 = false
  }

  # Identity for managed identity
  identity {
    type = "SystemAssigned"
  }
}

# API definition for OAuth endpoints
resource "azurerm_api_management_api" "oauth_api" {
  name                = "oauth-api"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  revision            = "1"
  display_name        = "OAuth API"
  path                = "oauth"
  protocols           = ["https"]
  service_url         = var.oauth_function_url

  import {
    content_format = "openapi+json"
    content_value = jsonencode({
      openapi = "3.0.0"
      info = {
        title   = "OAuth API"
        version = "1.0.0"
      }
      paths = {
        "/token" = {
          post = {
            summary     = "Get OAuth token"
            operationId = "getToken"
            requestBody = {
              required = true
              content = {
                "application/json" = {
                  schema = {
                    type = "object"
                    properties = {
                      grant_type    = { type = "string" }
                      client_id     = { type = "string" }
                      client_secret = { type = "string" }
                    }
                    required = ["grant_type", "client_id", "client_secret"]
                  }
                }
              }
            }
            responses = {
              "200" = {
                description = "Token response"
                content = {
                  "application/json" = {
                    schema = {
                      type = "object"
                      properties = {
                        access_token = { type = "string" }
                        token_type   = { type = "string" }
                        expires_in   = { type = "integer" }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    })
  }
}

# API definition for Authorization endpoints
resource "azurerm_api_management_api" "auth_api" {
  name                = "auth-api"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  revision            = "1"
  display_name        = "Authorization API"
  path                = "authorize"
  protocols           = ["https"]

  import {
    content_format = "openapi+json"
    content_value = jsonencode({
      openapi = "3.0.0"
      info = {
        title   = "Authorization API"
        version = "1.0.0"
      }
      paths = {
        "/jwt" = {
          post = {
            summary     = "JWT Authorization"
            operationId = "authorizeJwt"
            responses = {
              "200" = { description = "Authorization response" }
            }
          }
        }
        "/basic" = {
          post = {
            summary     = "Basic Authorization"
            operationId = "authorizeBasic"
            responses = {
              "200" = { description = "Authorization response" }
            }
          }
        }
      }
    })
  }
}

# API definition for Password Change endpoint
resource "azurerm_api_management_api" "password_api" {
  name                = "password-api"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  revision            = "1"
  display_name        = "Password Management API"
  path                = ""
  protocols           = ["https"]
  service_url         = var.password_change_function_url

  import {
    content_format = "openapi+json"
    content_value = jsonencode({
      openapi = "3.0.0"
      info = {
        title   = "Password Management API"
        version = "1.0.0"
      }
      paths = {
        "/change-password" = {
          post = {
            summary     = "Change password"
            operationId = "changePassword"
            responses = {
              "200" = { description = "Password change response" }
            }
          }
        }
        "/health" = {
          get = {
            summary     = "Health check"
            operationId = "healthCheck"
            responses = {
              "200" = { description = "Health status" }
            }
          }
        }
      }
    })
  }
}

# Backend configuration for OAuth Function
resource "azurerm_api_management_backend" "oauth_backend" {
  name                = "oauth-backend"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  protocol            = "http"
  url                 = var.oauth_function_url


}

# Backend configuration for JWT Authorizer Function
resource "azurerm_api_management_backend" "jwt_backend" {
  name                = "jwt-backend"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  protocol            = "http"
  url                 = var.jwt_authorizer_function_url


}

# Backend configuration for Basic Auth Function
resource "azurerm_api_management_backend" "basic_auth_backend" {
  name                = "basic-auth-backend"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  protocol            = "http"
  url                 = var.basic_auth_function_url


}

# Backend configuration for Password Change Function
resource "azurerm_api_management_backend" "password_backend" {
  name                = "password-backend"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  protocol            = "http"
  url                 = var.password_change_function_url


}

# API Operation for OAuth token endpoint
resource "azurerm_api_management_api_operation" "oauth_token" {
  operation_id        = "oauth-token"
  api_name            = azurerm_api_management_api.oauth_api.name
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  display_name        = "Get OAuth Token"
  method              = "POST"
  url_template        = "/token"
  description         = "OAuth 2.0 client credentials token endpoint"

  request {
    description = "OAuth token request"
    
    representation {
      content_type = "application/json"
    }
  }

  response {
    status_code = 200
    description = "Token response"
    
    representation {
      content_type = "application/json"
    }
  }
}

# API Operation for JWT authorization
resource "azurerm_api_management_api_operation" "jwt_authorize" {
  operation_id        = "jwt-authorize"
  api_name            = azurerm_api_management_api.auth_api.name
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  display_name        = "JWT Authorization"
  method              = "POST"
  url_template        = "/jwt"
  description         = "JWT token authorization endpoint"
}

# API Operation for Basic authorization
resource "azurerm_api_management_api_operation" "basic_authorize" {
  operation_id        = "basic-authorize"
  api_name            = azurerm_api_management_api.auth_api.name
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  display_name        = "Basic Authorization"
  method              = "POST"
  url_template        = "/basic"
  description         = "Basic authentication authorization endpoint"
}

# API Operation for password change
resource "azurerm_api_management_api_operation" "password_change" {
  operation_id        = "password-change"
  api_name            = azurerm_api_management_api.password_api.name
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  display_name        = "Change Password"
  method              = "POST"
  url_template        = "/change-password"
  description         = "Password change endpoint"
}

# API Operation for health check
resource "azurerm_api_management_api_operation" "health_check" {
  operation_id        = "health-check"
  api_name            = azurerm_api_management_api.password_api.name
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  display_name        = "Health Check"
  method              = "GET"
  url_template        = "/health"
  description         = "System health check endpoint"
}

# Policy for OAuth API to route to Function App
resource "azurerm_api_management_api_operation_policy" "oauth_token_policy" {
  api_name            = azurerm_api_management_api.oauth_api.name
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  operation_id        = azurerm_api_management_api_operation.oauth_token.operation_id

  xml_content = <<XML
<policies>
  <inbound>
    <base />
    <set-backend-service backend-id="oauth-backend" />
    <cors>
      <allowed-origins>
        <origin>*</origin>
      </allowed-origins>
      <allowed-methods>
        <method>GET</method>
        <method>POST</method>
        <method>PUT</method>
        <method>DELETE</method>
        <method>HEAD</method>
        <method>OPTIONS</method>
        <method>PATCH</method>
        <method>TRACE</method>
      </allowed-methods>
      <allowed-headers>
        <header>*</header>
      </allowed-headers>
    </cors>
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
XML
}

# Policy for JWT authorization
resource "azurerm_api_management_api_operation_policy" "jwt_authorize_policy" {
  api_name            = azurerm_api_management_api.auth_api.name
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  operation_id        = azurerm_api_management_api_operation.jwt_authorize.operation_id

  xml_content = <<XML
<policies>
  <inbound>
    <base />
    <set-backend-service backend-id="jwt-backend" />
    <cors>
      <allowed-origins>
        <origin>*</origin>
      </allowed-origins>
      <allowed-methods>
        <method>POST</method>
        <method>OPTIONS</method>
      </allowed-methods>
      <allowed-headers>
        <header>*</header>
      </allowed-headers>
    </cors>
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
XML
}

# Policy for Basic authorization
resource "azurerm_api_management_api_operation_policy" "basic_authorize_policy" {
  api_name            = azurerm_api_management_api.auth_api.name
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  operation_id        = azurerm_api_management_api_operation.basic_authorize.operation_id

  xml_content = <<XML
<policies>
  <inbound>
    <base />
    <set-backend-service backend-id="basic-auth-backend" />
    <cors>
      <allowed-origins>
        <origin>*</origin>
      </allowed-origins>
      <allowed-methods>
        <method>POST</method>
        <method>OPTIONS</method>
      </allowed-methods>
      <allowed-headers>
        <header>*</header>
      </allowed-headers>
    </cors>
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
XML
}

# Policy for password change
resource "azurerm_api_management_api_operation_policy" "password_change_policy" {
  api_name            = azurerm_api_management_api.password_api.name
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  operation_id        = azurerm_api_management_api_operation.password_change.operation_id

  xml_content = <<XML
<policies>
  <inbound>
    <base />
    <set-backend-service backend-id="password-backend" />
    <cors>
      <allowed-origins>
        <origin>*</origin>
      </allowed-origins>
      <allowed-methods>
        <method>POST</method>
        <method>OPTIONS</method>
      </allowed-methods>
      <allowed-headers>
        <header>*</header>
      </allowed-headers>
    </cors>
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
XML
}

# Application Insights integration
resource "azurerm_api_management_logger" "app_insights" {
  name                = "app-insights-logger"
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name

  application_insights {
    instrumentation_key = var.application_insights_instrumentation_key
  }
}

# Diagnostic settings for monitoring
resource "azurerm_api_management_diagnostic" "app_insights" {
  identifier               = "applicationinsights"
  resource_group_name      = var.resource_group_name
  api_management_name      = azurerm_api_management.main.name
  api_management_logger_id = azurerm_api_management_logger.app_insights.id

  sampling_percentage       = 100.0
  always_log_errors        = true
  log_client_ip            = true
  verbosity                = "information"
  http_correlation_protocol = "W3C"

  frontend_request {
    body_bytes = 1024
    headers_to_log = [
      "content-type",
      "accept",
      "origin"
    ]
  }

  frontend_response {
    body_bytes = 1024
    headers_to_log = [
      "content-type",
      "content-length"
    ]
  }

  backend_request {
    body_bytes = 1024
    headers_to_log = [
      "content-type",
      "accept",
      "origin"
    ]
  }

  backend_response {
    body_bytes = 1024
    headers_to_log = [
      "content-type",
      "content-length"
    ]
  }
}

# Product for OAuth API access
resource "azurerm_api_management_product" "oauth_product" {
  product_id            = "oauth-access"
  api_management_name   = azurerm_api_management.main.name
  resource_group_name   = var.resource_group_name
  display_name          = "OAuth API Access"
  description           = "Access to OAuth token endpoints"
  subscription_required = true
  approval_required     = false
  published             = true
  
  # Rate limiting
  subscriptions_limit = 100
  
  # Terms of use
  terms                = "By using this API, you agree to the terms and conditions."
}

# Product for Authorization API access
resource "azurerm_api_management_product" "auth_product" {
  product_id            = "auth-access"
  api_management_name   = azurerm_api_management.main.name
  resource_group_name   = var.resource_group_name
  display_name          = "Authorization API Access"
  description           = "Access to JWT and Basic authorization endpoints"
  subscription_required = true
  approval_required     = false
  published             = true
  
  # Rate limiting
  subscriptions_limit = 100
  
  # Terms of use
  terms                = "By using this API, you agree to the terms and conditions."
}

# Product for full API access (includes all endpoints)
resource "azurerm_api_management_product" "full_access_product" {
  product_id            = "full-access"
  api_management_name   = azurerm_api_management.main.name
  resource_group_name   = var.resource_group_name
  display_name          = "Full API Access"
  description           = "Complete access to all authentication and authorization endpoints"
  subscription_required = true
  approval_required     = false
  published             = true
  
  # Rate limiting
  subscriptions_limit = 50
  
  # Terms of use
  terms                = "By using this API, you agree to the terms and conditions."
}

# Product for development/testing (no subscription required)
resource "azurerm_api_management_product" "dev_product" {
  product_id            = "dev-access"
  api_management_name   = azurerm_api_management.main.name
  resource_group_name   = var.resource_group_name
  display_name          = "Development Access"
  description           = "Development and testing access - no subscription required"
  subscription_required = false
  approval_required     = false
  published             = true
  
  # Terms of use
  terms                = "Development use only. Not for production traffic."
}

# Associate OAuth API with products
resource "azurerm_api_management_product_api" "oauth_product_api" {
  api_name            = azurerm_api_management_api.oauth_api.name
  product_id          = azurerm_api_management_product.oauth_product.product_id
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
}

resource "azurerm_api_management_product_api" "oauth_full_access" {
  api_name            = azurerm_api_management_api.oauth_api.name
  product_id          = azurerm_api_management_product.full_access_product.product_id
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
}

resource "azurerm_api_management_product_api" "oauth_dev_access" {
  api_name            = azurerm_api_management_api.oauth_api.name
  product_id          = azurerm_api_management_product.dev_product.product_id
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
}

# Associate Authorization API with products
resource "azurerm_api_management_product_api" "auth_product_api" {
  api_name            = azurerm_api_management_api.auth_api.name
  product_id          = azurerm_api_management_product.auth_product.product_id
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
}

resource "azurerm_api_management_product_api" "auth_full_access" {
  api_name            = azurerm_api_management_api.auth_api.name
  product_id          = azurerm_api_management_product.full_access_product.product_id
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
}

resource "azurerm_api_management_product_api" "auth_dev_access" {
  api_name            = azurerm_api_management_api.auth_api.name
  product_id          = azurerm_api_management_product.dev_product.product_id
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
}

# Associate Password API with products
resource "azurerm_api_management_product_api" "password_full_access" {
  api_name            = azurerm_api_management_api.password_api.name
  product_id          = azurerm_api_management_product.full_access_product.product_id
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
}

resource "azurerm_api_management_product_api" "password_dev_access" {
  api_name            = azurerm_api_management_api.password_api.name
  product_id          = azurerm_api_management_product.dev_product.product_id
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
}

# Create subscription for OAuth access
resource "azurerm_api_management_subscription" "oauth_subscription" {
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  product_id          = azurerm_api_management_product.oauth_product.id
  display_name        = "OAuth API Subscription"
  state               = "active"
  allow_tracing       = true
}

# Create subscription for full access
resource "azurerm_api_management_subscription" "full_access_subscription" {
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  product_id          = azurerm_api_management_product.full_access_product.id
  display_name        = "Full Access Subscription"
  state               = "active"
  allow_tracing       = true
}

# Create subscription for authorization access
resource "azurerm_api_management_subscription" "auth_subscription" {
  api_management_name = azurerm_api_management.main.name
  resource_group_name = var.resource_group_name
  product_id          = azurerm_api_management_product.auth_product.id
  display_name        = "Authorization API Subscription"
  state               = "active"
  allow_tracing       = true
}

# Store subscription keys in Key Vault for secure access
resource "azurerm_key_vault_secret" "oauth_subscription_key" {
  name         = "apim-oauth-subscription-key"
  value        = azurerm_api_management_subscription.oauth_subscription.primary_key
  key_vault_id = var.key_vault_id
  
  depends_on = [azurerm_api_management_subscription.oauth_subscription]
}

resource "azurerm_key_vault_secret" "full_access_subscription_key" {
  name         = "apim-full-access-subscription-key"
  value        = azurerm_api_management_subscription.full_access_subscription.primary_key
  key_vault_id = var.key_vault_id
  
  depends_on = [azurerm_api_management_subscription.full_access_subscription]
}

resource "azurerm_key_vault_secret" "auth_subscription_key" {
  name         = "apim-auth-subscription-key"
  value        = azurerm_api_management_subscription.auth_subscription.primary_key
  key_vault_id = var.key_vault_id
  
  depends_on = [azurerm_api_management_subscription.auth_subscription]
} 