# Random string for unique naming
resource "random_string" "unique" {
  length  = 8
  special = false
  upper   = false
}

# Service Plan for Function Apps
resource "azurerm_service_plan" "main" {
  name                = "${var.name_prefix}-asp"
  resource_group_name = var.resource_group_name
  location            = var.location
  os_type             = "Linux"
  sku_name            = var.service_plan_sku
  tags                = var.tags
}

# OAuth Server Function App (equivalent to AWS Lambda ServerLambda)
resource "azurerm_linux_function_app" "oauth_server" {
  name                = "${var.name_prefix}-oauth-${random_string.unique.result}"
  resource_group_name = var.resource_group_name
  location            = var.location
  service_plan_id     = azurerm_service_plan.main.id
  storage_account_name       = var.storage_account_name
  storage_account_access_key = var.storage_account_access_key
  tags                = var.tags

  site_config {
    application_stack {
      java_version = var.runtime_version
    }
    
    application_insights_connection_string = var.application_insights_connection_string
    
    cors {
      allowed_origins = ["*"]
      support_credentials = false
    }
    
    dynamic "ip_restriction" {
      for_each = var.allowed_ip_ranges
      content {
        ip_address = ip_restriction.value
        action     = "Allow"
        priority   = 100 + ip_restriction.key
      }
    }
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME"              = "java"
    "WEBSITE_RUN_FROM_PACKAGE"              = "1"
    "APPLICATIONINSIGHTS_CONNECTION_STRING" = var.application_insights_connection_string
    
    # Azure AD B2C Configuration (equivalent to AWS Cognito)
    "B2C_TENANT_ID"   = var.b2c_tenant_id
    "B2C_CLIENT_ID"   = var.b2c_client_id
    "B2C_JWKS_URL"    = var.b2c_jwks_url
    "B2C_DOMAIN_NAME" = var.b2c_domain_name
    
    # Key Vault Configuration
    "KEY_VAULT_URL" = "@Microsoft.KeyVault(VaultName=${var.key_vault_name};SecretName=key-vault-url)"
    
    # Function Configuration
    "FUNCTION_TIMEOUT"   = var.function_timeout
    "FUNCTION_MEMORY"    = var.memory_size
    "ENABLE_DEBUG_LOGS"  = var.enable_debug_logging
    
    # Environment
    "ENVIRONMENT" = var.environment
  }

  identity {
    type = "SystemAssigned"
  }

  # VNet integration
  virtual_network_subnet_id = var.subnet_id

  lifecycle {
    ignore_changes = [
      app_settings["WEBSITE_RUN_FROM_PACKAGE"]
    ]
  }
}

# JWT Authorizer Function App (equivalent to AWS Lambda JwtAuthorizerLambda)
resource "azurerm_linux_function_app" "jwt_authorizer" {
  name                = "${var.name_prefix}-jwt-auth-${random_string.unique.result}"
  resource_group_name = var.resource_group_name
  location            = var.location
  service_plan_id     = azurerm_service_plan.main.id
  storage_account_name       = var.storage_account_name
  storage_account_access_key = var.storage_account_access_key
  tags                = var.tags

  site_config {
    application_stack {
      java_version = var.runtime_version
    }
    
    application_insights_connection_string = var.application_insights_connection_string
    
    cors {
      allowed_origins = ["*"]
      support_credentials = false
    }
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME"              = "java"
    "WEBSITE_RUN_FROM_PACKAGE"              = "1"
    "APPLICATIONINSIGHTS_CONNECTION_STRING" = var.application_insights_connection_string
    
    # Azure AD B2C Configuration
    "B2C_TENANT_ID"   = var.b2c_tenant_id
    "B2C_JWKS_URL"    = var.b2c_jwks_url
    "B2C_DOMAIN_NAME" = var.b2c_domain_name
    
    # Key Vault Configuration
    "KEY_VAULT_URL" = "@Microsoft.KeyVault(VaultName=${var.key_vault_name};SecretName=key-vault-url)"
    
    # Function Configuration
    "FUNCTION_TIMEOUT"   = var.function_timeout
    "ENABLE_DEBUG_LOGS"  = var.enable_debug_logging
    
    # Environment
    "ENVIRONMENT" = var.environment
  }

  identity {
    type = "SystemAssigned"
  }

  # VNet integration
  virtual_network_subnet_id = var.subnet_id

  lifecycle {
    ignore_changes = [
      app_settings["WEBSITE_RUN_FROM_PACKAGE"]
    ]
  }
}

