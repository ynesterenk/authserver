# Development Environment Configuration
project_name = "authserver"
environment  = "dev"

# Azure regions
location           = "West Europe"
secondary_location = "North Europe"

# Resource naming
resource_group_name = "authserver-dev-rg"

# Function App configuration
function_app_plan_sku      = "Y1"  # Consumption plan for cost optimization
function_app_runtime_version = "11"
function_timeout           = 30
function_memory_size       = 512

# API Management configuration
apim_sku_name       = "Developer_1"
apim_publisher_name = "LSEG Development"
apim_publisher_email = "dev-team@lseg.com"

# Azure AD B2C configuration
b2c_tenant_name = "authserverb2c-dev"
b2c_domain_name = "authserverb2c-dev.onmicrosoft.com"

# Storage configuration
storage_account_tier             = "Standard"
storage_account_replication_type = "LRS"

# Key Vault configuration
key_vault_sku_name                     = "standard"
key_vault_soft_delete_retention_days   = 7

# Monitoring configuration
log_analytics_retention_days            = 30
application_insights_sampling_percentage = 100

# Networking configuration
vnet_address_space                      = ["10.0.0.0/16"]
function_subnet_address_prefixes        = ["10.0.1.0/24"]
apim_subnet_address_prefixes           = ["10.0.2.0/24"]
private_endpoint_subnet_address_prefixes = ["10.0.3.0/24"]

# Security configuration
allowed_ip_ranges        = ["0.0.0.0/0"]  # Open for development
enable_private_endpoints = false

# Backup and DR configuration
enable_backup           = true
backup_retention_days   = 30
enable_geo_redundancy   = false

# Cost optimization
enable_auto_scaling = false  # Disabled for dev to save costs
max_instances      = 3

# Development/Testing configuration
create_test_users    = true
enable_debug_logging = true

# Tags
tags = {
  Project     = "AuthServer Migration"
  Environment = "dev"
  ManagedBy   = "Terraform"
  Owner       = "LSEG"
  CostCenter  = "Development"
  Purpose     = "AWS to Azure Migration - Development"
} 