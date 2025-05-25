# Performance & Cost Optimization Phase

## Context
You are optimizing the migrated Azure authserver for performance and cost efficiency. This is Phase 6 focusing on meeting SLA requirements and cost targets while maintaining functional parity.

## Performance Requirements (from PRD)
- **Warm Performance**: ≤ 200ms p95 latency under 100 RPS
- **Cold Start**: ≤ 500ms cold-start time
- **Reliability**: 99.9% monthly SLA
- **Cost Target**: ≤ +15% monthly runtime cost vs AWS baseline
- **Throughput**: Handle 100+ requests per second during peak

## Current Azure Architecture
- Azure Functions (Java 11) on Consumption Plan
- Azure API Management for routing
- Azure AD B2C for authentication
- Azure Monitor for observability

## Your Tasks

### 1. Cold Start Optimization
Minimize Azure Functions cold start times:

#### Function App Configuration
- **Runtime Version**: Use latest Java 11 runtime
- **Memory Allocation**: Optimize memory settings (512MB-1GB)
- **Initialization Code**: Minimize static initialization
- **Dependency Loading**: Lazy load non-critical dependencies
- **Connection Pooling**: Pre-warm database/service connections

#### Provisioned Concurrency
```hcl
# Terraform configuration for provisioned concurrency
resource "azurerm_function_app_slot" "warm" {
  name                = "warm"
  function_app_name   = azurerm_function_app.main.name
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  
  site_config {
    always_on = true
    pre_warmed_instance_count = 2
  }
}
```

#### Premium Plan Consideration
Evaluate Premium Plan for consistent performance:
- **Always On**: Eliminate cold starts entirely
- **VNet Integration**: Improved network performance
- **Dedicated Compute**: Predictable performance
- **Cost Analysis**: Compare with Consumption Plan costs

### 2. Warm Performance Optimization
Optimize response times for warm instances:

#### Code-Level Optimizations
- **JWT Validation**: Cache public keys and validation logic
- **Database Connections**: Use connection pooling
- **HTTP Clients**: Reuse HTTP client instances
- **Serialization**: Optimize JSON serialization/deserialization
- **Logging**: Minimize synchronous logging overhead

#### Caching Strategies
```java
// Example: JWT public key caching
@Component
public class JwtValidationService {
    private final Cache<String, RSAPublicKey> publicKeyCache = 
        Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    
    public boolean validateToken(String token) {
        // Use cached public key for validation
        String keyId = extractKeyId(token);
        RSAPublicKey publicKey = publicKeyCache.get(keyId, this::fetchPublicKey);
        return validateSignature(token, publicKey);
    }
}
```

#### Memory Management
- **Heap Size**: Optimize JVM heap settings
- **Garbage Collection**: Tune GC for low latency
- **Object Pooling**: Reuse expensive objects
- **Memory Leaks**: Profile and eliminate memory leaks

### 3. API Management Optimization
Optimize APIM for performance:

#### Caching Policies
```xml
<!-- APIM caching policy -->
<policies>
    <inbound>
        <cache-lookup vary-by-developer="false" 
                     vary-by-developer-groups="false" 
                     downstream-caching-type="none">
            <vary-by-header>Authorization</vary-by-header>
            <vary-by-query-parameter>grant_type</vary-by-query-parameter>
        </cache-lookup>
    </inbound>
    <outbound>
        <cache-store duration="300" />
    </outbound>
</policies>
```

#### Rate Limiting & Throttling
```xml
<!-- Rate limiting policy -->
<policies>
    <inbound>
        <rate-limit calls="1000" renewal-period="60" />
        <quota calls="10000" renewal-period="3600" />
    </inbound>
</policies>
```

#### Backend Pool Configuration
- **Load Balancing**: Distribute load across function instances
- **Health Checks**: Implement health check endpoints
- **Circuit Breaker**: Prevent cascade failures
- **Retry Policies**: Configure intelligent retry logic

### 4. Database & External Service Optimization
Optimize external dependencies:

#### Azure AD B2C Optimization
- **Token Caching**: Cache B2C tokens appropriately
- **Connection Pooling**: Reuse HTTPS connections
- **Batch Operations**: Minimize API calls where possible
- **Regional Proximity**: Use B2C in same region as functions

#### Key Vault Optimization
```java
// Optimized Key Vault client
@Configuration
public class KeyVaultConfig {
    @Bean
    @Scope("singleton")
    public SecretClient secretClient() {
        return new SecretClientBuilder()
            .vaultUrl(keyVaultUrl)
            .credential(new DefaultAzureCredentialBuilder().build())
            .httpClient(HttpClient.createDefault())
            .buildClient();
    }
}
```

### 5. Monitoring & Profiling
Implement comprehensive performance monitoring:

