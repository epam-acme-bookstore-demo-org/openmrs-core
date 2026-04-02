targetScope = 'resourceGroup'

@description('Azure region for the Azure Container Registry.')
param location string

@description('Name of the Azure Container Registry.')
param registryName string

@description('SKU for Azure Container Registry.')
@allowed([
  'Basic'
  'Standard'
  'Premium'
])
param skuName string = 'Basic'

@description('Whether public network access is enabled for ACR.')
param publicNetworkAccess string = 'Enabled'

@description('Whether to create a private endpoint for ACR.')
param enablePrivateEndpoint bool = false

@description('Resource ID of subnet used for ACR private endpoint when enabled.')
param privateEndpointSubnetId string = ''

@description('Name of private endpoint for ACR when enabled.')
param privateEndpointName string = ''

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: registryName
  location: location
  sku: {
    name: skuName
  }
  properties: {
    adminUserEnabled: false
    publicNetworkAccess: publicNetworkAccess
    networkRuleBypassOptions: 'AzureServices'
    policies: {
      quarantinePolicy: {
        status: 'disabled'
      }
      trustPolicy: {
        type: 'Notary'
        status: 'disabled'
      }
      retentionPolicy: {
        days: 7
        status: 'enabled'
      }
    }
  }
}

resource acrPrivateEndpoint 'Microsoft.Network/privateEndpoints@2023-11-01' = if (enablePrivateEndpoint) {
  name: privateEndpointName
  location: location
  properties: {
    subnet: {
      id: privateEndpointSubnetId
    }
    privateLinkServiceConnections: [
      {
        name: '${privateEndpointName}-conn'
        properties: {
          privateLinkServiceId: acr.id
          groupIds: [
            'registry'
          ]
        }
      }
    ]
  }
}

output registryId string = acr.id
output loginServer string = acr.properties.loginServer
output registryName string = acr.name
output privateEndpointId string = enablePrivateEndpoint ? acrPrivateEndpoint.id : ''
