# Local values for consistent naming and tagging
locals {
  name_prefix = "${var.project_name}-${var.environment}"
  
  common_tags = merge(var.tags, {
    Environment = var.environment
    Location    = var.location
    Timestamp   = timestamp()
  })
  
  resource_group_name = var.resource_group_name != "" ? var.resource_group_name : "${local.name_prefix}-rg"
}

# Data sources for existing resources
data "azurerm_client_config" "current" {}

data "azuread_client_config" "current" {}

# Resource Group Module
module "resource_group" {
  source = "./modules/resource-group"
  
  name     = local.resource_group_name
  location = var.location
  tags     = local.common_tags
}

# Networking Module
module "networking" {
  source = "./modules/networking"
  
  resource_group_name                      = module.resource_group.name
  location                                = var.location
  name_prefix                             = local.name_prefix
  vnet_address_space                      = var.vnet_address_space
  function_subnet_address_prefixes        = var.function_subnet_address_prefixes
  apim_subnet_address_prefixes           = var.apim_subnet_address_prefixes
  private_endpoint_subnet_address_prefixes = var.private_endpoint_subnet_address_prefixes
  tags                                    = local.common_tags
}

# Storage Module
module "storage" {
  source = "./modules/storage"
  
  resource_group_name      = module.resource_group.name
  location                = var.location
  name_prefix             = local.name_prefix
  account_tier            = var.storage_account_tier
  account_replication_type = var.storage_account_replication_type
  enable_backup           = var.enable_backup
  backup_retention_days   = var.backup_retention_days
  enable_geo_redundancy   = var.enable_geo_redundancy
  tags                    = local.common_tags
  
  depends_on = [module.resource_group]
}

# Key Vault Module
module "key_vault" {
  source = "./modules/key-vault"
  
  resource_group_name           = module.resource_group.name
  location                     = var.location
  name_prefix                  = local.name_prefix
  sku_name                     = var.key_vault_sku_name
  soft_delete_retention_days   = var.key_vault_soft_delete_retention_days
  tenant_id                    = data.azurerm_client_config.current.tenant_id
  object_id                    = data.azurerm_client_config.current.object_id
  enable_private_endpoints     = var.enable_private_endpoints
  private_endpoint_subnet_id   = module.networking.private_endpoint_subnet_id
  tags                         = local.common_tags
  
  depends_on = [module.resource_group, module.networking]
}

# Monitoring Module
module "monitoring" {
  source = "./modules/monitoring"
  
  resource_group_name                      = module.resource_group.name
  location                                = var.location
  name_prefix                             = local.name_prefix
  log_analytics_retention_days            = var.log_analytics_retention_days
  application_insights_sampling_percentage = var.application_insights_sampling_percentage
  tags                                    = local.common_tags
  
  depends_on = [module.resource_group]
}

# Azure AD B2C Module
module "ad_b2c" {
  source = "./modules/ad-b2c"
  
  tenant_name           = var.b2c_tenant_name
  domain_name          = var.b2c_domain_name
  name_prefix          = local.name_prefix
  environment          = var.environment
  create_test_users    = var.create_test_users
  key_vault_id         = module.key_vault.id
  tags                 = local.common_tags
  
  depends_on = [module.key_vault]
}

# Function Apps Module
module "function_apps" {
  source = "./modules/function-apps"
  
  resource_group_name                = module.resource_group.name
  location                          = var.location
  name_prefix                       = local.name_prefix
  service_plan_sku                  = var.function_app_plan_sku
  runtime_version                   = var.function_app_runtime_version
  function_timeout                  = var.function_timeout
  memory_size                       = var.function_memory_size
  storage_account_name              = module.storage.storage_account_name
  storage_account_access_key        = module.storage.storage_account_primary_access_key
  application_insights_connection_string = module.monitoring.application_insights_connection_string
  key_vault_id                      = module.key_vault.id
  subnet_id                         = module.networking.function_subnet_id
  b2c_tenant_id                     = module.ad_b2c.tenant_id
  b2c_client_id                     = module.ad_b2c.client_id
  b2c_jwks_url                      = module.ad_b2c.jwks_url
  enable_auto_scaling               = var.enable_auto_scaling
  max_instances                     = var.max_instances
  enable_debug_logging              = var.enable_debug_logging
  tags                              = local.common_tags
  
  depends_on = [
    module.resource_group,
    module.storage,
    module.monitoring,
    module.key_vault,
    module.ad_b2c,
    module.networking
  ]
}

# API Management Module
module "apim" {
  source = "./modules/apim"
  
  resource_group_name           = module.resource_group.name
  location                     = var.location
  name_prefix                  = local.name_prefix
  sku_name                     = var.apim_sku_name
  publisher_name               = var.apim_publisher_name
  publisher_email              = var.apim_publisher_email
  subnet_id                    = module.networking.apim_subnet_id
  oauth_function_url           = module.function_apps.oauth_function_url
  jwt_authorizer_function_url  = module.function_apps.jwt_authorizer_function_url
  basic_auth_function_url      = module.function_apps.basic_auth_function_url
  password_change_function_url = module.function_apps.password_change_function_url
  application_insights_id      = module.monitoring.application_insights_id
  allowed_ip_ranges           = var.allowed_ip_ranges
  tags                        = local.common_tags
  
  depends_on = [
    module.resource_group,
    module.function_apps,
    module.monitoring,
    module.networking
  ]
} 