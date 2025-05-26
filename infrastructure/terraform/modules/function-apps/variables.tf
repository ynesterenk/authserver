variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
}

variable "location" {
  description = "Azure region for resources"
  type        = string
}

variable "name_prefix" {
  description = "Prefix for resource names"
  type        = string
}

variable "service_plan_sku" {
  description = "SKU for the App Service Plan"
  type        = string
  default     = "Y1"
}

variable "runtime_version" {
  description = "Java runtime version"
  type        = string
  default     = "11"
}

variable "function_timeout" {
  description = "Function timeout in seconds"
  type        = number
  default     = 30
}

variable "memory_size" {
  description = "Function memory size in MB"
  type        = number
  default     = 512
}

variable "storage_account_name" {
  description = "Name of the storage account"
  type        = string
}

variable "storage_account_access_key" {
  description = "Access key for the storage account"
  type        = string
  sensitive   = true
}

variable "application_insights_connection_string" {
  description = "Application Insights connection string"
  type        = string
  sensitive   = true
}

variable "key_vault_id" {
  description = "ID of the Key Vault"
  type        = string
}

variable "key_vault_name" {
  description = "Name of the Key Vault"
  type        = string
}

variable "subnet_id" {
  description = "ID of the subnet for VNet integration"
  type        = string
  default     = null
}

variable "b2c_tenant_id" {
  description = "Azure AD B2C tenant ID"
  type        = string
}

variable "b2c_client_id" {
  description = "Azure AD B2C client ID"
  type        = string
}

variable "b2c_jwks_url" {
  description = "Azure AD B2C JWKS URL"
  type        = string
}

variable "b2c_domain_name" {
  description = "Azure AD B2C domain name"
  type        = string
}

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

variable "enable_debug_logging" {
  description = "Enable debug logging"
  type        = bool
  default     = false
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "allowed_ip_ranges" {
  description = "IP ranges allowed to access Function Apps"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
} 