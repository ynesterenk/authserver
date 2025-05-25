# Cutover & Deployment Phase - Blue-Green Migration Strategy

## Context
You are implementing the final cutover from AWS to Azure for the authserver. This is Phase 7 focusing on zero-downtime migration using blue-green deployment with automated rollback capabilities.

## Cutover Requirements (from PRD)
- **Blue/Green DNS Swap**: Seamless traffic transition in APIM
- **Traffic Mirror**: 15-minute traffic mirroring for validation
- **Auto-Rollback**: Automatic rollback if 5xx errors > 2% in 5-minute window
- **Zero Downtime**: No service interruption during cutover
- **Validation**: Comprehensive health checks before traffic switch

## Current State
- **Blue Environment**: AWS authserver (production traffic)
- **Green Environment**: Azure authserver (ready for cutover)
- **Traffic Router**: Azure API Management with routing policies

## Your Tasks

### 1. Blue-Green Deployment Architecture
Design the deployment strategy:

#### Traffic Routing Configuration
```xml
<!-- APIM Policy for Blue-Green Routing -->
<policies>
    <inbound>
        <choose>
            <when condition="@(context.Variables.GetValueOrDefault<string>("deployment-slot") == "green")">
                <set-backend-service base-url="https://authserver-azure.azurewebsites.net" />
            </when>
            <otherwise>
                <set-backend-service base-url="https://aws-authserver.amazonaws.com" />
            </otherwise>
        </choose>
        
        <!-- Traffic splitting for gradual migration -->
        <choose>
            <when condition="@(new Random().Next(1, 101) <= context.Variables.GetValueOrDefault<int>("green-traffic-percentage", 0))">
                <set-variable name="deployment-slot" value="green" />
            </when>
            <otherwise>
                <set-variable name="deployment-slot" value="blue" />
            </otherwise>
        </choose>
    </inbound>
    
    <backend>
        <forward-request />
    </backend>
    
    <outbound>
        <!-- Add deployment slot header for monitoring -->
        <set-header name="X-Deployment-Slot" exists-action="override">
            <value>@(context.Variables.GetValueOrDefault<string>("deployment-slot"))</value>
        </set-header>
    </outbound>
</policies>
```

#### Deployment Slots Configuration
```hcl
# Terraform configuration for deployment slots
resource "azurerm_linux_function_app_slot" "staging" {
  name                 = "staging"
  function_app_id      = azurerm_linux_function_app.main.id
  storage_account_name = azurerm_storage_account.main.name

  site_config {
    application_stack {
      java_version = "11"
    }
    
    # Staging-specific configurations
    app_settings = merge(
      azurerm_linux_function_app.main.app_settings,
      {
        "ENVIRONMENT" = "staging"
        "SLOT_NAME"   = "staging"
      }
    )
  }
}

# Slot swap configuration
resource "azurerm_function_app_slot_swap" "main" {
  slot_name        = azurerm_linux_function_app_slot.staging.name
  function_app_id  = azurerm_linux_function_app.main.id
  
  # Only swap when explicitly triggered
  lifecycle {
    ignore_changes = [slot_name]
  }
}
```

### 2. Pre-Cutover Validation
Implement comprehensive validation before traffic switch:

#### Health Check Endpoints
```java
// Azure Function health check endpoint
@FunctionName("health-check")
public HttpResponseMessage healthCheck(
    @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "health") 
    HttpRequestMessage<Optional<String>> request,
    final ExecutionContext context) {
    
    HealthCheckResult result = performHealthChecks();
    
    return request.createResponseBuilder(
        result.isHealthy() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
        .header("Content-Type", "application/json")
        .body(result)
        .build();
}

@Component
public class HealthCheckService {
    public HealthCheckResult performHealthChecks() {
        HealthCheckResult result = new HealthCheckResult();
        
        // Check Azure AD B2C connectivity
        result.addCheck("b2c-connectivity", checkB2CConnectivity());
        
        // Check Key Vault access
        result.addCheck("keyvault-access", checkKeyVaultAccess());
        
        // Check database connectivity (if applicable)
        result.addCheck("database-connectivity", checkDatabaseConnectivity());
        
        // Check external service dependencies
        result.addCheck("external-services", checkExternalServices());
        
        return result;
    }
}
```

#### Smoke Tests
```yaml
# Azure DevOps Pipeline for smoke tests
stages:
- stage: SmokeTests
  displayName: 'Pre-Cutover Smoke Tests'
  jobs:
  - job: RunSmokeTests
    displayName: 'Run Smoke Tests'
    steps:
    - task: RestApiTask@1
      displayName: 'Test OAuth Token Endpoint'
      inputs:
        connectionType: 'connectedServiceName'
        serviceConnection: 'Azure-Test-Environment'
        method: 'POST'
        headers: |
          Content-Type: application/x-www-form-urlencoded
        body: 'grant_type=client_credentials&client_id=$(TEST_CLIENT_ID)&client_secret=$(TEST_CLIENT_SECRET)'
        urlSuffix: '/oauth/token'
        waitForCompletion: 'true'
        successCriteria: 'eq(root[''access_token''], null)'
    
    - task: RestApiTask@1
      displayName: 'Test JWT Validation Endpoint'
      inputs:
        method: 'POST'
        headers: |
          Content-Type: application/x-www-form-urlencoded
        body: 'token=$(ACCESS_TOKEN)'
        urlSuffix: '/oauth/check_token'
        successCriteria: 'eq(root[''active''], true)'
```

