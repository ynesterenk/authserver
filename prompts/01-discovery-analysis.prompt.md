# Discovery & Analysis Phase - AWS Authserver Migration

## Context
You are assisting with migrating an AWS-based OAuth 2.0 authentication service (authserver) to Azure. This is Phase 1 of the migration focusing on discovery and analysis.

## Current Architecture (AWS)
- **ServerLambda/ProxyRequestHandler**: Issues OAuth 2.0 tokens
- **JwtAuthorizerLambda**: Validates JWTs, builds IAM policies  
- **BasicAuthenticatorLambda**: HTTP Basic Auth fallback for legacy clients
- **Amazon Cognito User Pool**: User store & JWT issuer
- **CloudFormation stacks**: Infrastructure as Code

## Your Tasks

### 1. Codebase Analysis
Analyze the Java 11 codebase and identify:
- All AWS SDK imports and dependencies (`software.amazon.*`)
- Lambda handler entry points and their signatures
- Environment variable usage
- AWS service integrations (Cognito, IAM, CloudWatch)
- External API calls and endpoints
- Configuration patterns

### 2. Call Graph Mapping
Create a comprehensive call graph showing:
- Inter-service dependencies between the three Lambda functions
- External service calls (Cognito User Pool, IAM)
- Data flow for OAuth token issuance and validation
- Error handling and fallback mechanisms

### 3. AWS-Specific Code Identification
Tag and categorize all AWS-specific code:
- **High Priority**: Core AWS SDK calls that must be replaced
- **Medium Priority**: AWS-specific configurations and environment variables
- **Low Priority**: AWS-specific logging and monitoring code

### 4. Security & Compliance Analysis
Document current security implementations:
- JWT signing algorithms and key management
- IAM policy structures and RBAC patterns
- Secret management and environment variable handling
- TLS/SSL configurations
- PCI-DSS and SOX compliance touchpoints

## Output Format
Provide your analysis in the following structure:

```markdown
## AWS Dependencies Analysis
### Critical AWS SDK Usage
- [List all critical AWS SDK imports and their usage contexts]

### Lambda Handler Signatures
- [Document all Lambda entry points with their signatures]

### Environment Variables
- [List all environment variables and their purposes]

## Call Graph
[Provide a textual representation of the service call graph]

## Migration Complexity Assessment
### High Risk Areas
- [Areas requiring careful migration attention]

### Low Risk Areas  
- [Areas that can be migrated with minimal changes]

## Security Considerations
- [Current security implementations that must be preserved]
- [Compliance requirements to maintain]
```

## Success Criteria
- Complete inventory of all AWS SDK usage
- Clear understanding of service dependencies
- Identification of all configuration touchpoints
- Security and compliance requirements documented
- Migration complexity assessment completed

## Focus Areas
Pay special attention to:
- OAuth 2.0 token issuance flows (`/oauth/token`)
- JWT validation logic (`/oauth/check_token`)
- Basic Auth fallback mechanisms
- IAM policy generation patterns
- Cognito User Pool integrations 