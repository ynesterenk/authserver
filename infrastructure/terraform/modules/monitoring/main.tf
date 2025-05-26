# Log Analytics Workspace
resource "azurerm_log_analytics_workspace" "main" {
  name                = "${var.name_prefix}-law"
  location            = var.location
  resource_group_name = var.resource_group_name
  sku                 = "PerGB2018"
  retention_in_days   = var.log_analytics_retention_days

  tags = var.tags
}

# Application Insights
resource "azurerm_application_insights" "main" {
  name                = "${var.name_prefix}-ai"
  location            = var.location
  resource_group_name = var.resource_group_name
  workspace_id        = azurerm_log_analytics_workspace.main.id
  application_type    = "web"
  sampling_percentage = var.application_insights_sampling_percentage

  tags = var.tags
}

# Action Group for alerts
resource "azurerm_monitor_action_group" "main" {
  name                = "${var.name_prefix}-action-group"
  resource_group_name = var.resource_group_name
  short_name          = "authserver"

  # Email notification (placeholder - should be configured with actual email)
  email_receiver {
    name          = "admin-email"
    email_address = "admin@example.com"
  }

  # Webhook notification (placeholder - can be configured for Teams, Slack, etc.)
  webhook_receiver {
    name        = "webhook"
    service_uri = "https://example.com/webhook"
  }

  tags = var.tags
}

# Metric Alert for Function App errors
resource "azurerm_monitor_metric_alert" "function_errors" {
  name                = "${var.name_prefix}-function-errors"
  resource_group_name = var.resource_group_name
  scopes              = [azurerm_application_insights.main.id]
  description         = "Alert when function app error rate exceeds threshold"
  severity            = 2
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.Insights/components"
    metric_name      = "exceptions/count"
    aggregation      = "Count"
    operator         = "GreaterThan"
    threshold        = 5
  }

  action {
    action_group_id = azurerm_monitor_action_group.main.id
  }

  tags = var.tags
}

# Metric Alert for Function App response time
resource "azurerm_monitor_metric_alert" "function_response_time" {
  name                = "${var.name_prefix}-function-response-time"
  resource_group_name = var.resource_group_name
  scopes              = [azurerm_application_insights.main.id]
  description         = "Alert when function app response time exceeds threshold"
  severity            = 3
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.Insights/components"
    metric_name      = "requests/duration"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 2000 # 2 seconds
  }

  action {
    action_group_id = azurerm_monitor_action_group.main.id
  }

  tags = var.tags
}

# Metric Alert for Function App availability
resource "azurerm_monitor_metric_alert" "function_availability" {
  name                = "${var.name_prefix}-function-availability"
  resource_group_name = var.resource_group_name
  scopes              = [azurerm_application_insights.main.id]
  description         = "Alert when function app availability drops below threshold"
  severity            = 1
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.Insights/components"
    metric_name      = "availabilityResults/availabilityPercentage"
    aggregation      = "Average"
    operator         = "LessThan"
    threshold        = 95
  }

  action {
    action_group_id = azurerm_monitor_action_group.main.id
  }

  tags = var.tags
}

# Diagnostic Settings for Application Insights
resource "azurerm_monitor_diagnostic_setting" "application_insights" {
  name                       = "${var.name_prefix}-ai-diagnostics"
  target_resource_id         = azurerm_application_insights.main.id
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id

  enabled_log {
    category = "AppAvailabilityResults"
  }

  enabled_log {
    category = "AppBrowserTimings"
  }

  enabled_log {
    category = "AppEvents"
  }

  enabled_log {
    category = "AppMetrics"
  }

  enabled_log {
    category = "AppDependencies"
  }

  enabled_log {
    category = "AppExceptions"
  }

  enabled_log {
    category = "AppPageViews"
  }

  enabled_log {
    category = "AppPerformanceCounters"
  }

  enabled_log {
    category = "AppRequests"
  }

  enabled_log {
    category = "AppSystemEvents"
  }

  enabled_log {
    category = "AppTraces"
  }

  metric {
    category = "AllMetrics"
    enabled  = true
  }
} 