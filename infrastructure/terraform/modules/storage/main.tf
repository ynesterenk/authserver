# Random string for unique storage account naming
resource "random_string" "storage_suffix" {
  length  = 8
  special = false
  upper   = false
}

# Storage Account (equivalent to AWS S3)
resource "azurerm_storage_account" "main" {
  name                     = "${replace(var.name_prefix, "-", "")}st${random_string.storage_suffix.result}"
  resource_group_name      = var.resource_group_name
  location                = var.location
  account_tier            = var.account_tier
  account_replication_type = var.account_replication_type
  account_kind            = "StorageV2"
  access_tier             = "Hot"
  
  # Security settings
  https_traffic_only_enabled     = true
  min_tls_version               = "TLS1_2"
  allow_nested_items_to_be_public = false
  
  # Advanced threat protection
  blob_properties {
    versioning_enabled       = true
    change_feed_enabled     = true
    change_feed_retention_in_days = 7
    last_access_time_enabled = true
    
    delete_retention_policy {
      days = 7
    }
    
    container_delete_retention_policy {
      days = 7
    }
  }
  
  # Network rules
  network_rules {
    default_action             = "Allow"
    bypass                    = ["AzureServices"]
    ip_rules                  = var.allowed_ip_ranges
  }
  
  tags = var.tags
}

# Container for Function App packages (equivalent to S3 bucket for Lambda deployment packages)
resource "azurerm_storage_container" "function_packages" {
  name                  = "function-packages"
  storage_account_name  = azurerm_storage_account.main.name
  container_access_type = "private"
}

# Container for application logs
resource "azurerm_storage_container" "logs" {
  name                  = "logs"
  storage_account_name  = azurerm_storage_account.main.name
  container_access_type = "private"
}

# Container for backups
resource "azurerm_storage_container" "backups" {
  name                  = "backups"
  storage_account_name  = azurerm_storage_account.main.name
  container_access_type = "private"
}

# Backup configuration
resource "azurerm_storage_management_policy" "backup_policy" {
  count              = var.enable_backup ? 1 : 0
  storage_account_id = azurerm_storage_account.main.id

  rule {
    name    = "backup-retention"
    enabled = true
    
    filters {
      prefix_match = ["backups/"]
      blob_types   = ["blockBlob"]
    }
    
    actions {
      base_blob {
        tier_to_cool_after_days_since_modification_greater_than    = 30
        tier_to_archive_after_days_since_modification_greater_than = 90
        delete_after_days_since_modification_greater_than         = var.backup_retention_days
      }
      
      snapshot {
        delete_after_days_since_creation_greater_than = 30
      }
      
      version {
        delete_after_days_since_creation = 30
      }
    }
  }
}

# Geo-redundant storage configuration
resource "azurerm_storage_account" "geo_redundant" {
  count                    = var.enable_geo_redundancy ? 1 : 0
  name                     = "${replace(var.name_prefix, "-", "")}geo${random_string.storage_suffix.result}"
  resource_group_name      = var.resource_group_name
  location                = var.secondary_location
  account_tier            = var.account_tier
  account_replication_type = "GRS"
  account_kind            = "StorageV2"
  access_tier             = "Hot"
  
  https_traffic_only_enabled     = true
  min_tls_version               = "TLS1_2"
  allow_nested_items_to_be_public = false
  
  blob_properties {
    versioning_enabled       = true
    change_feed_enabled     = true
    change_feed_retention_in_days = 7
    
    delete_retention_policy {
      days = 7
    }
    
    container_delete_retention_policy {
      days = 7
    }
  }
  
  tags = var.tags
}

# Storage account diagnostic settings
resource "azurerm_monitor_diagnostic_setting" "storage_diagnostics" {
  name               = "${var.name_prefix}-storage-diagnostics"
  target_resource_id = azurerm_storage_account.main.id
  log_analytics_workspace_id = var.log_analytics_workspace_id

  metric {
    category = "Transaction"
    enabled  = true
  }
  
  metric {
    category = "Capacity"
    enabled  = true
  }
} 