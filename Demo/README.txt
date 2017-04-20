USAGE:

1) Edit ./ExternalServer/server_config.json and ./InternalAgent/configuration.json

2) Start ./ExternalServer/Server.jar ($ java -jar Server.jar)

3) Start ./InternalAgent/InternalAgent.jar ($ java -jar InternalAgent.jar)

4) Import ./UserKeyStore.p12 certificate (password: domUser) in the application that will use the services localized in the private network (i.e. browser)