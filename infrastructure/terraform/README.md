# AuthServer Infrastructure - AWS to Azure Migration

This directory contains the complete Terraform infrastructure for migrating the AuthServer from AWS to Azure. The infrastructure converts AWS CloudFormation templates to Terraform with Azure resources while maintaining 100% API compatibility.

## 🏗️ Architecture Overview

### AWS to Azure Resource Mapping

| AWS Service | Azure Service | Purpose |
|-------------|---------------|---------|
| AWS Lambda | Azure Functions | Serverless compute for OAuth, JWT, and Basic Auth |
| AWS API Gateway | Azure API Management | API gateway with routing and policies |
| AWS Cognito | Azure AD B2C | Identity and authentication service |
| AWS S3 | Azure Storage Account | Object storage for artifacts and logs |
| AWS CloudWatch | Azure Monitor + Application Insights | Monitoring and logging |
| AWS IAM | Azure RBAC + Managed Identity | Identity and access management |
| AWS VPC | Azure Virtual Network | Network isolation and security |

### Infrastructure Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Azure Resource Group                     │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │   Azure APIM    │  │ Azure Functions │  │ Azure AD B2C│ │
│  │                 │  │                 │  │             │ │
│  │ • OAuth API     │  │ • OAuth Server  │  │ • Tenant    │ │
│  │ • Auth API      │  │ • JWT Authorizer│  │ • Apps      │ │
│  │ • Password API  │  │ • Basic Auth    │  │ • Policies  │ │
│  │ • Health API    │  │ • Password Mgmt │  │ • JWKS      │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
│                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │  Azure Storage  │  │   Key Vault     │  │ Monitoring  │ │
│  │                 │  │                 │  │             │ │
│  │ • Packages      │  │ • Secrets       │  │ • Log Analytics│
│  │ • Logs          │  │ • Certificates  │  │ • App Insights │
│  │ • Backups       │  │ • Keys          │  │ • Dashboards   │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │                Virtual Network                          │ │
│  │ ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐ │ │
│  │ │ Functions   │ │    APIM     │ │ Private Endpoints   │ │ │
│  │ │   Subnet    │ │   Subnet    │ │      Subnet         │ │ │
│  │ └─────────────┘ └─────────────┘ └─────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 📁 Directory Structure

```
infrastructure/terraform/
├── main.tf                     # Root module orchestration
├── variables.tf                # Input variables
├── outputs.tf                  # Output values
├── terraform.tf                # Provider and backend configuration
├── README.md                   # This documentation
├── modules/                    # Reusable Terraform modules
│   ├── resource-group/         # Azure Resource Group
│   ├── function-apps/          # Azure Functions (AWS Lambda equivalent)
│   ├── apim/                   # API Management (AWS API Gateway equivalent)
│   ├── ad-b2c/                 # Azure AD B2C (AWS Cognito equivalent)
│   ├── key-vault/              # Key Vault for secrets
│   ├── storage/                # Storage Account (AWS S3 equivalent)
│   ├── monitoring/             # Log Analytics & App Insights
│   └── networking/             # Virtual Network and subnets
├── environments/               # Environment-specific configurations
│   ├── dev/
│   │   └── terraform.tfvars    # Development environment variables
│   ├── staging/
│   │   └── terraform.tfvars    # Staging environment variables
│   └── prod/
│       └── terraform.tfvars    # Production environment variables
└── azure-pipelines.yml         # CI/CD pipeline configuration
```

## 🚀 Quick Start

### Prerequisites

1. **Azure CLI** installed and authenticated
2. **Terraform** >= 1.5.0 installed
3. **Azure subscription** with appropriate permissions
4. **Azure DevOps** project for CI/CD (optional)

