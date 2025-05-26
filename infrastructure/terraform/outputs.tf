# Resource Group Outputs
output "resource_group_name" {
  description = "Name of the resource group"
  value       = module.resource_group.name
}

output "resource_group_id" {
  description = "ID of the resource group"
  value       = module.resource_group.id
}

# Networking Outputs
output "virtual_network_id" {
  description = "ID of the virtual network"
  value       = module.networking.virtual_network_id
}

output "function_subnet_id" {
  description = "ID of the Function Apps subnet"
  value       = module.networking.function_subnet_id
}

output "apim_subnet_id" {
  description = "ID of the API Management subnet"
  value       = module.networking.apim_subnet_id
}

# Storage Outputs
output "storage_account_name" {
  description = "Name of the storage account"
  value       = module.storage.storage_account_name
}

output "storage_account_id" {
  description = "ID of the storage account"
  value       = module.storage.storage_account_id
}

# Key Vault Outputs
output "key_vault_name" {
  description = "Name of the Key Vault"
  value       = module.key_vault.name
}

output "key_vault_id" {
  description = "ID of the Key Vault"
  value       = module.key_vault.id
}

output "key_vault_uri" {
  description = "URI of the Key Vault"
  value       = module.key_vault.vault_uri
}

# Monitoring Outputs
output "log_analytics_workspace_id" {
  description = "ID of the Log Analytics workspace"
  value       = module.monitoring.log_analytics_workspace_id
}

output "application_insights_id" {
  description = "ID of the Application Insights instance"
  value       = module.monitoring.application_insights_id
}

output "application_insights_connection_string" {
  description = "Connection string for Application Insights"
  value       = module.monitoring.application_insights_connection_string
  sensitive   = true
}

output "application_insights_instrumentation_key" {
  description = "Instrumentation key for Application Insights"
  value       = module.monitoring.application_insights_instrumentation_key
  sensitive   = true
}

# Azure AD B2C Outputs
output "b2c_tenant_id" {
  description = "Azure AD B2C tenant ID"
  value       = module.ad_b2c.tenant_id
}

output "b2c_client_id" {
  description = "Azure AD B2C client ID"
  value       = module.ad_b2c.client_id
}

output "b2c_domain_name" {
  description = "Azure AD B2C domain name"
  value       = module.ad_b2c.domain_name
}

output "b2c_jwks_url" {
  description = "Azure AD B2C JWKS URL"
  value       = module.ad_b2c.jwks_url
}

# Function Apps Outputs
output "oauth_function_name" {
  description = "Name of the OAuth Function App"
  value       = module.function_apps.oauth_function_name
}

output "oauth_function_url" {
  description = "URL of the OAuth Function App"
  value       = module.function_apps.oauth_function_url
}

output "jwt_authorizer_function_name" {
  description = "Name of the JWT Authorizer Function App"
  value       = module.function_apps.jwt_authorizer_function_name
}

output "jwt_authorizer_function_url" {
  description = "URL of the JWT Authorizer Function App"
  value       = module.function_apps.jwt_authorizer_function_url
}

output "basic_auth_function_name" {
  description = "Name of the Basic Auth Function App"
  value       = module.function_apps.basic_auth_function_name
}

output "basic_auth_function_url" {
  description = "URL of the Basic Auth Function App"
  value       = module.function_apps.basic_auth_function_url
}

output "password_change_function_name" {
  description = "Name of the Password Change Function App"
  value       = module.function_apps.password_change_function_name
}

output "password_change_function_url" {
  description = "URL of the Password Change Function App"
  value       = module.function_apps.password_change_function_url
}

# API Management Outputs
output "apim_name" {
  description = "Name of the API Management instance"
  value       = module.apim.name
}

output "apim_gateway_url" {
  description = "Gateway URL of the API Management instance"
  value       = module.apim.gateway_url
}

output "apim_management_api_url" {
  description = "Management API URL of the API Management instance"
  value       = module.apim.management_api_url
}

output "apim_developer_portal_url" {
  description = "Developer portal URL of the API Management instance"
  value       = module.apim.developer_portal_url
}

# Service Endpoint (equivalent to AWS API Gateway endpoint)
output "service_endpoint" {
  description = "Main service endpoint URL (equivalent to AWS API Gateway)"
  value       = module.apim.gateway_url
}

# Migration Compatibility Outputs
output "aws_compatibility_endpoints" {
  description = "AWS-compatible endpoint mappings"
  value = {
    oauth_token        = "${module.apim.gateway_url}/oauth/token"
    authorize_jwt      = "${module.apim.gateway_url}/authorize/jwt"
    authorize_basic    = "${module.apim.gateway_url}/authorize/basic"
    change_password    = "${module.apim.gateway_url}/change-password"
    health_check       = "${module.apim.gateway_url}/health"
  }
}

# Environment Information
output "environment_info" {
  description = "Environment information"
  value = {
    environment     = var.environment
    location        = var.location
    project_name    = var.project_name
    deployment_time = timestamp()
  }
}

# Security Information
output "security_info" {
  description = "Security-related information"
  value = {
    key_vault_name           = module.key_vault.name
    managed_identity_enabled = true
    private_endpoints_enabled = var.enable_private_endpoints
    b2c_tenant_configured    = true
  }
}

# Cost Optimization Information
output "cost_optimization_info" {
  description = "Cost optimization settings"
  value = {
    function_plan_sku     = var.function_app_plan_sku
    apim_sku             = var.apim_sku_name
    auto_scaling_enabled = var.enable_auto_scaling
    max_instances        = var.max_instances
  }
} 