variable "tenant_name" {
  description = "Name of the Azure AD B2C tenant"
  type        = string
}

variable "domain_name" {
  description = "Domain name for the Azure AD B2C tenant"
  type        = string
}

variable "name_prefix" {
  description = "Prefix for naming resources"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "create_test_users" {
  description = "Whether to create test users for development"
  type        = bool
  default     = false
}

variable "key_vault_id" {
  description = "ID of the Key Vault to store B2C secrets"
  type        = string
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
} 