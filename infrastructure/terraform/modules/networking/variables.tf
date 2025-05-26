variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
}

variable "location" {
  description = "Azure region for the networking resources"
  type        = string
}

variable "name_prefix" {
  description = "Prefix for naming resources"
  type        = string
}

variable "vnet_address_space" {
  description = "Address space for the virtual network"
  type        = list(string)
  default     = ["10.0.0.0/16"]
}

variable "function_subnet_address_prefixes" {
  description = "Address prefixes for the function apps subnet"
  type        = list(string)
  default     = ["10.0.1.0/24"]
}

variable "apim_subnet_address_prefixes" {
  description = "Address prefixes for the API Management subnet"
  type        = list(string)
  default     = ["10.0.2.0/24"]
}

variable "private_endpoint_subnet_address_prefixes" {
  description = "Address prefixes for the private endpoints subnet"
  type        = list(string)
  default     = ["10.0.3.0/24"]
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
} 