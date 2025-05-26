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
    resource_group_name  = "authserver-terraform-state-rg"
    storage_account_name = "authserverterraformstate"
    container_name       = "terraform-state"
    key                  = "authserver.tfstate"
  }
}

provider "azurerm" {
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
  # Configuration will be provided via environment variables
}

provider "random" {
  # No configuration needed
} 