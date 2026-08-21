#!/bin/bash
# 恢复 CasePlan AWS 资源：创建 NAT 网关、更新路由、启动 RDS

set -euo pipefail

RDS_ID="dev-caseplan"
NAT_SUBNET_ID="subnet-05acfdd7656dbcebe"   # NAT 所在子网（公网）
LAMBDA_ROUTE_TABLE_ID="rtb-04e7cbeec63ba9f19"  # Lambda 子网的路由表

echo "=== 1. 分配新 Elastic IP ==="
ALLOC_ID=$(aws ec2 allocate-address --domain vpc --query 'AllocationId' --output text)
echo "EIP 分配: $ALLOC_ID"

echo ""
echo "=== 2. 创建 NAT 网关 ==="
NAT_ID=$(aws ec2 create-nat-gateway \
  --subnet-id "$NAT_SUBNET_ID" \
  --allocation-id "$ALLOC_ID" \
  --query 'NatGateway.NatGatewayId' --output text)
echo "NAT 创建中: $NAT_ID"
echo "等待 NAT 可用（约 1-2 分钟）..."
aws ec2 wait nat-gateway-available --nat-gateway-ids "$NAT_ID"
echo "NAT 已就绪"

echo ""
echo "=== 3. 更新路由表（0.0.0.0/0 -> NAT）==="
# 先删除可能存在的旧 0.0.0.0/0 路由（目标可能已失效）
aws ec2 describe-route-tables --route-table-ids "$LAMBDA_ROUTE_TABLE_ID" \
  --query 'RouteTables[0].Routes[?DestinationCidrBlock==`0.0.0.0/0`].RouteTableId' --output text 2>/dev/null | head -1
# 创建新路由
aws ec2 delete-route --route-table-id "$LAMBDA_ROUTE_TABLE_ID" --destination-cidr-block 0.0.0.0/0 2>/dev/null || true
aws ec2 create-route \
  --route-table-id "$LAMBDA_ROUTE_TABLE_ID" \
  --destination-cidr-block 0.0.0.0/0 \
  --nat-gateway-id "$NAT_ID"
echo "路由已更新"

echo ""
echo "=== 4. 启动 RDS ==="
aws rds start-db-instance --db-instance-identifier "$RDS_ID"
echo "RDS 启动中，等待可用（约 2-5 分钟）..."
aws rds wait db-instance-available --db-instance-identifier "$RDS_ID"
echo "RDS 已就绪"

echo ""
echo "=== 恢复完成 ==="
echo "CasePlan 服务可正常使用"
