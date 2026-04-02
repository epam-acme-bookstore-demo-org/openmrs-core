#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 --env dev|prod --resource-group <rg-name> [--location <azure-region>]"
  exit 1
}

ENVIRONMENT=""
RESOURCE_GROUP=""
LOCATION="eastus"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      ENVIRONMENT="${2:-}"
      shift 2
      ;;
    --resource-group)
      RESOURCE_GROUP="${2:-}"
      shift 2
      ;;
    --location)
      LOCATION="${2:-}"
      shift 2
      ;;
    *)
      usage
      ;;
  esac
done

if [[ -z "$ENVIRONMENT" || -z "$RESOURCE_GROUP" ]]; then
  usage
fi

if [[ "$ENVIRONMENT" != "dev" && "$ENVIRONMENT" != "prod" ]]; then
  echo "Error: --env must be 'dev' or 'prod'"
  exit 1
fi

if [[ -z "${OPENMRS_DB_ADMIN_PASSWORD:-}" ]]; then
  echo "Error: OPENMRS_DB_ADMIN_PASSWORD environment variable is required."
  exit 1
fi

if [[ "$ENVIRONMENT" == "prod" ]]; then
  if [[ -z "${OPENMRS_APPGW_SSL_CERT_BASE64:-}" || -z "${OPENMRS_APPGW_SSL_CERT_PASSWORD:-}" ]]; then
    echo "Error: OPENMRS_APPGW_SSL_CERT_BASE64 and OPENMRS_APPGW_SSL_CERT_PASSWORD are required for prod."
    exit 1
  fi
fi

TEMPLATE_FILE="infrastructure/environments/${ENVIRONMENT}/main.bicep"
PARAM_FILE="infrastructure/environments/${ENVIRONMENT}/main.bicepparam"
DEPLOYMENT_NAME="openmrs-${ENVIRONMENT}-$(date +%Y%m%d%H%M%S)"

echo "Ensuring resource group exists: ${RESOURCE_GROUP} (${LOCATION})"
az group create \
  --name "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --output table

echo "Running preview (what-if)..."
if [[ "$ENVIRONMENT" == "prod" ]]; then
  az deployment group what-if \
    --resource-group "$RESOURCE_GROUP" \
    --name "${DEPLOYMENT_NAME}-whatif" \
    --template-file "$TEMPLATE_FILE" \
    --parameters "$PARAM_FILE" \
    --parameters dbAdminPassword="$OPENMRS_DB_ADMIN_PASSWORD" \
    --parameters sslCertificateData="$OPENMRS_APPGW_SSL_CERT_BASE64" \
    --parameters sslCertificatePassword="$OPENMRS_APPGW_SSL_CERT_PASSWORD"
else
  az deployment group what-if \
    --resource-group "$RESOURCE_GROUP" \
    --name "${DEPLOYMENT_NAME}-whatif" \
    --template-file "$TEMPLATE_FILE" \
    --parameters "$PARAM_FILE" \
    --parameters dbAdminPassword="$OPENMRS_DB_ADMIN_PASSWORD"
fi

read -r -p "Proceed with deployment to ${ENVIRONMENT}? (yes/no): " CONFIRM
if [[ "$CONFIRM" != "yes" ]]; then
  echo "Deployment cancelled."
  exit 0
fi

echo "Applying deployment..."
if [[ "$ENVIRONMENT" == "prod" ]]; then
  az deployment group create \
    --resource-group "$RESOURCE_GROUP" \
    --name "$DEPLOYMENT_NAME" \
    --template-file "$TEMPLATE_FILE" \
    --parameters "$PARAM_FILE" \
    --parameters dbAdminPassword="$OPENMRS_DB_ADMIN_PASSWORD" \
    --parameters sslCertificateData="$OPENMRS_APPGW_SSL_CERT_BASE64" \
    --parameters sslCertificatePassword="$OPENMRS_APPGW_SSL_CERT_PASSWORD" \
    --output table
else
  az deployment group create \
    --resource-group "$RESOURCE_GROUP" \
    --name "$DEPLOYMENT_NAME" \
    --template-file "$TEMPLATE_FILE" \
    --parameters "$PARAM_FILE" \
    --parameters dbAdminPassword="$OPENMRS_DB_ADMIN_PASSWORD" \
    --output table
fi

echo "Deployment complete."
