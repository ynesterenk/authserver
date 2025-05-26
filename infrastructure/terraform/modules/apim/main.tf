# API Management instance (equivalent to AWS API Gateway)
resource "azurerm_api_management" "main" {
  name                = "${var.name_prefix}-apim"
  location            = var.location
  resource_group_name = var.resource_group_name
  publisher_name      = var.publisher_name
  publisher_email     = var.publisher_email
  sku_name           = var.sku_name
  tags               = var.tags

  # VNet integration for internal APIM
  dynamic "virtual_network_configuration" {
    for_each = var.subnet_id != null ? [1] : []
    content {
      subnet_id = var.subnet_id
    }
  }

  # Security configuration
  security {
    enable_backend_ssl30                = false
    enable_backend_tls10                = false
    enable_backend_tls11                = false
    enable_frontend_ssl30               = false
    enable_frontend_tls10               = false
    enable_frontend_tls11               = false
    tls_ecdhe_ecdsa_with_aes256_cbc_sha = false
    tls_ecdhe_ecdsa_with_aes128_cbc_sha = false
    tls_ecdhe_rsa_with_aes256_cbc_sha   = false
    tls_ecdhe_rsa_with_aes128_cbc_sha   = false
    tls_rsa_with_aes128_gcm_sha256      = false
    tls_rsa_with_aes256_cbc_sha256      = false
    tls_rsa_with_aes128_cbc_sha256      = false
    tls_rsa_with_aes256_cbc_sha         = false
    tls_rsa_with_aes128_cbc_sha         = false
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

  credentials {
    header = {
      "x-functions-key" = "{{oauth-function-key}}"
    }
  }
}

# Backend configuration for JWT Authorizer Function
resource "azurerm_api_management_backend" "jwt_backend" {
  name                = "jwt-backend"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  protocol            = "http"
  url                 = var.jwt_authorizer_function_url

  credentials {
    header = {
      "x-functions-key" = "{{jwt-function-key}}"
    }
  }
}

# Backend configuration for Basic Auth Function
resource "azurerm_api_management_backend" "basic_auth_backend" {
  name                = "basic-auth-backend"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  protocol            = "http"
  url                 = var.basic_auth_function_url

  credentials {
    header = {
      "x-functions-key" = "{{basic-auth-function-key}}"
    }
  }
}

# Backend configuration for Password Change Function
resource "azurerm_api_management_backend" "password_backend" {
  name                = "password-backend"
  resource_group_name = var.resource_group_name
  api_management_name = azurerm_api_management.main.name
  protocol            = "http"
  url                 = var.password_change_function_url

  credentials {
    header = {
      "x-functions-key" = "{{password-function-key}}"
    }
  }
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
      schema_id    = "default"
      type_name    = "TokenRequest"
    }
  }

  response {
    status_code = 200
    description = "Token response"
    
    representation {
      content_type = "application/json"
      schema_id    = "default"
      type_name    = "TokenResponse"
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
      "content-length",
      "origin"
    ]
  }

  backend_request {
    body_bytes = 1024
    headers_to_log = [
      "content-type",
      "accept"
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