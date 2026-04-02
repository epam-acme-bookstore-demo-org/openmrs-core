targetScope = 'resourceGroup'

@description('Azure region for the Log Analytics workspace.')
param location string

@description('Name of the Log Analytics workspace.')
param workspaceName string

@description('Retention in days for log data.')
param retentionInDays int = 30

resource workspace 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: workspaceName
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: retentionInDays
  }
}

output workspaceId string = workspace.id
output workspaceName string = workspace.name
output customerId string = workspace.properties.customerId
