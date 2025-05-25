# Authserver Migration Prompts - Usage Guide

This folder contains comprehensive AI prompts for migrating the AWS-based authserver to Azure using AI-assisted development tools.

## Prompt Files Overview

| File | Phase | Purpose | Primary AI Tool |
|------|-------|---------|-----------------|
| `00-migration-overview.prompt.md` | Overview | Complete migration summary and strategy | All Models |
| `01-discovery-analysis.prompt.md` | Discovery | Analyze AWS codebase and dependencies | Claude Sonnet 4 |
| `02-target-design.prompt.md` | Design | Design Azure architecture and mapping | Claude Opus 4 |
| `03-code-adaptation.prompt.md` | Development | Convert AWS SDK to Azure SDK | Cursor + Sonnet 4 |
| `04-infrastructure-terraform.prompt.md` | Infrastructure | CloudFormation to Terraform conversion | Claude Opus 4 |
| `05-ai-eval-gate.prompt.md` | Quality Assurance | Multi-LLM evaluation system | LangChain + All Models |
| `06-performance-optimization.prompt.md` | Optimization | Performance and cost optimization | Claude Sonnet 4 |
| `07-cutover-deployment.prompt.md` | Deployment | Blue-green cutover strategy | Live Cursor Chat |
| `08-post-migration-cleanup.prompt.md` | Cleanup | AWS decommissioning and finalization | Gemini 2.5 PRO |

## How to Use These Prompts

### 1. Sequential Execution
Execute the prompts in numerical order (01 → 08) as each phase builds upon the previous one:

```bash
# Phase 1: Discovery
# Use 01-discovery-analysis.prompt.md with Claude Sonnet 4

# Phase 2: Target Design  
# Use 02-target-design.prompt.md with Claude Opus 4

# Continue through all phases...
```

### 2. AI Tool Selection
Each prompt is optimized for specific AI models:

- **Claude Sonnet 4**: Code analysis, functional review, performance optimization
- **Claude Opus 4**: Architecture design, security review, infrastructure planning
- **Gemini 2.5 PRO**: Edge case analysis, documentation, tie-breaking
- **Cursor Agents**: Real-time code editing and patch suggestions
- **LangChain**: Orchestration and multi-model coordination

### 3. Prompt Customization
Before using each prompt:

1. **Replace Placeholders**: Update subscription IDs, resource names, and URLs
2. **Adjust Context**: Modify based on your specific AWS setup
3. **Set Parameters**: Configure thresholds and targets for your environment
4. **Review Requirements**: Ensure all prerequisites are met

### 4. Quality Gates
Each phase has specific success criteria that must be met before proceeding:

- **Phase 1**: Complete AWS dependency inventory
- **Phase 2**: Approved Azure architecture design
- **Phase 3**: All unit tests passing with Azure SDK
- **Phase 4**: Terraform infrastructure validated
- **Phase 5**: AI-Eval score ≥ 0.9
- **Phase 6**: Performance targets met
- **Phase 7**: Successful zero-downtime cutover
- **Phase 8**: AWS resources decommissioned

## AI Model Configuration

### Claude Sonnet 4 Setup
```bash
# Configure for code analysis and optimization
export ANTHROPIC_API_KEY="your-key"
export MODEL_TEMPERATURE=0.1  # Low temperature for precise code analysis
export MAX_TOKENS=4000
```

### Claude Opus 4 Setup
```bash
# Configure for architecture and infrastructure design
export ANTHROPIC_API_KEY="your-key"
export MODEL_TEMPERATURE=0.3  # Moderate temperature for creative design
export MAX_TOKENS=8000
```

### Gemini 2.5 PRO Setup
```bash
# Configure for edge cases and documentation
export GOOGLE_API_KEY="your-key"
export MODEL_TEMPERATURE=0.2
export MAX_TOKENS=6000
```

### Cursor Configuration
```json
{
  "cursor.ai.model": "claude-sonnet-4",
  "cursor.ai.temperature": 0.1,
  "cursor.ai.maxTokens": 4000,
  "cursor.ai.enablePatch": true,
  "langsmith.projectId": "authserver-migration"
}
```

