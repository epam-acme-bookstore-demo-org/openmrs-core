targetScope = 'resourceGroup'

@description('Azure region for the virtual network.')
param location string

@description('Name of the virtual network.')
param vnetName string

@description('Address space for the virtual network.')
param vnetAddressPrefix string = '10.0.0.0/16'

@description('Name of the Azure Container Apps subnet.')
param acaSubnetName string = 'aca-subnet'

@description('Address prefix for the Azure Container Apps subnet.')
param acaSubnetPrefix string = '10.0.0.0/23'

@description('Name of the Application Gateway subnet.')
param appGatewaySubnetName string = 'appgw-subnet'

@description('Address prefix for the Application Gateway subnet.')
param appGatewaySubnetPrefix string = '10.0.2.0/24'

@description('Name of the database subnet.')
param dbSubnetName string = 'db-subnet'

@description('Address prefix for the database subnet.')
param dbSubnetPrefix string = '10.0.3.0/24'

@description('Name of the management/private endpoint subnet.')
param mgmtSubnetName string = 'mgmt-subnet'

@description('Address prefix for the management/private endpoint subnet.')
param mgmtSubnetPrefix string = '10.0.4.0/24'

@description('Name of NSG for ACA subnet.')
param acaNsgName string

@description('Name of NSG for Application Gateway subnet.')
param appGatewayNsgName string

@description('Name of NSG for database subnet.')
param dbNsgName string

@description('Name of NSG for management subnet.')
param mgmtNsgName string

resource acaNsg 'Microsoft.Network/networkSecurityGroups@2023-11-01' = {
  name: acaNsgName
  location: location
  properties: {
    securityRules: [
      {
        name: 'allow-appgw-to-aca-8080'
        properties: {
          protocol: 'Tcp'
          sourcePortRange: '*'
          destinationPortRange: '8080'
          sourceAddressPrefix: appGatewaySubnetPrefix
          destinationAddressPrefix: acaSubnetPrefix
          access: 'Allow'
          priority: 100
          direction: 'Inbound'
        }
      }
      {
        name: 'allow-aca-outbound-https'
        properties: {
          protocol: 'Tcp'
          sourcePortRange: '*'
          destinationPortRange: '443'
          sourceAddressPrefix: acaSubnetPrefix
          destinationAddressPrefix: '*'
          access: 'Allow'
          priority: 110
          direction: 'Outbound'
        }
      }
    ]
  }
}

resource appgwNsg 'Microsoft.Network/networkSecurityGroups@2023-11-01' = {
  name: appGatewayNsgName
  location: location
  properties: {
    securityRules: [
      {
        name: 'allow-internet-https'
        properties: {
          protocol: 'Tcp'
          sourcePortRange: '*'
          destinationPortRange: '443'
          sourceAddressPrefix: 'Internet'
          destinationAddressPrefix: appGatewaySubnetPrefix
          access: 'Allow'
          priority: 100
          direction: 'Inbound'
        }
      }
      {
        name: 'allow-internet-http'
        properties: {
          protocol: 'Tcp'
          sourcePortRange: '*'
          destinationPortRange: '80'
          sourceAddressPrefix: 'Internet'
          destinationAddressPrefix: appGatewaySubnetPrefix
          access: 'Allow'
          priority: 110
          direction: 'Inbound'
        }
      }
      {
        name: 'allow-appgw-to-aca'
        properties: {
          protocol: 'Tcp'
          sourcePortRange: '*'
          destinationPortRange: '8080'
          sourceAddressPrefix: appGatewaySubnetPrefix
          destinationAddressPrefix: acaSubnetPrefix
          access: 'Allow'
          priority: 120
          direction: 'Outbound'
        }
      }
    ]
  }
}

resource dbNsg 'Microsoft.Network/networkSecurityGroups@2023-11-01' = {
  name: dbNsgName
  location: location
  properties: {
    securityRules: [
      {
        name: 'allow-aca-to-db-3306'
        properties: {
          protocol: 'Tcp'
          sourcePortRange: '*'
          destinationPortRange: '3306'
          sourceAddressPrefix: acaSubnetPrefix
          destinationAddressPrefix: dbSubnetPrefix
          access: 'Allow'
          priority: 100
          direction: 'Inbound'
        }
      }
    ]
  }
}

resource mgmtNsg 'Microsoft.Network/networkSecurityGroups@2023-11-01' = {
  name: mgmtNsgName
  location: location
  properties: {
    securityRules: [
      {
        name: 'allow-aca-to-private-endpoints-443'
        properties: {
          protocol: 'Tcp'
          sourcePortRange: '*'
          destinationPortRange: '443'
          sourceAddressPrefix: acaSubnetPrefix
          destinationAddressPrefix: mgmtSubnetPrefix
          access: 'Allow'
          priority: 100
          direction: 'Inbound'
        }
      }
    ]
  }
}

resource vnet 'Microsoft.Network/virtualNetworks@2023-11-01' = {
  name: vnetName
  location: location
  properties: {
    addressSpace: {
      addressPrefixes: [
        vnetAddressPrefix
      ]
    }
    subnets: [
      {
        name: acaSubnetName
        properties: {
          addressPrefix: acaSubnetPrefix
          delegations: [
            {
              name: 'acaDelegation'
              properties: {
                serviceName: 'Microsoft.App/environments'
              }
            }
          ]
          networkSecurityGroup: {
            id: acaNsg.id
          }
        }
      }
      {
        name: appGatewaySubnetName
        properties: {
          addressPrefix: appGatewaySubnetPrefix
          networkSecurityGroup: {
            id: appgwNsg.id
          }
        }
      }
      {
        name: dbSubnetName
        properties: {
          addressPrefix: dbSubnetPrefix
          networkSecurityGroup: {
            id: dbNsg.id
          }
        }
      }
      {
        name: mgmtSubnetName
        properties: {
          addressPrefix: mgmtSubnetPrefix
          privateEndpointNetworkPolicies: 'Disabled'
          networkSecurityGroup: {
            id: mgmtNsg.id
          }
        }
      }
    ]
  }
}

output vnetId string = vnet.id
output acaSubnetId string = resourceId('Microsoft.Network/virtualNetworks/subnets', vnet.name, acaSubnetName)
output appGatewaySubnetId string = resourceId('Microsoft.Network/virtualNetworks/subnets', vnet.name, appGatewaySubnetName)
output dbSubnetId string = resourceId('Microsoft.Network/virtualNetworks/subnets', vnet.name, dbSubnetName)
output mgmtSubnetId string = resourceId('Microsoft.Network/virtualNetworks/subnets', vnet.name, mgmtSubnetName)