### Initial Setup

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd authserver/infrastructure/terraform
   ```

2. **Create Terraform backend storage**:
   ```bash
   # Create resource group for Terraform state
   az group create --name authserver-terraform-state-rg --location "West Europe"
   
   # Create storage account for Terraform state
   az storage account create \
     --name authserverterraformstate \
     --resource-group authserver-terraform-state-rg \
     --location "West Europe" \
     --sku Standard_LRS
   
   # Create container for state files
   az storage container create \
     --name terraform-state \
     --account-name authserverterraformstate
   ```

3. **Initialize Terraform**:
   ```bash
   terraform init
   ```

### Environment Deployment

#### Development Environment

```bash
# Plan deployment
terraform plan -var-file="environments/dev/terraform.tfvars" -out=tfplan-dev

# Apply deployment
terraform apply tfplan-dev
```

#### Production Environment

```bash
# Plan deployment
terraform plan -var-file="environments/prod/terraform.tfvars" -out=tfplan-prod

# Apply deployment (with approval)
terraform apply tfplan-prod
```

## 🔧 Configuration

### Environment Variables

Each environment has its own `terraform.tfvars` file with specific configurations:

#### Development (`environments/dev/terraform.tfvars`)
- **Cost-optimized**: Consumption plans, basic SKUs
- **Open security**: Allows all IP ranges for development
- **Debug enabled**: Full logging and debugging
- **Test users**: Creates test users in B2C

#### Production (`environments/prod/terraform.tfvars`)
- **Performance-optimized**: Premium plans, standard SKUs
- **Restricted security**: Limited IP ranges, private endpoints
- **Minimal logging**: Production-level logging only
- **No test data**: Clean production environment

### Key Configuration Options

| Variable | Description | Dev Default | Prod Default |
|----------|-------------|-------------|--------------|
| `function_app_plan_sku` | Function App plan | `Y1` (Consumption) | `EP1` (Premium) |
| `apim_sku_name` | API Management SKU | `Developer_1` | `Standard_1` |
| `enable_private_endpoints` | Private endpoint security | `false` | `true` |
| `enable_auto_scaling` | Auto-scaling | `false` | `true` |
| `backup_retention_days` | Backup retention | `30` | `365` |

## 🔐 Security Features

### Identity and Access Management
- **Managed Identity**: All services use system-assigned managed identities
- **RBAC**: Role-based access control with least privilege
- **Key Vault**: Centralized secret management
- **Private Endpoints**: Secure network connectivity (production)

### Network Security
- **Virtual Network**: Isolated network environment
- **Subnets**: Segmented network for different services
- **Network Security Groups**: Traffic filtering and access control
- **Private DNS**: Internal name resolution

### Data Protection
- **Encryption at Rest**: All storage encrypted
- **Encryption in Transit**: HTTPS/TLS 1.2+ enforced
- **Backup**: Automated backup with retention policies
- **Geo-redundancy**: Cross-region replication (production)

## 📊 Monitoring and Observability

### Azure Monitor Integration
- **Log Analytics**: Centralized logging workspace
- **Application Insights**: Application performance monitoring
- **Custom Metrics**: Business-specific metrics tracking
- **Dashboards**: Real-time monitoring dashboards

### Alerting
- **Performance Alerts**: Response time and throughput monitoring
- **Error Rate Alerts**: Automatic error detection and notification
- **Security Alerts**: Suspicious activity monitoring
- **Resource Alerts**: Resource utilization monitoring

## 🔄 CI/CD Pipeline

### Azure DevOps Pipeline Features
- **Multi-stage deployment**: Dev → Staging → Production
- **Security scanning**: Checkov security analysis
- **Manual approvals**: Production deployment gates
- **Rollback capability**: Automated rollback procedures
- **Post-deployment validation**: Health checks and testing

### Pipeline Stages
1. **Validation**: Format check, validation, security scan
2. **Development**: Automatic deployment to dev environment
3. **Staging**: Automatic deployment to staging environment
4. **Production**: Manual approval + deployment to production
5. **Destroy**: Manual infrastructure destruction (if needed)

## 🏗️ Module Documentation

### Resource Group Module
- **Purpose**: Creates and manages Azure resource groups
- **Resources**: `azurerm_resource_group`
- **Features**: Lifecycle protection, consistent tagging

### Function Apps Module
- **Purpose**: Converts AWS Lambda to Azure Functions
- **Resources**: Service Plan, Function Apps (4), Auto-scaling
- **Features**: Java 11 runtime, VNet integration, managed identity

### API Management Module
- **Purpose**: Converts AWS API Gateway to Azure APIM
- **Resources**: APIM instance, APIs, operations, policies
- **Features**: OpenAPI definitions, backend routing, CORS support

### Azure AD B2C Module
- **Purpose**: Converts AWS Cognito to Azure AD B2C
- **Resources**: B2C tenant, applications, policies
- **Features**: OAuth 2.0, JWT tokens, JWKS endpoint

### Key Vault Module
- **Purpose**: Centralized secret management
- **Resources**: Key Vault, access policies, secrets
- **Features**: Managed identity access, private endpoints

### Storage Module
- **Purpose**: Converts AWS S3 to Azure Storage
- **Resources**: Storage account, containers, policies
- **Features**: Lifecycle management, geo-redundancy, backup

### Monitoring Module
- **Purpose**: Converts AWS CloudWatch to Azure Monitor
- **Resources**: Log Analytics, Application Insights
- **Features**: Custom metrics, dashboards, alerting

### Networking Module
- **Purpose**: Network isolation and security
- **Resources**: VNet, subnets, NSGs, private endpoints
- **Features**: Network segmentation, security rules

## 🔍 Troubleshooting

### Common Issues

#### Terraform State Lock
```bash
# If state is locked, force unlock (use with caution)
terraform force-unlock <lock-id>
```

#### Resource Naming Conflicts
```bash
# Check existing resources
az resource list --resource-group <resource-group-name>

