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