# Post-Migration Cleanup Phase - AWS Decommissioning & Finalization

## Context
You are completing the final phase of the authserver migration by safely decommissioning AWS resources and finalizing the Azure setup. This is Phase 8 focusing on cleanup, optimization, and knowledge transfer.

## Post-Migration Requirements (from PRD)
- **Second Region Fail-over**: Implement North Europe secondary region
- **Pen-test**: Security penetration testing of Azure environment
- **AWS Decommissioning**: Safe removal of CloudFormation stacks
- **Documentation**: Complete operational runbooks and knowledge transfer
- **Cost Optimization**: Final cost analysis and optimization

## Current State
- **Azure Production**: 100% traffic on Azure authserver
- **AWS Environment**: Idle AWS resources ready for decommissioning
- **Monitoring**: Azure Monitor fully operational
- **Performance**: Meeting all SLA requirements

## Your Tasks

### 1. Multi-Region Failover Implementation
Set up active-passive configuration across Azure regions:

#### Secondary Region Infrastructure
```hcl
# Terraform configuration for secondary region (North Europe)
module "secondary_region" {
  source = "./modules"
  
  # Region-specific variables
  location                = "northeurope"
  resource_group_name     = "authserver-secondary-rg"
  environment            = "production"
  is_secondary_region    = true
  
  # Replication settings
  primary_region_rg      = azurerm_resource_group.primary.name
  primary_storage_account = azurerm_storage_account.primary.name
  
  # Reduced capacity for passive region
  function_app_plan_sku  = "Y1"  # Consumption plan
  apim_sku              = "Developer_1"
  
  providers = {
    azurerm = azurerm.northeurope
  }
}

# Traffic Manager for failover
resource "azurerm_traffic_manager_profile" "main" {
  name                = "authserver-tm"
  resource_group_name = azurerm_resource_group.primary.name
  
  traffic_routing_method = "Priority"
  
  dns_config {
    relative_name = "authserver"
    ttl          = 30
  }
  
  monitor_config {
    protocol                     = "HTTPS"
    port                        = 443
    path                        = "/health"
    interval_in_seconds         = 30
    timeout_in_seconds          = 10
    tolerated_number_of_failures = 3
  }
}

# Primary endpoint
resource "azurerm_traffic_manager_azure_endpoint" "primary" {
  name               = "primary"
  profile_id         = azurerm_traffic_manager_profile.main.id
  priority           = 1
  target_resource_id = azurerm_api_management.primary.id
}

# Secondary endpoint
resource "azurerm_traffic_manager_azure_endpoint" "secondary" {
  name               = "secondary"
  profile_id         = azurerm_traffic_manager_profile.main.id
  priority           = 2
  target_resource_id = module.secondary_region.apim_id
}
```

#### Data Replication Strategy
```hcl
# Key Vault replication
resource "azurerm_key_vault" "secondary" {
  name                = "authserver-kv-secondary"
  location            = "northeurope"
  resource_group_name = azurerm_resource_group.secondary.name
  tenant_id           = data.azurerm_client_config.current.tenant_id
  sku_name           = "standard"
  
  # Replicate secrets from primary
  depends_on = [azurerm_key_vault.primary]
}

# Storage Account replication
resource "azurerm_storage_account" "secondary" {
  name                     = "authserversecondary"
  resource_group_name      = azurerm_resource_group.secondary.name
  location                 = "northeurope"
  account_tier             = "Standard"
  account_replication_type = "GRS"  # Geo-redundant storage
  
  # Cross-region replication
  blob_properties {
    change_feed_enabled = true
    versioning_enabled  = true
  }
}
```