# Update random suffix if needed
terraform taint random_string.storage_suffix
```

#### Permission Issues
```bash
# Check current Azure context
az account show

# Verify permissions
az role assignment list --assignee $(az account show --query user.name -o tsv)
```

### Validation Commands

```bash
# Validate Terraform configuration
terraform validate

# Check formatting
terraform fmt -check -recursive

# Security scan
checkov -d . --framework terraform

# Plan without applying
terraform plan -var-file="environments/dev/terraform.tfvars"
```

## 📈 Cost Optimization

### Development Environment
- **Consumption Plans**: Pay-per-execution for Functions
- **Developer SKUs**: Lower-cost API Management tier
- **LRS Storage**: Locally redundant storage
- **Minimal Retention**: Shorter backup and log retention

### Production Environment
- **Premium Plans**: Better performance with predictable costs
- **Standard SKUs**: Production-grade API Management
- **GRS Storage**: Geo-redundant storage for reliability
- **Extended Retention**: Longer backup and compliance retention

### Cost Monitoring
- **Resource Tags**: Consistent tagging for cost allocation
- **Budget Alerts**: Automated cost monitoring
- **Right-sizing**: Regular review of resource utilization

## 🔄 Migration Compatibility

### AWS API Gateway Compatibility
- **Exact Response Format**: Maintains AWS response structure
- **Status Codes**: Identical HTTP status code behavior
- **Headers**: Preserves CORS and content headers
- **Error Messages**: Compatible error response format

### Zero Client Impact
- **No Code Changes**: Existing clients work without modification
- **Same Endpoints**: URL structure preserved
- **Authentication**: OAuth 2.0 and Basic Auth maintained
- **Performance**: Sub-200ms response times maintained

## 📚 Additional Resources

- [Azure Functions Documentation](https://docs.microsoft.com/en-us/azure/azure-functions/)
- [Azure API Management Documentation](https://docs.microsoft.com/en-us/azure/api-management/)
- [Azure AD B2C Documentation](https://docs.microsoft.com/en-us/azure/active-directory-b2c/)
- [Terraform Azure Provider](https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs)

## 🆘 Support

For issues and questions:
- **Internal**: Contact the DevOps team
- **Documentation**: Check the migration guides in `/migration-documents/`
- **Monitoring**: Use Azure Monitor dashboards for system health
- **Logs**: Check Application Insights for detailed logging

---

**Migration Status**: Phase 4 Complete - Infrastructure as Code Ready for Deployment 🚀 