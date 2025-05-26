# Production Environment Configuration
project_name = "authserver"
environment  = "prod"

# Azure regions
location           = "West Europe"
secondary_location = "North Europe"

# Resource naming
resource_group_name = "authserver-prod-rg"

# Function App configuration
function_app_plan_sku      = "EP1"  # Premium plan for production performance
function_app_runtime_version = "11"
function_timeout           = 30
function_memory_size       = 512

# API Management configuration
apim_sku_name       = "Standard_1"
apim_publisher_name = "LSEG"
apim_publisher_email = "admin@lseg.com"

# Azure AD B2C configuration
b2c_tenant_name = "authserverb2c"
b2c_domain_name = "authserverb2c.onmicrosoft.com"

# Storage configuration
storage_account_tier             = "Standard"
storage_account_replication_type = "GRS"  # Geo-redundant for production

# Key Vault configuration
key_vault_sku_name                     = "premium"  # Premium for HSM support
key_vault_soft_delete_retention_days   = 90

# Monitoring configuration
log_analytics_retention_days            = 90
application_insights_sampling_percentage = 100

# Networking configuration
vnet_address_space                      = ["10.1.0.0/16"]
function_subnet_address_prefixes        = ["10.1.1.0/24"]
apim_subnet_address_prefixes           = ["10.1.2.0/24"]
private_endpoint_subnet_address_prefixes = ["10.1.3.0/24"]

# Security configuration
allowed_ip_ranges = [
  "203.0.113.0/24",    # LSEG office network
  "198.51.100.0/24",   # Partner network
  "192.0.2.0/24"       # Additional authorized network
]
enable_private_endpoints = true

# Backup and DR configuration
enable_backup           = true
backup_retention_days   = 365  # 1 year retention for production
enable_geo_redundancy   = true

# Performance and scaling
enable_auto_scaling = true
max_instances      = 20

# Production configuration
create_test_users    = false
enable_debug_logging = false

# Tags
tags = {
  Project     = "AuthServer Migration"
  Environment = "prod"
  ManagedBy   = "Terraform"
  Owner       = "LSEG"
  CostCenter  = "Production"
  Purpose     = "AWS to Azure Migration - Production"
  Compliance  = "SOX"
  DataClass   = "Confidential"
} 