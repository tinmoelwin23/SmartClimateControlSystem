public class EnhancedClimateControlClient {
    private final ServiceDiscovery discovery;
    private final Map<String, ServiceInfo> availableServices = new ConcurrentHashMap<>();
    
    public EnhancedClimateControlClient() throws IOException {
        this.discovery = new ServiceDiscovery();
        setupServiceDiscovery();
    }
    
    private void setupServiceDiscovery() {
        discoverServicesOfType("_airquality._tcp.local.");
        discoverServicesOfType("_thermostat._tcp.local.");
        discoverServicesOfType("_emissions._tcp.local.");
    }
    
    private void discoverServicesOfType(String serviceType) {
        discovery.discoverServicesAsync(serviceType, info -> {
            SwingUtilities.invokeLater(() -> {
                availableServices.put(info.getName(), info);
                updateServiceList();
                
                if (availableServices.values().stream()
                    .filter(s -> s.getType().equals(serviceType))
                    .count() == 1) {
                    connectToService(info);
                }
            });
        });
    }
    
    private void updateServiceList() {
    }
    
    private void connectToService(ServiceInfo info) {
        String type = info.getType();
        String address = info.getHostAddresses()[0];
        int port = info.getPort();
        
        try {
            if (type.equals("_airquality._tcp.local.")) {
                airQualityChannel = ManagedChannelBuilder.forAddress(address, port)
                    .usePlaintext()
                    .build();
                airQualityStub = AirQualityMonitorGrpc.newStub(airQualityChannel);
                log("Connected to Air Quality service at " + address + ":" + port);
            } 
            else if (type.equals("_thermostat._tcp.local.")) {
                thermostatChannel = ManagedChannelBuilder.forAddress(address, port)
                    .usePlaintext()
                    .build();
                thermostatBlockingStub = SmartThermostatGrpc.newBlockingStub(thermostatChannel);
                thermostatAsyncStub = SmartThermostatGrpc.newStub(thermostatChannel);
                log("Connected to Thermostat service at " + address + ":" + port);
            }
            else if (type.equals("_emissions._tcp.local.")) {
                emissionsChannel = ManagedChannelBuilder.forAddress(address, port)
                    .usePlaintext()
                    .build();
                emissionsStub = CarbonEmissionTrackerGrpc.newStub(emissionsChannel);
                log("Connected to Emissions service at " + address + ":" + port);
            }
        } catch (Exception e) {
            log("Failed to connect to service: " + e.getMessage());
        }
    }
