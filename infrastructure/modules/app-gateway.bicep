targetScope = 'resourceGroup'

@description('Azure region for Application Gateway.')
param location string

@description('Name of Application Gateway.')
param appGatewayName string

@description('Name of public IP resource for Application Gateway.')
param publicIpName string

@description('Resource ID of subnet used by Application Gateway.')
param appGatewaySubnetId string

@description('Frontend port for HTTP listener.')
param httpPort int = 80

@description('Frontend port for HTTPS listener.')
param httpsPort int = 443

@description('Backend hostname (Container App internal FQDN).')
param backendHostName string

@description('Backend port.')
param backendPort int = 8080

@description('Base64-encoded PFX certificate data for HTTPS listener.')
@secure()
param sslCertificateData string

@description('PFX certificate password for HTTPS listener.')
@secure()
param sslCertificatePassword string

@description('WAF mode for Application Gateway.')
@allowed([
  'Detection'
  'Prevention'
])
param wafMode string = 'Prevention'

@description('Capacity units for Application Gateway autoscale minimum.')
param minCapacity int = 1

@description('Capacity units for Application Gateway autoscale maximum.')
param maxCapacity int = 2

resource publicIp 'Microsoft.Network/publicIPAddresses@2023-11-01' = {
  name: publicIpName
  location: location
  sku: {
    name: 'Standard'
  }
  properties: {
    publicIPAllocationMethod: 'Static'
    publicIPAddressVersion: 'IPv4'
  }
}

resource appGateway 'Microsoft.Network/applicationGateways@2023-11-01' = {
  name: appGatewayName
  location: location
  sku: {
    name: 'WAF_v2'
    tier: 'WAF_v2'
  }
  properties: {
    autoscaleConfiguration: {
      minCapacity: minCapacity
      maxCapacity: maxCapacity
    }
    gatewayIPConfigurations: [
      {
        name: 'gwipc-openmrs'
        properties: {
          subnet: {
            id: appGatewaySubnetId
          }
        }
      }
    ]
    frontendIPConfigurations: [
      {
        name: 'feip-openmrs'
        properties: {
          publicIPAddress: {
            id: publicIp.id
          }
        }
      }
    ]
    frontendPorts: [
      {
        name: 'fp-http'
        properties: {
          port: httpPort
        }
      }
      {
        name: 'fp-https'
        properties: {
          port: httpsPort
        }
      }
    ]
    backendAddressPools: [
      {
        name: 'be-openmrs'
        properties: {
          backendAddresses: [
            {
              fqdn: backendHostName
            }
          ]
        }
      }
    ]
    backendHttpSettingsCollection: [
      {
        name: 'bhs-openmrs'
        properties: {
          port: backendPort
          protocol: 'Http'
          cookieBasedAffinity: 'Disabled'
          requestTimeout: 30
          pickHostNameFromBackendAddress: true
          probe: {
            id: resourceId('Microsoft.Network/applicationGateways/probes', appGateway.name, 'probe-openmrs')
          }
        }
      }
    ]
    httpListeners: [
      {
        name: 'listener-http'
        properties: {
          frontendIPConfiguration: {
            id: resourceId('Microsoft.Network/applicationGateways/frontendIPConfigurations', appGateway.name, 'feip-openmrs')
          }
          frontendPort: {
            id: resourceId('Microsoft.Network/applicationGateways/frontendPorts', appGateway.name, 'fp-http')
          }
          protocol: 'Http'
        }
      }
      {
        name: 'listener-https'
        properties: {
          frontendIPConfiguration: {
            id: resourceId('Microsoft.Network/applicationGateways/frontendIPConfigurations', appGateway.name, 'feip-openmrs')
          }
          frontendPort: {
            id: resourceId('Microsoft.Network/applicationGateways/frontendPorts', appGateway.name, 'fp-https')
          }
          protocol: 'Https'
          sslCertificate: {
            id: resourceId('Microsoft.Network/applicationGateways/sslCertificates', appGateway.name, 'ssl-openmrs')
          }
        }
      }
    ]
    requestRoutingRules: [
      {
        name: 'rr-http-redirect'
        properties: {
          ruleType: 'Basic'
          priority: 100
          httpListener: {
            id: resourceId('Microsoft.Network/applicationGateways/httpListeners', appGateway.name, 'listener-http')
          }
          redirectConfiguration: {
            id: resourceId('Microsoft.Network/applicationGateways/redirectConfigurations', appGateway.name, 'redirect-https')
          }
        }
      }
      {
        name: 'rr-https-openmrs'
        properties: {
          ruleType: 'Basic'
          priority: 110
          httpListener: {
            id: resourceId('Microsoft.Network/applicationGateways/httpListeners', appGateway.name, 'listener-https')
          }
          backendAddressPool: {
            id: resourceId('Microsoft.Network/applicationGateways/backendAddressPools', appGateway.name, 'be-openmrs')
          }
          backendHttpSettings: {
            id: resourceId('Microsoft.Network/applicationGateways/backendHttpSettingsCollection', appGateway.name, 'bhs-openmrs')
          }
        }
      }
    ]
    redirectConfigurations: [
      {
        name: 'redirect-https'
        properties: {
          redirectType: 'Permanent'
          targetListener: {
            id: resourceId('Microsoft.Network/applicationGateways/httpListeners', appGateway.name, 'listener-https')
          }
          includePath: true
          includeQueryString: true
        }
      }
    ]
    probes: [
      {
        name: 'probe-openmrs'
        properties: {
          protocol: 'Http'
          path: '/openmrs/health/alive'
          pickHostNameFromBackendHttpSettings: true
          interval: 30
          timeout: 30
          unhealthyThreshold: 3
          port: backendPort
        }
      }
    ]
    sslCertificates: [
      {
        name: 'ssl-openmrs'
        properties: {
          data: sslCertificateData
          password: sslCertificatePassword
        }
      }
    ]
    webApplicationFirewallConfiguration: {
      enabled: true
      firewallMode: wafMode
      ruleSetType: 'OWASP'
      ruleSetVersion: '3.2'
      requestBodyCheck: true
      maxRequestBodySizeInKb: 128
      fileUploadLimitInMb: 100
    }
  }
}

output appGatewayId string = appGateway.id
output publicIpAddress string = publicIp.properties.ipAddress
