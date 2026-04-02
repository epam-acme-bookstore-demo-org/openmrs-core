targetScope = 'resourceGroup'

@description('Azure region for the database server.')
param location string

@description('Name of the MySQL Flexible Server.')
param serverName string

@description('Database engine type.')
@allowed([
  'MySQL'
  'MariaDB'
])
param databaseEngine string = 'MySQL'

@description('Name of the application database.')
param databaseName string = 'openmrs'

@description('Admin username for MySQL Flexible Server.')
param administratorLogin string = 'openmrsadmin'

@description('Admin password for MySQL Flexible Server.')
@secure()
param administratorPassword string

@description('SKU name for MySQL Flexible Server.')
param skuName string = 'Standard_B1ms'

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

@description('Backup retention days for MySQL Flexible Server.')
param backupRetentionDays int = 7

var mysqlVersion = '8.0.21'

resource mysqlServer 'Microsoft.DBforMySQL/flexibleServers@2023-12-30' = {
  name: serverName
  location: location
  sku: {
    name: skuName
    tier: startsWith(skuName, 'Standard_B') ? 'Burstable' : 'GeneralPurpose'
  }
  properties: {
    version: mysqlVersion
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
      storageSizeGB: 32
      iops: 360
      autoGrow: 'Enabled'
    }
  }
}

resource mysqlDb 'Microsoft.DBforMySQL/flexibleServers/databases@2023-12-30' = {
  parent: mysqlServer
  name: databaseName
  properties: {
    charset: 'utf8mb4'
    collation: 'utf8mb4_unicode_ci'
  }
}

resource mysqlPrivateEndpoint 'Microsoft.Network/privateEndpoints@2023-11-01' = if (enablePrivateEndpoint) {
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
          privateLinkServiceId: mysqlServer.id
          groupIds: [
            'mysqlServer'
          ]
        }
      }
    ]
  }
}

output databaseEngineResolved string = databaseEngine == 'MariaDB' ? 'MySQL (Azure managed path)' : 'MySQL'
output serverId string = mysqlServer.id
output serverName string = mysqlServer.name
output fqdn string = mysqlServer.properties.fullyQualifiedDomainName
output databaseNameOut string = mysqlDb.name
output privateEndpointId string = enablePrivateEndpoint ? mysqlPrivateEndpoint.id : ''
