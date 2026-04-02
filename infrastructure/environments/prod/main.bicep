targetScope = 'resourceGroup'

@description('Azure location for all production resources.')
param location string = resourceGroup().location

@description('Workload name used for resource naming.')
param workloadName string = 'openmrs'

@description('Environment short name.')
param environmentName string = 'prod'

@description('Container image repository name (without registry server).')
param imageRepository string = 'openmrs-core'

@description('Container image tag.')
param imageTag string = 'latest'

@description('Database admin username.')
param dbAdminUsername string = 'openmrsadmin'

@description('Database admin password.')
@secure()
param dbAdminPassword string

@description('Database name for OpenMRS.')
param dbName string = 'openmrs'

@description('Database engine selection.')
@allowed([
  'MySQL'
  'MariaDB'
])
param databaseEngine string = 'MySQL'

@description('Virtual network CIDR.')
param vnetAddressPrefix string = '10.0.0.0/16'

@description('ACA subnet CIDR.')
param acaSubnetPrefix string = '10.0.0.0/23'

@description('Application Gateway subnet CIDR.')
param appGatewaySubnetPrefix string = '10.0.2.0/24'

@description('Database subnet CIDR.')
param dbSubnetPrefix string = '10.0.3.0/24'

@description('Management subnet CIDR.')
param mgmtSubnetPrefix string = '10.0.4.0/24'

@description('Base64-encoded PFX certificate data for Application Gateway HTTPS listener.')
@secure()
param sslCertificateData string

@description('PFX certificate password for Application Gateway HTTPS listener.')
@secure()
param sslCertificatePassword string

var logAnalyticsName = 'law-${workloadName}-${environmentName}'
var containerRegistryName = toLower(replace('acr${workloadName}${environmentName}', '-', ''))
var managedEnvironmentName = 'acae-${workloadName}-${environmentName}'
var containerAppName = 'aca-${workloadName}-${environmentName}'
var mysqlServerName = 'mysql-${workloadName}-${environmentName}'
var vnetName = 'vnet-${workloadName}-${environmentName}'
var acaNsgName = 'nsg-${workloadName}-${environmentName}-aca'
var appgwNsgName = 'nsg-${workloadName}-${environmentName}-appgw'
var dbNsgName = 'nsg-${workloadName}-${environmentName}-db'
var mgmtNsgName = 'nsg-${workloadName}-${environmentName}-mgmt'
var acrPrivateEndpointName = 'pep-${workloadName}-${environmentName}-acr'
var dbPrivateEndpointName = 'pep-${workloadName}-${environmentName}-db'
var appGatewayName = 'agw-${workloadName}-${environmentName}'
var appGatewayPipName = 'pip-${workloadName}-${environmentName}-agw'

module vnet '../../modules/vnet.bicep' = {
  name: 'vnetDeploy'
  params: {
    location: location
    vnetName: vnetName
    vnetAddressPrefix: vnetAddressPrefix
    acaSubnetName: 'aca-subnet'
    acaSubnetPrefix: acaSubnetPrefix
    appGatewaySubnetName: 'appgw-subnet'
    appGatewaySubnetPrefix: appGatewaySubnetPrefix
    dbSubnetName: 'db-subnet'
    dbSubnetPrefix: dbSubnetPrefix
    mgmtSubnetName: 'mgmt-subnet'
    mgmtSubnetPrefix: mgmtSubnetPrefix
    acaNsgName: acaNsgName
    appGatewayNsgName: appgwNsgName
    dbNsgName: dbNsgName
    mgmtNsgName: mgmtNsgName
  }
}

module logAnalytics '../../modules/log-analytics.bicep' = {
  name: 'logAnalyticsDeploy'
  params: {
    location: location
    workspaceName: logAnalyticsName
    retentionInDays: 90
  }
}

module acr '../../modules/container-registry.bicep' = {
  name: 'acrDeploy'
  params: {
    location: location
    registryName: containerRegistryName
    skuName: 'Premium'
    publicNetworkAccess: 'Disabled'
    enablePrivateEndpoint: true
    privateEndpointSubnetId: vnet.outputs.mgmtSubnetId
    privateEndpointName: acrPrivateEndpointName
  }
}

module database '../../modules/database.bicep' = {
  name: 'databaseDeploy'
  params: {
    location: location
    serverName: mysqlServerName
    databaseEngine: databaseEngine
    databaseName: dbName
    administratorLogin: dbAdminUsername
    administratorPassword: dbAdminPassword
    skuName: 'Standard_D2ds_v4'
    publicNetworkAccess: 'Disabled'
    enablePrivateEndpoint: true
    privateEndpointSubnetId: vnet.outputs.mgmtSubnetId
    privateEndpointName: dbPrivateEndpointName
    enableHighAvailability: true
    backupRetentionDays: 14
  }
}

module containerApp '../../modules/container-app.bicep' = {
  name: 'containerAppDeploy'
  params: {
    location: location
    managedEnvironmentName: managedEnvironmentName
    containerAppName: containerAppName
    containerImage: '${acr.outputs.loginServer}/${imageRepository}:${imageTag}'
    registryServer: acr.outputs.loginServer
    registryName: acr.outputs.registryName
    logAnalyticsWorkspaceName: logAnalytics.outputs.workspaceName
    targetPort: 8080
    externalIngress: false
    minReplicas: 2
    maxReplicas: 5
    cpu: '1.0'
    memory: '2Gi'
    enableVnetIntegration: true
    infrastructureSubnetId: vnet.outputs.acaSubnetId
    useWorkloadProfiles: true
  }
}

module appGateway '../../modules/app-gateway.bicep' = {
  name: 'appGatewayDeploy'
  params: {
    location: location
    appGatewayName: appGatewayName
    publicIpName: appGatewayPipName
    appGatewaySubnetId: vnet.outputs.appGatewaySubnetId
    backendHostName: containerApp.outputs.containerAppFqdn
    backendPort: 8080
    sslCertificateData: sslCertificateData
    sslCertificatePassword: sslCertificatePassword
    wafMode: 'Prevention'
    minCapacity: 1
    maxCapacity: 3
  }
}

output applicationGatewayPublicIp string = appGateway.outputs.publicIpAddress
output registryLoginServer string = acr.outputs.loginServer
output dbServerFqdn string = database.outputs.fqdn