#### Failover Automation
```python
# Automated failover script
import time
import requests
from azure.identity import DefaultAzureCredential
from azure.mgmt.trafficmanager import TrafficManagerManagementClient
from azure.mgmt.monitor import MonitorManagementClient

class FailoverManager:
    def __init__(self):
        self.credential = DefaultAzureCredential()
        self.tm_client = TrafficManagerManagementClient(
            self.credential, 
            subscription_id="your-subscription-id"
        )
        self.monitor_client = MonitorManagementClient(
            self.credential,
            subscription_id="your-subscription-id"
        )
    
    def check_primary_health(self):
        """Check primary region health"""
        try:
            response = requests.get(
                "https://authserver-primary.azure-api.net/health",
                timeout=10
            )
            return response.status_code == 200
        except:
            return False
    
    def trigger_failover(self):
        """Trigger failover to secondary region"""
        print("Triggering failover to secondary region...")
        
        # Disable primary endpoint
        self.tm_client.endpoints.update(
            resource_group_name="authserver-rg",
            profile_name="authserver-tm",
            endpoint_type="azureEndpoints",
            endpoint_name="primary",
            parameters={
                "endpoint_status": "Disabled"
            }
        )
        
        # Enable secondary endpoint
        self.tm_client.endpoints.update(
            resource_group_name="authserver-rg",
            profile_name="authserver-tm",
            endpoint_type="azureEndpoints",
            endpoint_name="secondary",
            parameters={
                "endpoint_status": "Enabled"
            }
        )
        
        print("Failover completed successfully")
    
    def monitor_and_failover(self):
        """Continuous monitoring with automatic failover"""
        consecutive_failures = 0
        
        while True:
            if self.check_primary_health():
                consecutive_failures = 0
                print("Primary region healthy")
            else:
                consecutive_failures += 1
                print(f"Primary region unhealthy ({consecutive_failures}/3)")
                
                if consecutive_failures >= 3:
                    self.trigger_failover()
                    break
            
            time.sleep(30)  # Check every 30 seconds

# Run failover monitoring
failover_manager = FailoverManager()
failover_manager.monitor_and_failover()
```

### 2. Security Penetration Testing
Coordinate comprehensive security testing:

#### Penetration Testing Checklist
```markdown
## Security Testing Scope
### Authentication & Authorization
- [ ] OAuth 2.0 flow security
- [ ] JWT token validation
- [ ] Basic Auth fallback security
- [ ] RBAC implementation
- [ ] Session management

### Infrastructure Security
- [ ] Azure AD B2C configuration
- [ ] Key Vault access controls
- [ ] API Management security policies
- [ ] Network security groups
- [ ] TLS/SSL configuration

### Application Security
- [ ] Input validation
- [ ] SQL injection testing
- [ ] Cross-site scripting (XSS)
- [ ] Cross-site request forgery (CSRF)
- [ ] Security headers

### Compliance Testing
- [ ] PCI-DSS requirements
- [ ] SOX compliance
- [ ] Data residency verification
- [ ] Audit logging
- [ ] Encryption at rest and in transit
```

#### Security Test Automation
```python
# Automated security testing script
import requests
import json
from datetime import datetime

class SecurityTester:
    def __init__(self, base_url):
        self.base_url = base_url
        self.results = []
    
    def test_oauth_security(self):
        """Test OAuth endpoint security"""
        tests = [
            {
                "name": "Invalid client credentials",
                "data": "grant_type=client_credentials&client_id=invalid&client_secret=invalid",
                "expected_status": 401
            },
            {
                "name": "Missing grant type",
                "data": "client_id=test&client_secret=test",
                "expected_status": 400
            },
            {
                "name": "SQL injection attempt",
                "data": "grant_type=client_credentials&client_id='; DROP TABLE users; --&client_secret=test",
                "expected_status": 400
            }
        ]
        
        for test in tests:
            response = requests.post(
                f"{self.base_url}/oauth/token",
                data=test["data"],
                headers={"Content-Type": "application/x-www-form-urlencoded"}
            )
            
            result = {
                "test": test["name"],
                "expected": test["expected_status"],
                "actual": response.status_code,
                "passed": response.status_code == test["expected_status"],
                "timestamp": datetime.utcnow().isoformat()
            }
            
            self.results.append(result)
    
    def test_jwt_validation_security(self):
        """Test JWT validation security"""
        tests = [
            {
                "name": "Invalid JWT token",
                "token": "invalid.jwt.token",
                "expected_status": 401
            },
            {
                "name": "Expired JWT token",
                "token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9...",  # Expired token
                "expected_status": 401
            },
            {
                "name": "Malformed JWT token",
                "token": "malformed-token",
                "expected_status": 400
            }
        ]
        
        for test in tests:
            response = requests.post(
                f"{self.base_url}/oauth/check_token",
                data=f"token={test['token']}",
                headers={"Content-Type": "application/x-www-form-urlencoded"}
            )
            
            result = {
                "test": test["name"],
                "expected": test["expected_status"],
                "actual": response.status_code,
                "passed": response.status_code == test["expected_status"],
                "timestamp": datetime.utcnow().isoformat()
            }
            
            self.results.append(result)
    
    def generate_report(self):
        """Generate security test report"""
        passed_tests = sum(1 for r in self.results if r["passed"])
        total_tests = len(self.results)
        
        report = {
            "summary": {
                "total_tests": total_tests,
                "passed_tests": passed_tests,
                "failed_tests": total_tests - passed_tests,
                "success_rate": (passed_tests / total_tests) * 100 if total_tests > 0 else 0
            },
            "details": self.results,
            "generated_at": datetime.utcnow().isoformat()
        }
        
        return json.dumps(report, indent=2)

# Run security tests
tester = SecurityTester("https://authserver.azure-api.net")
tester.test_oauth_security()
tester.test_jwt_validation_security()
print(tester.generate_report())
```

