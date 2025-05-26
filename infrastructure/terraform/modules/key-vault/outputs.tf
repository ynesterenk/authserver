output "id" {
  description = "ID of the Key Vault"
  value       = azurerm_key_vault.main.id
}

output "name" {
  description = "Name of the Key Vault"
  value       = azurerm_key_vault.main.name
}

output "vault_uri" {
  description = "URI of the Key Vault"
  value       = azurerm_key_vault.main.vault_uri
}

output "resource_group_name" {
  description = "Resource group name of the Key Vault"
  value       = azurerm_key_vault.main.resource_group_name
}

output "location" {
  description = "Location of the Key Vault"
  value       = azurerm_key_vault.main.location
}

output "tenant_id" {
  description = "Tenant ID of the Key Vault"
  value       = azurerm_key_vault.main.tenant_id
}

output "private_endpoint_ip" {
  description = "Private IP address of the Key Vault private endpoint"
  value       = var.enable_private_endpoints ? azurerm_private_endpoint.key_vault[0].private_service_connection[0].private_ip_address : null
}

output "private_dns_zone_name" {
  description = "Name of the private DNS zone for Key Vault"
  value       = var.enable_private_endpoints ? azurerm_private_dns_zone.key_vault[0].name : null
} 