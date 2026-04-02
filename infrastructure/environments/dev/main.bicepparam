using './main.bicep'

param workloadName = 'openmrs'
param environmentName = 'dev'
param imageRepository = 'openmrs-core'
param imageTag = 'latest'
param dbAdminUsername = 'openmrsadmin'
param dbName = 'openmrs'
param databaseEngine = 'MySQL'
