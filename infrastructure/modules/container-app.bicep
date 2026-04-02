targetScope = 'resourceGroup'

@description('Azure region for Container Apps resources.')
param location string

@description('Name of the Container Apps managed environment.')
param managedEnvironmentName string

@description('Name of the Container App.')
param containerAppName string

@description('Container image reference for OpenMRS app.')
param containerImage string

@description('Container registry login server.')
param registryServer string

@description('Container registry name used for AcrPull role assignment.')
param registryName string

@description('Name of the Log Analytics workspace.')
param logAnalyticsWorkspaceName string

@description('Target port exposed by OpenMRS container.')
param targetPort int = 8080

@description('Whether ingress is externally accessible.')
param externalIngress bool = true

@description('Minimum number of replicas.')
param minReplicas int = 0

@description('Maximum number of replicas.')
param maxReplicas int = 2

@description('CPU cores per replica.')
param cpu string = '1.0'

@description('Memory per replica.')
param memory string = '2Gi'

@description('Whether to integrate ACA environment with VNET.')
param enableVnetIntegration bool = false

@description('Infrastructure subnet resource ID for ACA environment when VNET integration is enabled.')
param infrastructureSubnetId string = ''

@description('Whether to use workload profiles in ACA environment.')
param useWorkloadProfiles bool = false

var acrPullRoleDefinitionId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = {
  name: registryName
}

resource logAnalyticsWorkspace 'Microsoft.OperationalInsights/workspaces@2022-10-01' existing = {
  name: logAnalyticsWorkspaceName
}

resource managedEnvironment 'Microsoft.App/managedEnvironments@2023-05-01' = {
  name: managedEnvironmentName
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalyticsWorkspace.properties.customerId
        sharedKey: logAnalyticsWorkspace.listKeys().primarySharedKey
      }
    }
    vnetConfiguration: enableVnetIntegration
      ? {
          internal: true
          infrastructureSubnetId: infrastructureSubnetId
        }
      : null
    workloadProfiles: useWorkloadProfiles
      ? [
          {
            name: 'Consumption'
            workloadProfileType: 'Consumption'
            minimumCount: 1
            maximumCount: 3
          }
        ]
      : []
  }
}

resource containerApp 'Microsoft.App/containerApps@2023-05-01' = {
  name: containerAppName
  location: location
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    managedEnvironmentId: managedEnvironment.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: externalIngress
        targetPort: targetPort
        allowInsecure: false
        transport: 'auto'
      }
      registries: [
        {
          server: registryServer
          identity: 'system'
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'openmrs'
          image: containerImage
          resources: {
            cpu: json(cpu)
            memory: memory
          }
          probes: [
            {
              type: 'Liveness'
              httpGet: {
                path: '/openmrs/health/alive'
                port: targetPort
              }
              initialDelaySeconds: 30
              periodSeconds: 10
            }
            {
              type: 'Readiness'
              httpGet: {
                path: '/openmrs/health/alive'
                port: targetPort
              }
              initialDelaySeconds: 20
              periodSeconds: 10
            }
          ]
        }
      ]
      scale: {
        minReplicas: minReplicas
        maxReplicas: maxReplicas
      }
    }
  }
}

resource acrPullAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, containerApp.id, 'acrpull')
  scope: acr
  properties: {
    principalId: containerApp.identity.principalId
    roleDefinitionId: acrPullRoleDefinitionId
    principalType: 'ServicePrincipal'
  }
}

output managedEnvironmentId string = managedEnvironment.id
output containerAppId string = containerApp.id
output containerAppFqdn string = contains(containerApp.properties.configuration.ingress, 'fqdn') ? containerApp.properties.configuration.ingress.fqdn : ''
output containerAppPrincipalId string = containerApp.identity.principalId
