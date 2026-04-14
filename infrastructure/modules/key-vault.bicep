targetScope = 'resourceGroup'

@description('Azure region for the Key Vault.')
param location string

@description('Name of the Key Vault.')
param keyVaultName string

@description('SKU for Key Vault.')
@allowed([
  'standard'
  'premium'
])
param skuName string = 'standard'

@description('Whether to enable soft delete.')
param enableSoftDelete bool = true

@description('Soft delete retention in days.')
param softDeleteRetentionInDays int = 7

@description('Whether to enable purge protection.')
param enablePurgeProtection bool = false

@description('Whether public network access is enabled.')
param publicNetworkAccess string = 'Enabled'

@description('Whether to create a private endpoint for Key Vault.')
param enablePrivateEndpoint bool = false

@description('Subnet resource ID for private endpoint.')
param privateEndpointSubnetId string = ''

@description('Name of the private endpoint.')
param privateEndpointName string = ''

@description('Object ID of the principal that should have secrets access.')
param secretsOfficerPrincipalId string = ''

resource kv 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: keyVaultName
  location: location
  properties: {
    sku: {
      family: 'A'
      name: skuName
    }
    tenantId: subscription().tenantId
    enableSoftDelete: enableSoftDelete
    softDeleteRetentionInDays: softDeleteRetentionInDays
    enablePurgeProtection: enablePurgeProtection ? true : null
    enableRbacAuthorization: true
    publicNetworkAccess: publicNetworkAccess
  }
}

resource secretsOfficerRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(secretsOfficerPrincipalId)) {
  name: guid(kv.id, secretsOfficerPrincipalId, '4633458b-17de-408a-b874-0445c86b69e6')
  scope: kv
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4633458b-17de-408a-b874-0445c86b69e6')
    principalId: secretsOfficerPrincipalId
    principalType: 'ServicePrincipal'
  }
}

resource kvPrivateEndpoint 'Microsoft.Network/privateEndpoints@2023-11-01' = if (enablePrivateEndpoint) {
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
          privateLinkServiceId: kv.id
          groupIds: [
            'vault'
          ]
        }
      }
    ]
  }
}

output keyVaultId string = kv.id
output keyVaultName string = kv.name
output keyVaultUri string = kv.properties.vaultUri
output privateEndpointId string = enablePrivateEndpoint ? kvPrivateEndpoint.id : ''
