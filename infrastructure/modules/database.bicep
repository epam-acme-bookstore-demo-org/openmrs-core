targetScope = 'resourceGroup'

@description('Azure region for the database server.')
param location string

@description('Name of the PostgreSQL Flexible Server.')
param serverName string

@description('Database engine type.')
@allowed([
  'PostgreSQL'
])
param databaseEngine string = 'PostgreSQL'

@description('Name of the application database.')
param databaseName string = 'openmrs'

@description('Admin username for PostgreSQL Flexible Server.')
param administratorLogin string = 'openmrsadmin'

@description('Admin password for PostgreSQL Flexible Server.')
@secure()
param administratorPassword string

@description('SKU name for PostgreSQL Flexible Server.')
param skuName string = 'Standard_B1ms'

@description('Storage size in GB for the database server.')
param storageSizeGB int = 32

@description('Whether public network access is enabled.')
param publicNetworkAccess string = 'Enabled'

@description('Whether to create private endpoint for database.')
param enablePrivateEndpoint bool = false

@description('Resource ID of subnet used for database private endpoint when enabled.')
param privateEndpointSubnetId string = ''

@description('Private endpoint resource name for the database when enabled.')
param privateEndpointName string = ''

@description('Whether to enable zone-redundant high availability.')
param enableHighAvailability bool = false

@description('Backup retention days for PostgreSQL Flexible Server.')
param backupRetentionDays int = 7

var postgresqlVersion = '16'

resource postgresqlServer 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: serverName
  location: location
  sku: {
    name: skuName
    tier: startsWith(skuName, 'Standard_B') ? 'Burstable' : 'GeneralPurpose'
  }
  properties: {
    version: postgresqlVersion
    administratorLogin: administratorLogin
    administratorLoginPassword: administratorPassword
    backup: {
      backupRetentionDays: backupRetentionDays
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: enableHighAvailability
      ? {
          mode: 'ZoneRedundant'
        }
      : {
          mode: 'Disabled'
        }
    network: {
      publicNetworkAccess: publicNetworkAccess
    }
    storage: {
      storageSizeGB: storageSizeGB
      iops: 360
      autoGrow: 'Enabled'
    }
  }
}

resource postgresqlDatabase 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgresqlServer
  name: databaseName
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

resource postgresqlPrivateEndpoint 'Microsoft.Network/privateEndpoints@2023-11-01' = if (enablePrivateEndpoint) {
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
          privateLinkServiceId: postgresqlServer.id
          groupIds: [
            'postgresqlServer'
          ]
        }
      }
    ]
  }
}

output databaseEngineResolved string = databaseEngine
output serverId string = postgresqlServer.id
output serverName string = postgresqlServer.name
output fqdn string = postgresqlServer.properties.fullyQualifiedDomainName
output databaseNameOut string = postgresqlDatabase.name
output privateEndpointId string = enablePrivateEndpoint ? postgresqlPrivateEndpoint.id : ''
