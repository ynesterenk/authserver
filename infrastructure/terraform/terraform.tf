terraform {
  required_version = ">= 1.0"
  
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 2.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  backend "azurerm" {
    resource_group_name  = "authserver-tfstate-personal-rg"
    storage_account_name = "authservertfsyevgen"
    container_name       = "tfstate"
    key                  = "authserver.tfstate"
  }
}

provider "azurerm" {
  subscription_id = "1757ec62-3908-48fd-a5c9-2d320fb26e4f"
  tenant_id       = "b580e134-0c17-4a0e-9fa1-4aff04ad87f6"
  features {
    resource_group {
      prevent_deletion_if_contains_resources = false
    }
    
    key_vault {
      purge_soft_delete_on_destroy    = true
      recover_soft_deleted_key_vaults = true
    }
    
    application_insights {
      disable_generated_rule = false
    }
  }
}

provider "azuread" {
  tenant_id = "b580e134-0c17-4a0e-9fa1-4aff04ad87f6"
  # Configuration will be provided via environment variables
}

provider "random" {
  # No configuration needed
} 