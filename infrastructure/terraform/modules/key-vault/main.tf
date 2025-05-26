# Generate a random suffix for the Key Vault name to ensure uniqueness
resource "random_string" "key_vault_suffix" {
  length  = 8
  special = false
  upper   = false
}

# Azure Key Vault
resource "azurerm_key_vault" "main" {
  name                = "${replace(var.name_prefix, "-", "")}kv${random_string.key_vault_suffix.result}"
  location            = var.location
  resource_group_name = var.resource_group_name
  tenant_id           = var.tenant_id
  sku_name            = var.sku_name

  # Security settings
  enabled_for_disk_encryption     = true
  enabled_for_deployment          = true
  enabled_for_template_deployment = true
  purge_protection_enabled        = true
  soft_delete_retention_days      = var.soft_delete_retention_days

  # Network access settings
  public_network_access_enabled = var.enable_private_endpoints ? false : true
  
  network_acls {
    default_action = var.enable_private_endpoints ? "Deny" : "Allow"
    bypass         = "AzureServices"
  }

  tags = var.tags
}

# Access policy for the current user/service principal
resource "azurerm_key_vault_access_policy" "main" {
  key_vault_id = azurerm_key_vault.main.id
  tenant_id    = var.tenant_id
  object_id    = var.object_id

  key_permissions = [
    "Get",
    "List",
    "Update",
    "Create",
    "Import",
    "Delete",
    "Recover",
    "Backup",
    "Restore",
    "Decrypt",
    "Encrypt",
    "UnwrapKey",
    "WrapKey",
    "Verify",
    "Sign",
    "Purge"
  ]

  secret_permissions = [
    "Get",
    "List",
    "Set",
    "Delete",
    "Recover",
    "Backup",
    "Restore",
    "Purge"
  ]

  certificate_permissions = [
    "Get",
    "List",
    "Update",
    "Create",
    "Import",
    "Delete",
    "Recover",
    "Backup",
    "Restore",
    "ManageContacts",
    "ManageIssuers",
    "GetIssuers",
    "ListIssuers",
    "SetIssuers",
    "DeleteIssuers",
    "Purge"
  ]
}

# Private endpoint for Key Vault (if enabled)
resource "azurerm_private_endpoint" "key_vault" {
  count               = var.enable_private_endpoints ? 1 : 0
  name                = "${var.name_prefix}-kv-pe"
  location            = var.location
  resource_group_name = var.resource_group_name
  subnet_id           = var.private_endpoint_subnet_id

  private_service_connection {
    name                           = "${var.name_prefix}-kv-psc"
    private_connection_resource_id = azurerm_key_vault.main.id
    subresource_names              = ["vault"]
    is_manual_connection           = false
  }

  tags = var.tags
}

# Private DNS zone for Key Vault private endpoint (if enabled)
resource "azurerm_private_dns_zone" "key_vault" {
  count               = var.enable_private_endpoints ? 1 : 0
  name                = "privatelink.vaultcore.azure.net"
  resource_group_name = var.resource_group_name

  tags = var.tags
}

# Private DNS zone virtual network link (if enabled)
resource "azurerm_private_dns_zone_virtual_network_link" "key_vault" {
  count                 = var.enable_private_endpoints ? 1 : 0
  name                  = "${var.name_prefix}-kv-dns-link"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.key_vault[0].name
  virtual_network_id    = data.azurerm_virtual_network.main[0].id

  tags = var.tags
}

# Data source for virtual network (if private endpoints enabled)
data "azurerm_virtual_network" "main" {
  count               = var.enable_private_endpoints && var.vnet_name != "" ? 1 : 0
  name                = var.vnet_name
  resource_group_name = var.resource_group_name
}

# Data source for private endpoint subnet (if enabled)
data "azurerm_subnet" "private_endpoint" {
  count                = var.enable_private_endpoints && var.private_endpoint_subnet_name != "" ? 1 : 0
  name                 = var.private_endpoint_subnet_name
  virtual_network_name = var.vnet_name
  resource_group_name  = var.resource_group_name
}

# Private DNS A record for Key Vault (if enabled)
resource "azurerm_private_dns_a_record" "key_vault" {
  count               = var.enable_private_endpoints ? 1 : 0
  name                = azurerm_key_vault.main.name
  zone_name           = azurerm_private_dns_zone.key_vault[0].name
  resource_group_name = var.resource_group_name
  ttl                 = 300
  records             = [azurerm_private_endpoint.key_vault[0].private_service_connection[0].private_ip_address]

  tags = var.tags
} 