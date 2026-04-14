using './main.bicep'

param workloadName = 'openmrs'
param environmentName = 'dev'
param imageRepository = 'openmrs-core'
param imageTag = 'REPLACE_WITH_IMAGE_TAG'
param dbAdminUsername = 'openmrsadmin'
param dbName = 'openmrs'
param databaseEngine = 'PostgreSQL'