### 3. AWS Resource Decommissioning
Safely decommission AWS infrastructure:

#### Pre-Decommissioning Checklist
```bash
#!/bin/bash
# AWS decommissioning safety checklist

echo "=== AWS Decommissioning Safety Checklist ==="

# 1. Verify Azure is handling 100% traffic
echo "1. Checking traffic routing..."
AZURE_TRAFFIC=$(curl -s "https://authserver.azure-api.net/health" | jq -r '.status')
if [ "$AZURE_TRAFFIC" != "healthy" ]; then
    echo "ERROR: Azure environment not healthy. Aborting decommissioning."
    exit 1
fi

# 2. Verify no active AWS traffic
echo "2. Checking AWS traffic..."
AWS_REQUESTS=$(aws logs filter-log-events \
    --log-group-name "/aws/lambda/authserver" \
    --start-time $(date -d '1 hour ago' +%s)000 \
    --query 'events[].message' \
    --output text | wc -l)

if [ "$AWS_REQUESTS" -gt 0 ]; then
    echo "WARNING: AWS still receiving traffic ($AWS_REQUESTS requests in last hour)"
    echo "Please verify traffic routing before proceeding."
    read -p "Continue anyway? (y/N): " confirm
    if [ "$confirm" != "y" ]; then
        exit 1
    fi
fi

# 3. Backup critical data
echo "3. Creating final backups..."
aws s3 sync s3://authserver-artifacts s3://authserver-backup-$(date +%Y%m%d)
aws logs create-export-task \
    --log-group-name "/aws/lambda/authserver" \
    --from $(date -d '30 days ago' +%s)000 \
    --to $(date +%s)000 \
    --destination "authserver-logs-backup"

# 4. Document current configuration
echo "4. Documenting current configuration..."
aws cloudformation describe-stacks \
    --stack-name authserver \
    --output json > aws-final-config.json

echo "Pre-decommissioning checks completed successfully."
```

