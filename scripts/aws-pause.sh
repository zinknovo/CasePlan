#!/bin/bash
# 暂停 CasePlan AWS 资源：创建 RDS 快照、停止 RDS、删除 NAT 网关
# 暂停后每月约省 $45，仅 RDS 存储 ~$2-3/月

set -euo pipefail

RDS_ID="dev-caseplan"
NAT_ID="nat-0f02c9ccb9e8601ce"
SNAPSHOT_ID="dev-caseplan-pause-$(date +%Y%m%d)"

echo "=== 1. 创建 RDS 快照（备份，用于恢复）==="
aws rds create-db-snapshot \
  --db-instance-identifier "$RDS_ID" \
  --db-snapshot-identifier "$SNAPSHOT_ID"
echo "快照创建中，等待完成（约 2-5 分钟）..."
aws rds wait db-snapshot-available --db-snapshot-identifier "$SNAPSHOT_ID"
echo "快照完成: $SNAPSHOT_ID"

echo ""
echo "=== 2. 停止 RDS ==="
aws rds stop-db-instance --db-instance-identifier "$RDS_ID"
echo "RDS 停止中..."

echo ""
echo "=== 3. 删除 NAT 网关 ==="
aws ec2 delete-nat-gateway --nat-gateway-id "$NAT_ID"
echo "NAT 网关已删除（关联的 EIP 将自动释放）"

echo ""
echo "=== 暂停完成 ==="
echo "- RDS 已停止，仅收取存储费 ~\$2-3/月"
echo "- NAT 网关已删除，节省 ~\$32/月"
echo ""
echo "恢复时运行: ./scripts/aws-resume.sh"
