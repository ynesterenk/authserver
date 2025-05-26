output "storage_account_id" {
  description = "ID of the storage account"
  value       = azurerm_storage_account.main.id
}

output "storage_account_name" {
  description = "Name of the storage account"
  value       = azurerm_storage_account.main.name
}

output "storage_account_primary_access_key" {
  description = "Primary access key for the storage account"
  value       = azurerm_storage_account.main.primary_access_key
  sensitive   = true
}

output "storage_account_secondary_access_key" {
  description = "Secondary access key for the storage account"
  value       = azurerm_storage_account.main.secondary_access_key
  sensitive   = true
}

output "storage_account_primary_connection_string" {
  description = "Primary connection string for the storage account"
  value       = azurerm_storage_account.main.primary_connection_string
  sensitive   = true
}

output "storage_account_secondary_connection_string" {
  description = "Secondary connection string for the storage account"
  value       = azurerm_storage_account.main.secondary_connection_string
  sensitive   = true
}

output "storage_account_primary_blob_endpoint" {
  description = "Primary blob endpoint for the storage account"
  value       = azurerm_storage_account.main.primary_blob_endpoint
}

output "function_packages_container_name" {
  description = "Name of the function packages container"
  value       = azurerm_storage_container.function_packages.name
}

output "logs_container_name" {
  description = "Name of the logs container"
  value       = azurerm_storage_container.logs.name
}

output "backups_container_name" {
  description = "Name of the backups container"
  value       = azurerm_storage_container.backups.name
}

output "geo_redundant_storage_account_id" {
  description = "ID of the geo-redundant storage account"
  value       = var.enable_geo_redundancy ? azurerm_storage_account.geo_redundant[0].id : null
}

output "geo_redundant_storage_account_name" {
  description = "Name of the geo-redundant storage account"
  value       = var.enable_geo_redundancy ? azurerm_storage_account.geo_redundant[0].name : null
} 