### 3. Traffic Mirroring Implementation
Implement traffic mirroring for validation:

#### APIM Traffic Mirroring Policy
```xml
<!-- Traffic mirroring policy -->
<policies>
    <inbound>
        <!-- Clone request for mirroring -->
        <set-variable name="original-request-body" value="@(context.Request.Body.As<string>(preserveContent: true))" />
        <set-variable name="original-request-headers" value="@(context.Request.Headers)" />
    </inbound>
    
    <backend>
        <forward-request />
    </backend>
    
    <outbound>
        <!-- Mirror traffic to Azure environment -->
        <choose>
            <when condition="@(context.Variables.GetValueOrDefault<bool>("mirror-enabled", false))">
                <send-request mode="new" response-variable-name="mirror-response" timeout="10" ignore-error="true">
                    <set-url>https://authserver-azure.azurewebsites.net@(context.Request.Url.Path)</set-url>
                    <set-method>@(context.Request.Method)</set-method>
                    <set-header name="Content-Type" exists-action="override">
                        <value>@(context.Request.Headers.GetValueOrDefault("Content-Type", ""))</value>
                    </set-header>
                    <set-body>@(context.Variables.GetValueOrDefault<string>("original-request-body"))</set-body>
                </send-request>
                
                <!-- Log mirroring results for comparison -->
                <log-to-eventhub logger-id="mirror-logger">
                    @{
                        return new JObject(
                            new JProperty("timestamp", DateTime.UtcNow),
                            new JProperty("original-status", context.Response.StatusCode),
                            new JProperty("mirror-status", ((IResponse)context.Variables["mirror-response"]).StatusCode),
                            new JProperty("request-id", context.RequestId)
                        ).ToString();
                    }
                </log-to-eventhub>
            </when>
        </choose>
    </outbound>
</policies>
```

### 4. Automated Monitoring & Rollback
Implement automated monitoring and rollback:

#### Real-time Monitoring
```python
# Python script for real-time monitoring
import time
import requests
from azure.monitor.query import LogsQueryClient
from azure.identity import DefaultAzureCredential

class CutoverMonitor:
    def __init__(self):
        self.credential = DefaultAzureCredential()
        self.logs_client = LogsQueryClient(self.credential)
        self.workspace_id = "your-workspace-id"
        
    def monitor_error_rate(self, duration_minutes=5):
        """Monitor error rate and trigger rollback if threshold exceeded"""
        query = f"""
        requests
        | where timestamp > ago({duration_minutes}m)
        | where name contains "oauth"
        | summarize 
            total_requests = count(),
            error_requests = countif(resultCode >= 500)
        | extend error_rate = (error_requests * 100.0) / total_requests
        """
        
        result = self.logs_client.query_workspace(
            workspace_id=self.workspace_id,
            query=query,
            timespan=timedelta(minutes=duration_minutes)
        )
        
        if result.tables:
            error_rate = result.tables[0].rows[0][2]  # error_rate column
            if error_rate > 2.0:  # 2% threshold
                self.trigger_rollback()
                return False
        return True
    
    def trigger_rollback(self):
        """Trigger automatic rollback to AWS"""
        print("ERROR THRESHOLD EXCEEDED - TRIGGERING ROLLBACK")
        
        # Update APIM policy to route all traffic back to AWS
        rollback_policy = """
        <policies>
            <inbound>
                <set-backend-service base-url="https://aws-authserver.amazonaws.com" />
            </inbound>
        </policies>
        """
        
        # Apply rollback policy via Azure REST API
        self.apply_apim_policy(rollback_policy)
        
        # Send alerts
        self.send_rollback_alert()

# Monitoring loop
monitor = CutoverMonitor()
for i in range(180):  # Monitor for 15 minutes
    if not monitor.monitor_error_rate():
        break
    time.sleep(5)  # Check every 5 seconds
```

### 5. Gradual Traffic Migration
Implement gradual traffic shift:

#### Traffic Percentage Controller
```hcl
# Terraform variable for traffic percentage
variable "green_traffic_percentage" {
  description = "Percentage of traffic to route to Azure (green) environment"
  type        = number
  default     = 0
  
  validation {
    condition     = var.green_traffic_percentage >= 0 && var.green_traffic_percentage <= 100
    error_message = "Traffic percentage must be between 0 and 100."
  }
}

# APIM named value for traffic control
resource "azurerm_api_management_named_value" "green_traffic_percentage" {
  name                = "green-traffic-percentage"
  api_management_name = azurerm_api_management.main.name
  resource_group_name = azurerm_resource_group.main.name
  display_name        = "Green Traffic Percentage"
  value               = var.green_traffic_percentage
}
```

#### Migration Script
```bash
#!/bin/bash
# Gradual traffic migration script

RESOURCE_GROUP="authserver-rg"
APIM_NAME="authserver-apim"
NAMED_VALUE="green-traffic-percentage"

# Traffic migration stages
STAGES=(0 10 25 50 75 100)

for stage in "${STAGES[@]}"; do
    echo "Migrating $stage% traffic to Azure..."
    
    # Update traffic percentage
    az apim nv update \
        --resource-group $RESOURCE_GROUP \
        --service-name $APIM_NAME \
        --named-value-id $NAMED_VALUE \
        --value $stage
    
    # Wait for traffic to stabilize
    echo "Waiting 15 minutes for traffic to stabilize..."
    sleep 900
    
    # Check error rates
    python monitor_cutover.py --duration 5 --threshold 2.0
    
    if [ $? -ne 0 ]; then
        echo "Error threshold exceeded, rolling back..."
        az apim nv update \
            --resource-group $RESOURCE_GROUP \
            --service-name $APIM_NAME \
            --named-value-id $NAMED_VALUE \
            --value 0
        exit 1
    fi
    
    echo "Stage $stage% completed successfully"
done

echo "Migration completed successfully!"
```

### 6. Post-Cutover Validation
Implement comprehensive post-cutover validation:

#### Validation Test Suite
```java
// Comprehensive validation tests
@TestMethodOrder(OrderAnnotation.class)
public class PostCutoverValidationTests {
    
    @Test
    @Order(1)
    public void testOAuthTokenIssuance() {
        // Test OAuth token endpoint functionality
        String response = restTemplate.postForObject(
            "/oauth/token",
            createTokenRequest(),
            String.class
        );
        
        assertThat(response).contains("access_token");
        assertThat(response).contains("token_type");
    }
    
    @Test
    @Order(2)
    public void testJWTValidation() {
        // Test JWT validation endpoint
        String token = getValidToken();
        String response = restTemplate.postForObject(
            "/oauth/check_token",
            createValidationRequest(token),
            String.class
        );
        
        assertThat(response).contains("\"active\":true");
    }
    
    @Test
    @Order(3)
    public void testBasicAuthFallback() {
        // Test Basic Auth fallback mechanism
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("testuser", "testpass");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
            "/oauth/token",
            HttpMethod.POST,
            entity,
            String.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    @Test
    @Order(4)
    public void testPerformanceRequirements() {
        // Test performance requirements
        long startTime = System.currentTimeMillis();
        
        String response = restTemplate.postForObject(
            "/oauth/token",
            createTokenRequest(),
            String.class
        );
        
        long duration = System.currentTimeMillis() - startTime;
        assertThat(duration).isLessThan(200); // 200ms requirement
    }
}
```

## Output Format
Provide your cutover plan in the following structure:

```markdown
## Cutover Execution Plan
### Pre-Cutover Checklist
- [ ] [Validation item 1]
- [ ] [Validation item 2]

### Traffic Migration Strategy
1. [Stage 1: 0% → 10%]
2. [Stage 2: 10% → 25%]
3. [Stage 3: 25% → 50%]
4. [Stage 4: 50% → 75%]
5. [Stage 5: 75% → 100%]

### Monitoring & Alerting
- [Real-time monitoring setup]
- [Rollback triggers and thresholds]

### Rollback Procedures
- [Automatic rollback conditions]
- [Manual rollback steps]

### Post-Cutover Validation
- [Validation test suite]
- [Performance verification]

## Risk Mitigation
### Identified Risks
- [Risk 1 and mitigation]
- [Risk 2 and mitigation]

### Contingency Plans
- [Plan A: Partial rollback]
- [Plan B: Full rollback]
- [Plan C: Emergency procedures]
```

## Success Criteria
- **Zero Downtime**: No service interruption during cutover
- **Performance**: Maintain ≤ 200ms p95 latency
- **Error Rate**: Keep 5xx errors < 2% throughout migration
- **Functional Parity**: All OAuth flows working correctly
- **Monitoring**: Real-time visibility into migration progress
- **Rollback**: Successful rollback capability if needed

## Critical Success Factors
- **Comprehensive Testing**: Thorough pre-cutover validation
- **Gradual Migration**: Staged traffic increase with validation
- **Real-time Monitoring**: Continuous error rate and performance monitoring
- **Automated Rollback**: Quick response to issues
- **Communication**: Clear status updates to stakeholders
- **Documentation**: Complete runbook for operations team 