# Basic Authenticator Function App (equivalent to AWS Lambda BasicAuthenticatorLambda)
resource "azurerm_linux_function_app" "basic_authenticator" {
  name                = "${var.name_prefix}-basic-auth-${random_string.unique.result}"
  resource_group_name = var.resource_group_name
  location            = var.location
  service_plan_id     = azurerm_service_plan.main.id
  storage_account_name       = var.storage_account_name
  storage_account_access_key = var.storage_account_access_key
  tags                = var.tags

  site_config {
    application_stack {
      java_version = var.runtime_version
    }
    
    application_insights_connection_string = var.application_insights_connection_string
    
    cors {
      allowed_origins = ["*"]
      support_credentials = false
    }
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME"              = "java"
    "WEBSITE_RUN_FROM_PACKAGE"              = "1"
    "APPLICATIONINSIGHTS_CONNECTION_STRING" = var.application_insights_connection_string
    
    # Azure AD B2C Configuration
    "B2C_TENANT_ID"   = var.b2c_tenant_id
    "B2C_CLIENT_ID"   = var.b2c_client_id
    "B2C_DOMAIN_NAME" = var.b2c_domain_name
    
    # Key Vault Configuration
    "KEY_VAULT_URL" = "@Microsoft.KeyVault(VaultName=${var.key_vault_name};SecretName=key-vault-url)"
    
    # Function Configuration
    "FUNCTION_TIMEOUT"   = var.function_timeout
    "ENABLE_DEBUG_LOGS"  = var.enable_debug_logging
    
    # Environment
    "ENVIRONMENT" = var.environment
  }

  identity {
    type = "SystemAssigned"
  }

  # VNet integration
  virtual_network_subnet_id = var.subnet_id

  lifecycle {
    ignore_changes = [
      app_settings["WEBSITE_RUN_FROM_PACKAGE"]
    ]
  }
}

# Password Change Function App (new functionality)
resource "azurerm_linux_function_app" "password_change" {
  name                = "${var.name_prefix}-pwd-change-${random_string.unique.result}"
  resource_group_name = var.resource_group_name
  location            = var.location
  service_plan_id     = azurerm_service_plan.main.id
  storage_account_name       = var.storage_account_name
  storage_account_access_key = var.storage_account_access_key
  tags                = var.tags

  site_config {
    application_stack {
      java_version = var.runtime_version
    }
    
    application_insights_connection_string = var.application_insights_connection_string
    
    cors {
      allowed_origins = ["*"]
      support_credentials = false
    }
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME"              = "java"
    "WEBSITE_RUN_FROM_PACKAGE"              = "1"
    "APPLICATIONINSIGHTS_CONNECTION_STRING" = var.application_insights_connection_string
    
    # Azure AD B2C Configuration
    "B2C_TENANT_ID"   = var.b2c_tenant_id
    "B2C_CLIENT_ID"   = var.b2c_client_id
    "B2C_DOMAIN_NAME" = var.b2c_domain_name
    
    # Key Vault Configuration
    "KEY_VAULT_URL" = "@Microsoft.KeyVault(VaultName=${var.key_vault_name};SecretName=key-vault-url)"
    
    # Function Configuration
    "FUNCTION_TIMEOUT"   = var.function_timeout
    "ENABLE_DEBUG_LOGS"  = var.enable_debug_logging
    
    # Environment
    "ENVIRONMENT" = var.environment
  }

  identity {
    type = "SystemAssigned"
  }

  # VNet integration
  virtual_network_subnet_id = var.subnet_id

  lifecycle {
    ignore_changes = [
      app_settings["WEBSITE_RUN_FROM_PACKAGE"]
    ]
  }
}

# Auto-scaling configuration for Premium plans
resource "azurerm_monitor_autoscale_setting" "function_apps" {
  count               = var.enable_auto_scaling && var.service_plan_sku != "Y1" ? 1 : 0
  name                = "${var.name_prefix}-autoscale"
  resource_group_name = var.resource_group_name
  location            = var.location
  target_resource_id  = azurerm_service_plan.main.id

  profile {
    name = "default"

    capacity {
      default = 1
      minimum = 1
      maximum = var.max_instances
    }

    rule {
      metric_trigger {
        metric_name        = "CpuPercentage"
        metric_resource_id = azurerm_service_plan.main.id
        time_grain         = "PT1M"
        statistic          = "Average"
        time_window        = "PT5M"
        time_aggregation   = "Average"
        operator           = "GreaterThan"
        threshold          = 70
      }

      scale_action {
        direction = "Increase"
        type      = "ChangeCount"
        value     = "1"
        cooldown  = "PT5M"
      }
    }

    rule {
      metric_trigger {
        metric_name        = "CpuPercentage"
        metric_resource_id = azurerm_service_plan.main.id
        time_grain         = "PT1M"
        statistic          = "Average"
        time_window        = "PT5M"
        time_aggregation   = "Average"
        operator           = "LessThan"
        threshold          = 30
      }

      scale_action {
        direction = "Decrease"
        type      = "ChangeCount"
        value     = "1"
        cooldown  = "PT5M"
      }
    }
  }

  tags = var.tags
} 