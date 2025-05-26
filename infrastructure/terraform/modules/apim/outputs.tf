output "id" {
  description = "ID of the API Management instance"
  value       = azurerm_api_management.main.id
}

output "name" {
  description = "Name of the API Management instance"
  value       = azurerm_api_management.main.name
}

output "gateway_url" {
  description = "Gateway URL of the API Management instance"
  value       = azurerm_api_management.main.gateway_url
}

output "management_api_url" {
  description = "Management API URL of the API Management instance"
  value       = azurerm_api_management.main.management_api_url
}

output "developer_portal_url" {
  description = "Developer portal URL of the API Management instance"
  value       = azurerm_api_management.main.developer_portal_url
}

output "principal_id" {
  description = "Principal ID of the API Management managed identity"
  value       = azurerm_api_management.main.identity[0].principal_id
}

output "public_ip_addresses" {
  description = "Public IP addresses of the API Management instance"
  value       = azurerm_api_management.main.public_ip_addresses
}

output "private_ip_addresses" {
  description = "Private IP addresses of the API Management instance"
  value       = azurerm_api_management.main.private_ip_addresses
}

# Subscription key outputs (sensitive)
output "oauth_subscription_key" {
  description = "Primary subscription key for OAuth API access"
  value       = azurerm_api_management_subscription.oauth_subscription.primary_key
  sensitive   = true
}

output "full_access_subscription_key" {
  description = "Primary subscription key for full API access"
  value       = azurerm_api_management_subscription.full_access_subscription.primary_key
  sensitive   = true
}

output "auth_subscription_key" {
  description = "Primary subscription key for authorization API access"
  value       = azurerm_api_management_subscription.auth_subscription.primary_key
  sensitive   = true
}

# Product information
output "oauth_product_id" {
  description = "ID of the OAuth API product"
  value       = azurerm_api_management_product.oauth_product.product_id
}

output "auth_product_id" {
  description = "ID of the Authorization API product"
  value       = azurerm_api_management_product.auth_product.product_id
}

output "full_access_product_id" {
  description = "ID of the Full Access API product"
  value       = azurerm_api_management_product.full_access_product.product_id
}

output "dev_product_id" {
  description = "ID of the Development API product (no subscription required)"
  value       = azurerm_api_management_product.dev_product.product_id
} 