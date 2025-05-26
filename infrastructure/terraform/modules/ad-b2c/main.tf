# Data source for current Azure AD configuration
data "azuread_client_config" "current" {}

# Azure AD B2C Tenant (Note: B2C tenants are typically created manually or via Azure CLI)
# This resource represents the configuration for an existing B2C tenant
resource "azuread_application" "auth_server" {
  display_name = "${var.name_prefix}-auth-server"
  
  # OAuth 2.0 configuration
  web {
    redirect_uris = [
      "https://${var.domain_name}/oauth/callback",
      "https://${var.domain_name}/auth/callback"
    ]
    
    implicit_grant {
      access_token_issuance_enabled = true
      id_token_issuance_enabled     = true
    }
  }

  # API permissions for Microsoft Graph
  required_resource_access {
    resource_app_id = "00000003-0000-0000-c000-000000000000" # Microsoft Graph

    resource_access {
      id   = "e1fe6dd8-ba31-4d61-89e7-88639da4683d" # User.Read
      type = "Scope"
    }
  }

  # Application ID URI
  identifier_uris = ["api://${var.name_prefix}-auth-server"]

  tags = [for k, v in var.tags : "${k}:${v}"]
}

# Service Principal for the application
resource "azuread_service_principal" "auth_server" {
  application_id = azuread_application.auth_server.application_id
  
  tags = [for k, v in var.tags : "${k}:${v}"]
}

# Application password/secret
resource "azuread_application_password" "auth_server" {
  application_object_id = azuread_application.auth_server.object_id
  display_name         = "${var.name_prefix}-auth-server-secret"
  end_date_relative    = "8760h" # 1 year
}

# Store the client secret in Key Vault
resource "azurerm_key_vault_secret" "b2c_client_secret" {
  name         = "b2c-client-secret"
  value        = azuread_application_password.auth_server.value
  key_vault_id = var.key_vault_id

  tags = var.tags
}

# Store the client ID in Key Vault
resource "azurerm_key_vault_secret" "b2c_client_id" {
  name         = "b2c-client-id"
  value        = azuread_application.auth_server.application_id
  key_vault_id = var.key_vault_id

  tags = var.tags
}

# Store the tenant ID in Key Vault
resource "azurerm_key_vault_secret" "b2c_tenant_id" {
  name         = "b2c-tenant-id"
  value        = data.azuread_client_config.current.tenant_id
  key_vault_id = var.key_vault_id

  tags = var.tags
}

# User flow for sign-up and sign-in (B2C specific)
# Note: This would typically be configured manually in the Azure portal
# or via Azure CLI as Terraform support for B2C user flows is limited

# Test users (if enabled)
resource "azuread_user" "test_users" {
  count = var.create_test_users ? 3 : 0
  
  user_principal_name = "testuser${count.index + 1}@${var.domain_name}"
  display_name        = "Test User ${count.index + 1}"
  mail_nickname       = "testuser${count.index + 1}"
  password            = "TempPassword123!"
}

# Store test user credentials in Key Vault (if test users are created)
resource "azurerm_key_vault_secret" "test_user_credentials" {
  count = var.create_test_users ? 3 : 0
  
  name         = "test-user-${count.index + 1}-credentials"
  value        = jsonencode({
    username = azuread_user.test_users[count.index].user_principal_name
    password = "TempPassword123!"
  })
  key_vault_id = var.key_vault_id

  tags = var.tags
} 