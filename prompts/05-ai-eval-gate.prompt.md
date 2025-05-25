# AI-Eval Gate Phase - Automated Quality Assurance

## Context
You are implementing the AI-Eval gate system for the authserver migration. This is Phase 5 focusing on creating an automated quality assurance system using multiple LLMs to validate migration quality before allowing code to merge.

## AI-Eval Requirements (from PRD)
- **Target Score**: ≥ 0.9 to pass CI gate and allow PR merge
- **Multi-LLM Voting**: Claude Sonnet 4 + Claude Opus 4 vote; Gemini 2.5 PRO as tie-breaker
- **LangChain Orchestrator**: Fetches k-nearest code chunks for context
- **OpenAI-Evals Replacement**: Custom evaluation framework
- **Hallucination Detection**: Giskard hallucination score < 0.1 required

## Your Tasks

### 1. AI-Eval Framework Architecture
Design the evaluation system:
- **Orchestrator**: LangChain-based system to coordinate evaluations
- **Code Retrieval**: Vector similarity search for relevant code chunks
- **Multi-LLM Voting**: Parallel evaluation by multiple models
- **Scoring System**: Weighted scoring across different criteria
- **CI Integration**: GitHub Actions or Azure Pipelines integration

### 2. Evaluation Criteria
Define scoring criteria for migration quality:

#### Functional Parity (Weight: 30%)
- API endpoint compatibility (`/oauth/token`, `/oauth/check_token`)
- JSON response format matching
- HTTP status code consistency
- Error message preservation
- OAuth 2.0 flow compliance

#### Security Compliance (Weight: 25%)
- JWT signature validation (RS256)
- Secret management (Key Vault integration)
- RBAC implementation correctness
- TLS/SSL configuration
- PCI-DSS and SOX alignment

#### Code Quality (Weight: 20%)
- AWS SDK removal completeness
- Azure SDK integration correctness
- Error handling preservation
- Logging and monitoring migration
- Code structure and maintainability

#### Performance Considerations (Weight: 15%)
- Cold start optimization
- Memory allocation appropriateness
- Timeout configuration
- Caching strategies
- Resource utilization

#### Infrastructure Compliance (Weight: 10%)
- Terraform best practices
- Resource naming conventions
- Multi-region setup
- Backup and disaster recovery
- Cost optimization

### 3. LLM Evaluation Prompts
Create specific prompts for each LLM evaluator:

#### Claude Sonnet 4 - Functional Analysis
```
Analyze the migrated authserver code for functional parity with AWS version.

Focus on:
- OAuth 2.0 token issuance flow correctness
- JWT validation logic preservation
- API response format consistency
- Error handling completeness

Score: 0.0-1.0 based on functional equivalence.
```

#### Claude Opus 4 - Security & Architecture Review
```
Review the Azure migration for security and architectural compliance.

Evaluate:
- Security implementation (JWT signing, secret management)
- Azure best practices adherence
- RBAC and access control correctness
- Compliance with PCI-DSS and SOX requirements

Score: 0.0-1.0 based on security and architecture quality.
```

#### Gemini 2.5 PRO - Edge Case & Integration Analysis
```
Analyze edge cases and integration aspects of the migration.

Assess:
- Error scenarios and exception handling
- Integration with Azure services
- Performance optimization opportunities
- Potential migration risks

Score: 0.0-1.0 based on robustness and integration quality.
```

### 4. Code Retrieval System
Implement vector-based code retrieval:
- **Embedding Model**: Use text-embedding-ada-002 or similar
- **Vector Store**: Pinecone, Weaviate, or Azure Cognitive Search
- **Chunk Strategy**: Function-level and class-level chunking
- **Context Window**: Retrieve top-k relevant chunks for each evaluation

### 5. Scoring Algorithm
Design the weighted scoring system:

```python
def calculate_ai_eval_score(evaluations):
    weights = {
        'functional_parity': 0.30,
        'security_compliance': 0.25,
        'code_quality': 0.20,
        'performance': 0.15,
        'infrastructure': 0.10
    }
    
    # Multi-LLM voting with tie-breaker
    sonnet_score = evaluations['claude_sonnet_4']
    opus_score = evaluations['claude_opus_4']
    gemini_score = evaluations['gemini_2_5_pro']
    
    # Weighted average with tie-breaker logic
    if abs(sonnet_score - opus_score) < 0.1:
        final_score = (sonnet_score + opus_score) / 2
    else:
        final_score = gemini_score  # Tie-breaker
    
    return final_score
```

### 6. Hallucination Detection
Implement Giskard-based hallucination detection:
- **Fact Verification**: Cross-reference claims with source code
- **Consistency Checking**: Ensure LLM outputs align with actual code
- **Confidence Scoring**: Measure LLM confidence in evaluations
- **Threshold**: Reject evaluations with hallucination score ≥ 0.1

### 7. CI/CD Integration
Create pipeline integration:

#### GitHub Actions Workflow
```yaml
name: AI-Eval Gate
on:
  pull_request:
    branches: [main]

jobs:
  ai-eval:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run AI-Eval
        run: |
          python ai_eval/orchestrator.py \
            --pr-number ${{ github.event.number }} \
            --threshold 0.9
      - name: Post Results
        uses: actions/github-script@v6
        with:
          script: |
            // Post AI-Eval results as PR comment
```

#### Azure Pipelines Integration
```yaml
trigger:
  branches:
    include:
      - main

stages:
- stage: AIEval
  displayName: 'AI Evaluation Gate'
  jobs:
  - job: RunAIEval
    displayName: 'Run AI-Eval'
    steps:
    - task: PythonScript@0
      inputs:
        scriptSource: 'filePath'
        scriptPath: 'ai_eval/orchestrator.py'
        arguments: '--threshold 0.9'
```

### 8. Evaluation Reports
Generate comprehensive reports:
- **Score Breakdown**: Per-criteria scoring details
- **LLM Consensus**: Agreement/disagreement analysis
- **Risk Assessment**: Identified migration risks
- **Recommendations**: Specific improvement suggestions
- **Trend Analysis**: Score evolution over time

## Output Format
Provide your AI-Eval implementation in the following structure:

```markdown
## AI-Eval Framework
### Architecture Overview
- [System design and component interaction]

### Evaluation Criteria
- [Detailed scoring criteria with weights]

### LLM Integration
- [How each LLM is used and their specific roles]

### Code Retrieval System
- [Vector search implementation details]

### Scoring Algorithm
```python
[Complete scoring algorithm implementation]
```

### CI/CD Integration
```yaml
[Pipeline configuration files]
```

### Hallucination Detection
- [Giskard integration and thresholds]

### Reporting System
- [Report generation and visualization]
```

## Success Criteria
- AI-Eval system integrated into CI/CD pipeline
- Multi-LLM voting system operational
- Hallucination detection below 0.1 threshold
- Scoring algorithm produces reliable results
- Reports provide actionable feedback
- System blocks low-quality migrations (score < 0.9)
- Performance allows for reasonable CI/CD cycle times

## Key Requirements
- **Reliability**: Consistent scoring across runs
- **Performance**: Evaluation completes within 10 minutes
- **Accuracy**: High correlation with manual code review quality
- **Transparency**: Clear explanation of scoring decisions
- **Maintainability**: Easy to update criteria and thresholds

## Risk Mitigation
- **LLM Availability**: Fallback to single LLM if others unavailable
- **Rate Limiting**: Implement backoff strategies for API calls
- **Cost Control**: Monitor and limit LLM API usage
- **False Positives**: Manual override capability for edge cases
- **Security**: Secure handling of code and API keys

## Testing Strategy
- **Unit Tests**: Test individual evaluation components
- **Integration Tests**: End-to-end evaluation pipeline
- **Benchmark Tests**: Known good/bad migration examples
- **Performance Tests**: Evaluation speed and resource usage
- **Accuracy Tests**: Correlation with human expert reviews 