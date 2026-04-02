targetScope = 'resourceGroup'

@description('Azure location for all dev resources.')
param location string = resourceGroup().location

@description('Workload name used for resource naming.')
param workloadName string = 'openmrs'

@description('Environment short name.')
param environmentName string = 'dev'

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

var logAnalyticsName = 'law-${workloadName}-${environmentName}'
var containerRegistryName = toLower(replace('acr${workloadName}${environmentName}', '-', ''))
var managedEnvironmentName = 'acae-${workloadName}-${environmentName}'
var containerAppName = 'aca-${workloadName}-${environmentName}'
var mysqlServerName = 'mysql-${workloadName}-${environmentName}'

module logAnalytics '../../modules/log-analytics.bicep' = {
  name: 'logAnalyticsDeploy'
  params: {
    location: location
    workspaceName: logAnalyticsName
    retentionInDays: 30
  }
}

module acr '../../modules/container-registry.bicep' = {
  name: 'acrDeploy'
  params: {
    location: location
    registryName: containerRegistryName
    skuName: 'Basic'
    publicNetworkAccess: 'Enabled'
    enablePrivateEndpoint: false
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
    skuName: 'Standard_B1ms'
    publicNetworkAccess: 'Enabled'
    enablePrivateEndpoint: false
    enableHighAvailability: false
    backupRetentionDays: 7
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
    externalIngress: true
    minReplicas: 0
    maxReplicas: 2
    cpu: '1.0'
    memory: '2Gi'
    enableVnetIntegration: false
    useWorkloadProfiles: false
  }
}

output containerAppUrl string = 'https://${containerApp.outputs.containerAppFqdn}'
output registryLoginServer string = acr.outputs.loginServer
output dbServerFqdn string = database.outputs.fqdn
