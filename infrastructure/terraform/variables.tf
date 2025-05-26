variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "authserver"
  
  validation {
    condition     = can(regex("^[a-z0-9-]+$", var.project_name))
    error_message = "Project name must contain only lowercase letters, numbers, and hyphens."
  }
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

variable "location" {
  description = "Azure region for primary resources"
  type        = string
  default     = "West Europe"
}

variable "secondary_location" {
  description = "Azure region for secondary resources (disaster recovery)"
  type        = string
  default     = "North Europe"
}

variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
  default     = ""
}

variable "tags" {
  description = "Tags to apply to all resources"
  type        = map(string)
  default = {
    Project     = "AuthServer Migration"
    Environment = ""
    ManagedBy   = "Terraform"
    Owner       = "LSEG"
  }
}

# Function App Configuration
variable "function_app_plan_sku" {
  description = "SKU for the Function App Service Plan"
  type        = string
  default     = "Y1" # Consumption plan
  
  validation {
    condition = contains([
      "Y1",    # Consumption
      "EP1",   # Premium v2
      "EP2",   # Premium v2
      "EP3"    # Premium v2
    ], var.function_app_plan_sku)
    error_message = "Function App plan SKU must be one of: Y1, EP1, EP2, EP3."
  }
}

variable "function_app_runtime_version" {
  description = "Java runtime version for Function Apps"
  type        = string
  default     = "11"
}

variable "function_timeout" {
  description = "Function timeout in seconds"
  type        = number
  default     = 30
}

variable "function_memory_size" {
  description = "Function memory size in MB"
  type        = number
  default     = 512
}

# API Management Configuration
variable "apim_sku_name" {
  description = "SKU for API Management"
  type        = string
  default     = "Developer_1"
  
  validation {
    condition = contains([
      "Developer_1",
      "Basic_1",
      "Basic_2", 
      "Standard_1",
      "Standard_2",
      "Premium_1",
      "Premium_2"
    ], var.apim_sku_name)
    error_message = "APIM SKU must be a valid Azure API Management SKU."
  }
}

variable "apim_publisher_name" {
  description = "Publisher name for API Management"
  type        = string
  default     = "LSEG"
}

variable "apim_publisher_email" {
  description = "Publisher email for API Management"
  type        = string
  default     = "admin@lseg.com"
}

# Azure AD B2C Configuration
variable "b2c_tenant_name" {
  description = "Azure AD B2C tenant name"
  type        = string
  default     = "authserverb2c"
}

variable "b2c_domain_name" {
  description = "Azure AD B2C domain name"
  type        = string
  default     = "authserverb2c.onmicrosoft.com"
}

# Storage Configuration
variable "storage_account_tier" {
  description = "Storage account tier"
  type        = string
  default     = "Standard"
}

variable "storage_account_replication_type" {
  description = "Storage account replication type"
  type        = string
  default     = "LRS"
}

# Key Vault Configuration
variable "key_vault_sku_name" {
  description = "SKU name for Key Vault"
  type        = string
  default     = "standard"
}

variable "key_vault_soft_delete_retention_days" {
  description = "Soft delete retention days for Key Vault"
  type        = number
  default     = 7
}

# Monitoring Configuration
variable "log_analytics_retention_days" {
  description = "Log Analytics workspace retention in days"
  type        = number
  default     = 30
}

variable "application_insights_sampling_percentage" {
  description = "Application Insights sampling percentage"
  type        = number
  default     = 100
}

# Networking Configuration
variable "vnet_address_space" {
  description = "Address space for the virtual network"
  type        = list(string)
  default     = ["10.0.0.0/16"]
}

variable "function_subnet_address_prefixes" {
  description = "Address prefixes for Function Apps subnet"
  type        = list(string)
  default     = ["10.0.1.0/24"]
}

variable "apim_subnet_address_prefixes" {
  description = "Address prefixes for API Management subnet"
  type        = list(string)
  default     = ["10.0.2.0/24"]
}

variable "private_endpoint_subnet_address_prefixes" {
  description = "Address prefixes for private endpoints subnet"
  type        = list(string)
  default     = ["10.0.3.0/24"]
}

# Security Configuration
variable "allowed_ip_ranges" {
  description = "IP ranges allowed to access resources"
  type        = list(string)
  default     = ["0.0.0.0/0"] # Should be restricted in production
}

variable "enable_private_endpoints" {
  description = "Enable private endpoints for secure connectivity"
  type        = bool
  default     = false
}

# Backup and DR Configuration
variable "enable_backup" {
  description = "Enable backup for storage accounts"
  type        = bool
  default     = true
}

variable "backup_retention_days" {
  description = "Backup retention in days"
  type        = number
  default     = 30
}

variable "enable_geo_redundancy" {
  description = "Enable geo-redundant storage"
  type        = bool
  default     = false
}

# Cost Optimization
variable "enable_auto_scaling" {
  description = "Enable auto-scaling for Function Apps"
  type        = bool
  default     = true
}

variable "max_instances" {
  description = "Maximum number of instances for auto-scaling"
  type        = number
  default     = 10
}

# Development/Testing Configuration
variable "create_test_users" {
  description = "Create test users in B2C (for non-prod environments)"
  type        = bool
  default     = false
}

variable "enable_debug_logging" {
  description = "Enable debug logging"
  type        = bool
  default     = false
} 