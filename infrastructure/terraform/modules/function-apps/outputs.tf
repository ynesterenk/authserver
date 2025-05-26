# Service Plan Outputs
output "service_plan_id" {
  description = "ID of the App Service Plan"
  value       = azurerm_service_plan.main.id
}

output "service_plan_name" {
  description = "Name of the App Service Plan"
  value       = azurerm_service_plan.main.name
}

# OAuth Server Function App Outputs
output "oauth_function_id" {
  description = "ID of the OAuth Function App"
  value       = azurerm_linux_function_app.oauth_server.id
}

output "oauth_function_name" {
  description = "Name of the OAuth Function App"
  value       = azurerm_linux_function_app.oauth_server.name
}

output "oauth_function_url" {
  description = "URL of the OAuth Function App"
  value       = "https://${azurerm_linux_function_app.oauth_server.default_hostname}/api"
}

output "oauth_function_principal_id" {
  description = "Principal ID of the OAuth Function App managed identity"
  value       = azurerm_linux_function_app.oauth_server.identity[0].principal_id
}

# JWT Authorizer Function App Outputs
output "jwt_authorizer_function_id" {
  description = "ID of the JWT Authorizer Function App"
  value       = azurerm_linux_function_app.jwt_authorizer.id
}

output "jwt_authorizer_function_name" {
  description = "Name of the JWT Authorizer Function App"
  value       = azurerm_linux_function_app.jwt_authorizer.name
}

output "jwt_authorizer_function_url" {
  description = "URL of the JWT Authorizer Function App"
  value       = "https://${azurerm_linux_function_app.jwt_authorizer.default_hostname}/api"
}

output "jwt_authorizer_function_principal_id" {
  description = "Principal ID of the JWT Authorizer Function App managed identity"
  value       = azurerm_linux_function_app.jwt_authorizer.identity[0].principal_id
}

# Basic Auth Function App Outputs
output "basic_auth_function_id" {
  description = "ID of the Basic Auth Function App"
  value       = azurerm_linux_function_app.basic_authenticator.id
}

output "basic_auth_function_name" {
  description = "Name of the Basic Auth Function App"
  value       = azurerm_linux_function_app.basic_authenticator.name
}

output "basic_auth_function_url" {
  description = "URL of the Basic Auth Function App"
  value       = "https://${azurerm_linux_function_app.basic_authenticator.default_hostname}/api"
}

output "basic_auth_function_principal_id" {
  description = "Principal ID of the Basic Auth Function App managed identity"
  value       = azurerm_linux_function_app.basic_authenticator.identity[0].principal_id
}

# Password Change Function App Outputs
output "password_change_function_id" {
  description = "ID of the Password Change Function App"
  value       = azurerm_linux_function_app.password_change.id
}

output "password_change_function_name" {
  description = "Name of the Password Change Function App"
  value       = azurerm_linux_function_app.password_change.name
}

output "password_change_function_url" {
  description = "URL of the Password Change Function App"
  value       = "https://${azurerm_linux_function_app.password_change.default_hostname}/api"
}

output "password_change_function_principal_id" {
  description = "Principal ID of the Password Change Function App managed identity"
  value       = azurerm_linux_function_app.password_change.identity[0].principal_id
}

# Collective Outputs
output "all_function_principal_ids" {
  description = "List of all Function App managed identity principal IDs"
  value = [
    azurerm_linux_function_app.oauth_server.identity[0].principal_id,
    azurerm_linux_function_app.jwt_authorizer.identity[0].principal_id,
    azurerm_linux_function_app.basic_authenticator.identity[0].principal_id,
    azurerm_linux_function_app.password_change.identity[0].principal_id
  ]
}

output "all_function_urls" {
  description = "Map of all Function App URLs"
  value = {
    oauth_server      = "https://${azurerm_linux_function_app.oauth_server.default_hostname}/api"
    jwt_authorizer    = "https://${azurerm_linux_function_app.jwt_authorizer.default_hostname}/api"
    basic_auth        = "https://${azurerm_linux_function_app.basic_authenticator.default_hostname}/api"
    password_change   = "https://${azurerm_linux_function_app.password_change.default_hostname}/api"
  }
} 