#### Application Insights Configuration
```java
// Custom telemetry for performance tracking
@Component
public class PerformanceTracker {
    private final TelemetryClient telemetryClient;
    
    public void trackDependency(String name, long duration, boolean success) {
        telemetryClient.trackDependency(name, name, 
            Instant.now().minusMillis(duration), 
            Duration.ofMillis(duration), success);
    }
    
    public void trackCustomMetric(String name, double value) {
        telemetryClient.trackMetric(name, value);
    }
}
```

#### Performance Dashboards
Create Azure Monitor dashboards for:
- **Response Time Percentiles**: p50, p95, p99
- **Throughput Metrics**: Requests per second
- **Error Rates**: 4xx and 5xx response rates
- **Cold Start Frequency**: Cold start occurrence tracking
- **Resource Utilization**: CPU, memory, network usage

#### Alerting Rules
```json
{
  "name": "High Response Time Alert",
  "criteria": {
    "metricName": "Http Server Response Time",
    "operator": "GreaterThan",
    "threshold": 200,
    "timeAggregation": "Average",
    "windowSize": "PT5M"
  },
  "actions": [
    {
      "actionGroupId": "/subscriptions/.../actionGroups/performance-alerts"
    }
  ]
}
```

### 6. Cost Optimization
Optimize costs while maintaining performance:

#### Function App Sizing
- **Right-sizing**: Match memory allocation to actual usage
- **Execution Time**: Optimize function execution duration
- **Consumption vs Premium**: Cost-benefit analysis
- **Reserved Capacity**: Consider reserved instances for predictable workloads

#### Resource Optimization
```hcl
# Cost-optimized Terraform configuration
resource "azurerm_service_plan" "main" {
  name                = "authserver-plan"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  os_type             = "Linux"
  sku_name           = "Y1"  # Consumption plan for cost efficiency
}

# Auto-scaling configuration
resource "azurerm_monitor_autoscale_setting" "main" {
  name                = "authserver-autoscale"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  target_resource_id  = azurerm_service_plan.main.id

  profile {
    name = "default"
    
    capacity {
      default = 2
      minimum = 1
      maximum = 10
    }
    
    rule {
      metric_trigger {
        metric_name        = "CpuPercentage"
        metric_resource_id = azurerm_service_plan.main.id
        operator           = "GreaterThan"
        statistic          = "Average"
        threshold          = 70
        time_aggregation   = "Average"
        time_grain         = "PT1M"
        time_window        = "PT5M"
      }
      
      scale_action {
        direction = "Increase"
        type      = "ChangeCount"
        value     = "1"
        cooldown  = "PT5M"
      }
    }
  }
}
```

### 7. Load Testing & Benchmarking
Implement comprehensive performance testing:

#### Load Testing Strategy
```yaml
# Azure Load Testing configuration
testPlan:
  name: "authserver-load-test"
  scenarios:
    - name: "oauth-token-load"
      endpoint: "https://authserver-apim.azure-api.net/oauth/token"
      method: "POST"
      headers:
        Content-Type: "application/x-www-form-urlencoded"
      body: "grant_type=client_credentials&client_id=test&client_secret=secret"
      users: 100
      duration: "10m"
      rampUp: "2m"
    
    - name: "jwt-validation-load"
      endpoint: "https://authserver-apim.azure-api.net/oauth/check_token"
      method: "POST"
      users: 50
      duration: "10m"
```

#### Performance Benchmarks
Establish baseline metrics:
- **AWS Baseline**: Current AWS performance metrics
- **Azure Target**: Target Azure performance metrics
- **Regression Testing**: Automated performance regression detection
- **Capacity Planning**: Peak load handling capabilities

## Output Format
Provide your optimization plan in the following structure:

```markdown
## Performance Optimization Plan
### Cold Start Optimization
- [Specific optimizations and configurations]

### Warm Performance Tuning
- [Code and configuration optimizations]

### Infrastructure Optimization
- [Azure resource configurations]

### Monitoring & Alerting
- [Performance monitoring setup]

### Cost Optimization
- [Cost reduction strategies]

### Load Testing Results
- [Performance test results and analysis]

## Implementation Priority
1. [High priority optimizations]
2. [Medium priority optimizations]
3. [Low priority optimizations]

## Performance Metrics
### Before Optimization
- [Baseline metrics]

### After Optimization
- [Target metrics]

### Cost Analysis
- [Cost comparison with AWS]
```

## Success Criteria
- **Performance**: Meet ≤ 200ms p95 and ≤ 500ms cold start targets
- **Reliability**: Achieve 99.9% uptime SLA
- **Cost**: Stay within +15% of AWS baseline costs
- **Scalability**: Handle 100+ RPS during peak loads
- **Monitoring**: Comprehensive observability in place
- **Testing**: Automated performance regression testing

## Key Focus Areas
- **Critical Path Optimization**: Focus on OAuth token issuance flow
- **Resource Right-sizing**: Match resources to actual usage patterns
- **Caching Strategy**: Implement intelligent caching at all layers
- **Connection Management**: Optimize all external service connections
- **Proactive Monitoring**: Early detection of performance degradation 