#### Gradual Decommissioning Script
```bash
#!/bin/bash
# Gradual AWS resource decommissioning

STACK_NAME="authserver"
REGION="us-east-1"

echo "=== Starting AWS Resource Decommissioning ==="

# Phase 1: Disable Lambda triggers (keep functions for rollback)
echo "Phase 1: Disabling Lambda triggers..."
aws apigateway update-stage \
    --rest-api-id $(aws apigateway get-rest-apis --query 'items[?name==`authserver-api`].id' --output text) \
    --stage-name prod \
    --patch-ops op=replace,path=/throttle/rateLimit,value=0

# Wait and monitor for 24 hours
echo "Waiting 24 hours to ensure no issues..."
sleep 86400

# Phase 2: Delete API Gateway
echo "Phase 2: Deleting API Gateway..."
API_ID=$(aws apigateway get-rest-apis --query 'items[?name==`authserver-api`].id' --output text)
aws apigateway delete-rest-api --rest-api-id $API_ID

# Wait and monitor for 24 hours
echo "Waiting 24 hours to ensure no issues..."
sleep 86400

# Phase 3: Delete Lambda functions
echo "Phase 3: Deleting Lambda functions..."
aws lambda delete-function --function-name authserver-oauth-token
aws lambda delete-function --function-name authserver-jwt-authorizer
aws lambda delete-function --function-name authserver-basic-auth

# Phase 4: Delete Cognito User Pool
echo "Phase 4: Deleting Cognito User Pool..."
USER_POOL_ID=$(aws cognito-idp list-user-pools --max-items 50 --query 'UserPools[?Name==`authserver-pool`].Id' --output text)
aws cognito-idp delete-user-pool --user-pool-id $USER_POOL_ID

# Phase 5: Delete CloudFormation stack
echo "Phase 5: Deleting CloudFormation stack..."
aws cloudformation delete-stack --stack-name $STACK_NAME

# Monitor stack deletion
echo "Monitoring stack deletion..."
aws cloudformation wait stack-delete-complete --stack-name $STACK_NAME

echo "AWS decommissioning completed successfully!"
```

### 4. Final Cost Optimization
Optimize Azure costs post-migration:

#### Cost Analysis Script
```python
# Azure cost analysis and optimization
from azure.identity import DefaultAzureCredential
from azure.mgmt.consumption import ConsumptionManagementClient
from azure.mgmt.costmanagement import CostManagementClient
import pandas as pd
from datetime import datetime, timedelta

class CostOptimizer:
    def __init__(self):
        self.credential = DefaultAzureCredential()
        self.consumption_client = ConsumptionManagementClient(
            self.credential,
            subscription_id="your-subscription-id"
        )
    
    def analyze_costs(self):
        """Analyze current Azure costs"""
        # Get cost data for last 30 days
        end_date = datetime.now()
        start_date = end_date - timedelta(days=30)
        
        # Query cost data
        scope = f"/subscriptions/your-subscription-id/resourceGroups/authserver-rg"
        
        usage_details = self.consumption_client.usage_details.list(
            scope=scope,
            filter=f"properties/usageStart ge '{start_date.isoformat()}' and properties/usageStart le '{end_date.isoformat()}'"
        )
        
        costs_by_service = {}
        for usage in usage_details:
            service = usage.meter_category
            cost = usage.cost
            
            if service in costs_by_service:
                costs_by_service[service] += cost
            else:
                costs_by_service[service] = cost
        
        return costs_by_service
    
    def recommend_optimizations(self, costs):
        """Recommend cost optimizations"""
        recommendations = []
        
        # Function App optimization
        if "Azure Functions" in costs and costs["Azure Functions"] > 100:
            recommendations.append({
                "service": "Azure Functions",
                "current_cost": costs["Azure Functions"],
                "recommendation": "Consider Premium plan for consistent workloads",
                "potential_savings": costs["Azure Functions"] * 0.15
            })
        
        # API Management optimization
        if "API Management" in costs and costs["API Management"] > 200:
            recommendations.append({
                "service": "API Management",
                "current_cost": costs["API Management"],
                "recommendation": "Evaluate if Developer tier is sufficient",
                "potential_savings": costs["API Management"] * 0.30
            })
        
        # Storage optimization
        if "Storage" in costs and costs["Storage"] > 50:
            recommendations.append({
                "service": "Storage",
                "current_cost": costs["Storage"],
                "recommendation": "Implement lifecycle policies for blob storage",
                "potential_savings": costs["Storage"] * 0.20
            })
        
        return recommendations
    
    def generate_cost_report(self):
        """Generate comprehensive cost report"""
        costs = self.analyze_costs()
        recommendations = self.recommend_optimizations(costs)
        
        total_cost = sum(costs.values())
        potential_savings = sum(r["potential_savings"] for r in recommendations)
        
        report = {
            "summary": {
                "total_monthly_cost": total_cost,
                "potential_monthly_savings": potential_savings,
                "optimization_percentage": (potential_savings / total_cost) * 100 if total_cost > 0 else 0
            },
            "costs_by_service": costs,
            "recommendations": recommendations,
            "generated_at": datetime.utcnow().isoformat()
        }
        
        return report

# Generate cost optimization report
optimizer = CostOptimizer()
report = optimizer.generate_cost_report()
print(json.dumps(report, indent=2))
```

