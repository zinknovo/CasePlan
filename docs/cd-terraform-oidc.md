# CD with Terraform + OIDC (AWS)

This project uses GitHub Actions to run Terraform plan/apply with AWS OIDC.

Workflow file:
- `.github/workflows/cd-terraform.yml`

## 1) What It Does

- On PR: runs Terraform `plan` (no apply).
- On push to `main`: runs `plan` then `apply` to `staging`.
- On manual trigger (`workflow_dispatch`): choose environment and whether to apply.

## 2) Required GitHub Configuration

### Secrets

- `AWS_ROLE_ARN`
- `DATASOURCE_PASSWORD`
- `OPENAI_API_KEY` (optional)
- `DEEPSEEK_API_KEY` (optional)

### Variables

- `AWS_REGION` (e.g. `us-east-2`)
- `PROJECT_NAME` (e.g. `caseplan`)
- `VPC_ID`
- `LAMBDA_SUBNET_IDS_JSON` (JSON array, e.g. `["subnet-a","subnet-b"]`)
- `ADDITIONAL_LAMBDA_SECURITY_GROUP_IDS_JSON` (optional, default `[]`)
- `RDS_SECURITY_GROUP_ID`
- `SQS_VPCE_SECURITY_GROUP_ID` (optional)
- `DATASOURCE_URL`
- `DATASOURCE_USERNAME`
- `CORS_ALLOW_ORIGINS_JSON` (optional, default `["*"]`)
- `LLM_PROVIDER` (optional, default `openai`)
- `TF_STATE_BUCKET`
- `TF_STATE_REGION`
- `TF_LOCK_TABLE`
- `TF_STATE_KEY` (optional; default `caseplan/<env>/terraform.tfstate`)

## 3) AWS OIDC Role Setup

Use an IAM role trusted by GitHub OIDC:

- Provider: `token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`
- Subject should be limited to this repo and branch/environment patterns.

Example trust condition (conceptual):

- `repo:zinknovo/CasePlan:ref:refs/heads/main`
- optionally your PR refs/environment subjects if needed

The role needs permissions for:

- Terraform-managed resources in `infra/terraform-replica`
- S3 state bucket access
- DynamoDB lock table access
- IAM/Lambda/API Gateway/SQS/CloudWatch actions used by this module

## 4) Notes

- Terraform state is remote (S3 + DynamoDB lock) in CI; this is required.
- The workflow builds `target/legal-caseplan-lambda.zip` via Maven before `plan`.
- `apply` is intentionally gated to:
  - push on `main`, or
  - manual dispatch with `apply=true`.
