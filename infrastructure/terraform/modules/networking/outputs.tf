output "vnet_id" {
  description = "ID of the virtual network"
  value       = azurerm_virtual_network.main.id
}

output "vnet_name" {
  description = "Name of the virtual network"
  value       = azurerm_virtual_network.main.name
}

output "function_subnet_id" {
  description = "ID of the function apps subnet"
  value       = azurerm_subnet.function_apps.id
}

output "function_subnet_name" {
  description = "Name of the function apps subnet"
  value       = azurerm_subnet.function_apps.name
}

output "apim_subnet_id" {
  description = "ID of the API Management subnet"
  value       = azurerm_subnet.apim.id
}

output "apim_subnet_name" {
  description = "Name of the API Management subnet"
  value       = azurerm_subnet.apim.name
}

output "private_endpoint_subnet_id" {
  description = "ID of the private endpoints subnet"
  value       = azurerm_subnet.private_endpoints.id
}

output "private_endpoint_subnet_name" {
  description = "Name of the private endpoints subnet"
  value       = azurerm_subnet.private_endpoints.name
}

output "function_nsg_id" {
  description = "ID of the function apps network security group"
  value       = azurerm_network_security_group.function_apps.id
}

output "apim_nsg_id" {
  description = "ID of the API Management network security group"
  value       = azurerm_network_security_group.apim.id
}

output "private_endpoint_nsg_id" {
  description = "ID of the private endpoints network security group"
  value       = azurerm_network_security_group.private_endpoints.id
} 