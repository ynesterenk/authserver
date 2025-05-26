output "tenant_id" {
  description = "Azure AD B2C Tenant ID"
  value       = data.azuread_client_config.current.tenant_id
}

output "client_id" {
  description = "Azure AD B2C Application (Client) ID"
  value       = azuread_application.auth_server.application_id
}

output "client_secret" {
  description = "Azure AD B2C Application Client Secret"
  value       = azuread_application_password.auth_server.value
  sensitive   = true
}

output "application_object_id" {
  description = "Azure AD B2C Application Object ID"
  value       = azuread_application.auth_server.object_id
}

output "service_principal_id" {
  description = "Azure AD B2C Service Principal ID"
  value       = azuread_service_principal.auth_server.id
}

output "jwks_url" {
  description = "JWKS URL for token validation"
  value       = "https://login.microsoftonline.com/${data.azuread_client_config.current.tenant_id}/discovery/v2.0/keys"
}

output "issuer_url" {
  description = "Token issuer URL"
  value       = "https://login.microsoftonline.com/${data.azuread_client_config.current.tenant_id}/v2.0"
}

output "authorization_endpoint" {
  description = "OAuth 2.0 authorization endpoint"
  value       = "https://login.microsoftonline.com/${data.azuread_client_config.current.tenant_id}/oauth2/v2.0/authorize"
}

output "token_endpoint" {
  description = "OAuth 2.0 token endpoint"
  value       = "https://login.microsoftonline.com/${data.azuread_client_config.current.tenant_id}/oauth2/v2.0/token"
}

output "test_users" {
  description = "Test user credentials (if created)"
  value = var.create_test_users ? [
    for user in azuread_user.test_users : {
      username = user.user_principal_name
      object_id = user.object_id
    }
  ] : []
}

output "domain_name" {
  description = "B2C domain name"
  value       = var.domain_name
}

output "tenant_name" {
  description = "B2C tenant name"
  value       = var.tenant_name
} 