## Environment Variables

Set these environment variables before starting:

```bash
# Azure Configuration
export AZURE_SUBSCRIPTION_ID="your-subscription-id"
export AZURE_TENANT_ID="your-tenant-id"
export AZURE_REGION_PRIMARY="westeurope"
export AZURE_REGION_SECONDARY="northeurope"

# AWS Configuration (for analysis phase)
export AWS_REGION="us-east-1"
export AWS_ACCOUNT_ID="your-account-id"

# AI Service Configuration
export ANTHROPIC_API_KEY="your-anthropic-key"
export GOOGLE_API_KEY="your-google-key"
export OPENAI_API_KEY="your-openai-key"

# Migration Configuration
export MIGRATION_BRANCH="authserver.azure"
export AI_EVAL_THRESHOLD="0.9"
export PERFORMANCE_TARGET_P95="200"
export COST_INCREASE_LIMIT="15"
```

## Best Practices

### 1. Prompt Engineering
- **Be Specific**: Include exact file paths, function names, and requirements
- **Provide Context**: Share relevant code snippets and architecture diagrams
- **Set Expectations**: Clearly state success criteria and output format
- **Iterate**: Refine prompts based on AI responses and results

### 2. Multi-Model Strategy
- **Primary Model**: Use the recommended model for each phase
- **Validation**: Cross-check critical decisions with secondary models
- **Tie-Breaking**: Use Gemini 2.5 PRO when Sonnet and Opus disagree
- **Consensus**: Require agreement on architectural decisions

### 3. Quality Assurance
- **AI-Eval Gate**: Implement the multi-LLM evaluation system early
- **Continuous Testing**: Run tests after each code change
- **Performance Monitoring**: Track metrics throughout migration
- **Security Validation**: Regular security scans and reviews

### 4. Risk Management
- **Incremental Changes**: Make small, testable changes
- **Rollback Plans**: Maintain ability to revert at each phase
- **Backup Strategy**: Preserve original AWS configuration
- **Monitoring**: Continuous monitoring during cutover

## Troubleshooting

### Common Issues

#### AI Model Responses
- **Hallucination**: Use multi-model validation and Giskard detection
- **Inconsistency**: Provide more specific context and examples
- **Rate Limits**: Implement backoff strategies and request queuing

#### Code Generation
- **Compilation Errors**: Validate generated code incrementally
- **Missing Dependencies**: Check and update import statements
- **Configuration Issues**: Verify environment variables and settings

#### Infrastructure
- **Terraform Errors**: Validate syntax and provider versions
- **Azure Permissions**: Ensure proper RBAC and service principal setup
- **Resource Conflicts**: Use unique naming conventions

### Getting Help

1. **Review Logs**: Check AI model responses and error messages
2. **Validate Inputs**: Ensure all required context is provided
3. **Test Incrementally**: Isolate issues by testing small changes
4. **Consult Documentation**: Reference Azure and Terraform docs
5. **Seek Review**: Have human experts validate critical decisions

## Success Metrics

Track these metrics throughout the migration:

### Technical Metrics
- **Functional Parity**: 100% test pass rate
- **Performance**: ≤ 200ms p95 response time
- **Availability**: 99.9% uptime SLA
- **Cost**: ≤ +15% vs AWS baseline

### AI-Assisted Development Metrics
- **AI-Eval Score**: ≥ 0.9 consistently
- **Code Review Efficiency**: 80% reduction in manual review time
- **Migration Velocity**: Accelerated delivery timeline
- **Quality**: Reduced defect rate

## Support and Feedback

For questions or issues with these prompts:

1. **Check Prerequisites**: Ensure all dependencies are installed
2. **Review Examples**: Study the provided code examples
3. **Test Incrementally**: Validate each step before proceeding
4. **Document Issues**: Record problems and solutions for future reference

Remember: These prompts are designed to work together as a cohesive migration strategy. Follow the sequence, validate outputs, and maintain quality gates throughout the process. 