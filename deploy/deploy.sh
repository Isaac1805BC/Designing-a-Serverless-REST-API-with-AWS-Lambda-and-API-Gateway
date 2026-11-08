#!/usr/bin/env bash
# Deploys BOTH architectures of the Orders API with the AWS CLI.
#
#   Architecture A (non-proxy / mapping templates):  stage "a"  -> 3 Lambdas (direct.*)
#   Architecture B (Lambda proxy + HTTP adapter):    stage "b"  -> 1 Lambda  (proxy.OrdersProxyHandler)
#
# Prerequisites: `mvn package`, `aws configure` done, and an existing Lambda execution role
# (AWSLambdaBasicExecutionRole). Usage:
#   ROLE_ARN=arn:aws:iam::123456789012:role/orders-lambda-role REGION=us-east-1 ./deploy/deploy.sh
#
# NOTE: written for the AWS CLI v2 REST API (apigateway) commands. Re-running creates NEW
# functions/APIs; delete old ones first or adapt the script to update-function-code.
set -euo pipefail

: "${ROLE_ARN:?Set ROLE_ARN to the Lambda execution role ARN}"
REGION="${REGION:-us-east-1}"
JAR="fileb://target/orders-api-1.0.0.jar"
PKG="edu.eci.tdse.orders.lambda"
T="file://deploy/templates"
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)

create_fn() { # name handler
  aws lambda create-function --region "$REGION" --function-name "$1" --runtime java17 \
    --handler "$2" --role "$ROLE_ARN" --zip-file "$JAR" --timeout 15 --memory-size 512 \
    --query FunctionArn --output text
}
lambda_uri() { echo "arn:aws:apigateway:$REGION:lambda:path/2015-03-31/functions/$1/invocations"; }
allow_apigw() { # function-name api-id
  aws lambda add-permission --region "$REGION" --function-name "$1" --statement-id "apigw-$2-$RANDOM" \
    --action lambda:InvokeFunction --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:$REGION:$ACCOUNT:$2/*/*/*" >/dev/null
}

echo "== Lambdas =="
CREATE_ARN=$(create_fn orders-create "$PKG.direct.CreateOrderHandler::handleRequest")
GET_ARN=$(create_fn orders-get "$PKG.direct.GetOrderHandler::handleRequest")
FIND_ARN=$(create_fn orders-find "$PKG.direct.FindOrdersHandler::handleRequest")
PROXY_ARN=$(create_fn orders-proxy "$PKG.proxy.OrdersProxyHandler::handleRequest")

# ---------------------------------------------------------------- Architecture A
echo "== API Gateway: Architecture A (non-proxy) =="
API_A=$(aws apigateway create-rest-api --region "$REGION" --name orders-api-a --query id --output text)
ROOT_A=$(aws apigateway get-resources --region "$REGION" --rest-api-id "$API_A" --query 'items[0].id' --output text)
ORDERS_A=$(aws apigateway create-resource --region "$REGION" --rest-api-id "$API_A" --parent-id "$ROOT_A" --path-part orders --query id --output text)
ORDER_A=$(aws apigateway create-resource --region "$REGION" --rest-api-id "$API_A" --parent-id "$ORDERS_A" --path-part '{id}' --query id --output text)

# method_a <resource-id> <http-method> <lambda-arn> <request-template-file> <success-code> [extra put-method args]
method_a() {
  local res=$1 verb=$2 arn=$3 tpl=$4 ok=$5; shift 5
  aws apigateway put-method --region "$REGION" --rest-api-id "$API_A" --resource-id "$res" \
    --http-method "$verb" --authorization-type NONE "$@" >/dev/null
  aws apigateway put-integration --region "$REGION" --rest-api-id "$API_A" --resource-id "$res" \
    --http-method "$verb" --type AWS --integration-http-method POST --uri "$(lambda_uri "$arn")" \
    --passthrough-behavior NEVER --request-templates "$T/$tpl" >/dev/null
  for code in "$ok" 400 404 500; do
    aws apigateway put-method-response --region "$REGION" --rest-api-id "$API_A" --resource-id "$res" \
      --http-method "$verb" --status-code "$code" --response-models '{"application/json":"Empty"}' >/dev/null
  done
  # Success = default (no selection pattern). Errors are matched on the "[NNN]" tag in errorMessage.
  aws apigateway put-integration-response --region "$REGION" --rest-api-id "$API_A" --resource-id "$res" \
    --http-method "$verb" --status-code "$ok" >/dev/null
  for code in 400 404 500; do
    aws apigateway put-integration-response --region "$REGION" --rest-api-id "$API_A" --resource-id "$res" \
      --http-method "$verb" --status-code "$code" --selection-pattern "^\\[$code\\].*" \
      --response-templates "$T/response-error.json" >/dev/null
  done
}
method_a "$ORDER_A"  GET  "$GET_ARN"    request-get.json    200 --request-parameters method.request.path.id=true
method_a "$ORDERS_A" GET  "$FIND_ARN"   request-find.json   200 --request-parameters method.request.querystring.customerId=true
method_a "$ORDERS_A" POST "$CREATE_ARN" request-create.json 201
for fn in orders-create orders-get orders-find; do allow_apigw "$fn" "$API_A"; done
aws apigateway create-deployment --region "$REGION" --rest-api-id "$API_A" --stage-name a >/dev/null
echo "Architecture A: https://$API_A.execute-api.$REGION.amazonaws.com/a"

# ---------------------------------------------------------------- Architecture B
echo "== API Gateway: Architecture B (Lambda proxy) =="
API_B=$(aws apigateway create-rest-api --region "$REGION" --name orders-api-b --query id --output text)
ROOT_B=$(aws apigateway get-resources --region "$REGION" --rest-api-id "$API_B" --query 'items[0].id' --output text)
ORDERS_B=$(aws apigateway create-resource --region "$REGION" --rest-api-id "$API_B" --parent-id "$ROOT_B" --path-part orders --query id --output text)
ORDER_B=$(aws apigateway create-resource --region "$REGION" --rest-api-id "$API_B" --parent-id "$ORDERS_B" --path-part '{id}' --query id --output text)

proxy_method() { # resource-id http-method
  aws apigateway put-method --region "$REGION" --rest-api-id "$API_B" --resource-id "$1" \
    --http-method "$2" --authorization-type NONE >/dev/null
  aws apigateway put-integration --region "$REGION" --rest-api-id "$API_B" --resource-id "$1" \
    --http-method "$2" --type AWS_PROXY --integration-http-method POST --uri "$(lambda_uri "$PROXY_ARN")" >/dev/null
}
proxy_method "$ORDERS_B" GET
proxy_method "$ORDERS_B" POST
proxy_method "$ORDER_B" GET
# proxy_method "$ORDER_B" DELETE    # <- added in the final challenge
allow_apigw orders-proxy "$API_B"
aws apigateway create-deployment --region "$REGION" --rest-api-id "$API_B" --stage-name b >/dev/null
echo "Architecture B: https://$API_B.execute-api.$REGION.amazonaws.com/b"
