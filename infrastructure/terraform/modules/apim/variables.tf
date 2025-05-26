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

variable "sku_name" {
  description = "SKU for API Management"
  type        = string
  default     = "Developer_1"
}

variable "publisher_name" {
  description = "Publisher name for API Management"
  type        = string
}

variable "publisher_email" {
  description = "Publisher email for API Management"
  type        = string
}

variable "subnet_id" {
  description = "ID of the subnet for VNet integration"
  type        = string
  default     = null
}

variable "oauth_function_url" {
  description = "URL of the OAuth Function App"
  type        = string
}

variable "jwt_authorizer_function_url" {
  description = "URL of the JWT Authorizer Function App"
  type        = string
}

variable "basic_auth_function_url" {
  description = "URL of the Basic Auth Function App"
  type        = string
}

variable "password_change_function_url" {
  description = "URL of the Password Change Function App"
  type        = string
}

variable "application_insights_id" {
  description = "ID of the Application Insights instance"
  type        = string
}

variable "application_insights_instrumentation_key" {
  description = "Instrumentation key for Application Insights"
  type        = string
  sensitive   = true
}

variable "allowed_ip_ranges" {
  description = "IP ranges allowed to access API Management"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
} 