### 5. Documentation & Knowledge Transfer
Create comprehensive operational documentation:

#### Operational Runbook Template
```markdown
# Authserver Azure Operations Runbook

## Service Overview
- **Service**: OAuth 2.0 Authentication Server
- **Platform**: Azure Functions + API Management
- **Regions**: West Europe (primary), North Europe (secondary)
- **SLA**: 99.9% uptime

## Architecture
### Components
- Azure Functions (Java 11)
- Azure API Management
- Azure AD B2C
- Azure Key Vault
- Azure Monitor + Application Insights

### Endpoints
- `/oauth/token` - Token issuance
- `/oauth/check_token` - Token validation
- `/health` - Health check

## Monitoring & Alerting
### Key Metrics
- Response time p95: ≤ 200ms
- Error rate: < 2%
- Availability: ≥ 99.9%

### Alert Conditions
- High error rate (>2% for 5 minutes)
- High response time (>200ms p95 for 5 minutes)
- Service unavailability

### Dashboards
- [Azure Monitor Dashboard](link)
- [Application Insights Dashboard](link)

## Troubleshooting
### Common Issues
1. **High Response Times**
   - Check Function App scaling
   - Review Application Insights performance data
   - Verify Key Vault connectivity

2. **Authentication Failures**
   - Check Azure AD B2C status
   - Verify client credentials
   - Review JWT token validation

3. **Service Unavailability**
   - Check Function App status
   - Verify API Management health
   - Review Traffic Manager configuration

### Escalation Procedures
1. Level 1: Operations team
2. Level 2: Platform engineering team
3. Level 3: Development team

## Disaster Recovery
### Failover Procedures
1. Verify primary region status
2. Trigger Traffic Manager failover
3. Monitor secondary region performance
4. Communicate status to stakeholders

### Recovery Procedures
1. Restore primary region services
2. Verify functionality
3. Failback traffic to primary region
4. Post-incident review

## Maintenance
### Regular Tasks
- Monthly cost review
- Quarterly security review
- Semi-annual disaster recovery testing
- Annual penetration testing

### Update Procedures
1. Deploy to staging slot
2. Run validation tests
3. Swap to production slot
4. Monitor for issues
5. Rollback if necessary
```

## Output Format
Provide your cleanup plan in the following structure:

```markdown
## Post-Migration Cleanup Plan
### Multi-Region Setup
- [Secondary region configuration]
- [Failover automation]
- [Data replication strategy]

### Security Testing
- [Penetration testing scope]
- [Security test results]
- [Remediation actions]

### AWS Decommissioning
- [Decommissioning timeline]
- [Safety checks and backups]
- [Resource removal steps]

### Cost Optimization
- [Cost analysis results]
- [Optimization recommendations]
- [Implementation plan]

### Documentation
- [Operational runbooks]
- [Knowledge transfer materials]
- [Training requirements]

## Final Validation
### Performance Metrics
- [Current performance vs targets]
- [SLA compliance verification]

### Cost Analysis
- [Azure vs AWS cost comparison]
- [ROI analysis]

### Security Posture
- [Security assessment results]
- [Compliance verification]
```

## Success Criteria
- **Multi-Region**: Active-passive setup operational
- **Security**: Penetration testing passed with no critical issues
- **AWS Cleanup**: All AWS resources safely decommissioned
- **Cost**: Final costs within +15% of AWS baseline
- **Documentation**: Complete operational runbooks delivered
- **Knowledge Transfer**: Operations team trained and ready

## Final Deliverables
- **Operational Runbooks**: Complete troubleshooting and maintenance guides
- **Security Report**: Penetration testing results and remediation
- **Cost Analysis**: Final cost comparison and optimization plan
- **Architecture Documentation**: As-built Azure architecture diagrams
- **Training Materials**: Operations team training documentation
- **Lessons Learned**: Migration retrospective and recommendations 