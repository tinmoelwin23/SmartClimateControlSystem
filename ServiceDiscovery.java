package climate.discovery;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ServiceDiscovery {
    private static final int DISCOVERY_TIMEOUT = 10; // seconds
    private final JmDNS jmdns;
    private final Map<String, ServiceInfo> discoveredServices = new HashMap<>();
    
    public ServiceDiscovery() throws IOException {
        this.jmdns = JmDNS.create(InetAddress.getLocalHost());
    }
    
    
    public void registerService(String serviceType, String serviceName, int port, 
                              Map<String, String> metadata) throws IOException {
        ServiceInfo serviceInfo = ServiceInfo.create(serviceType, serviceName, port, 0, 0, metadata);
        jmdns.registerService(serviceInfo);
        System.out.println("Registered service: " + serviceInfo);
    }
    
    public ServiceInfo discoverService(String serviceType, String serviceName) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        ServiceInfo[] foundService = new ServiceInfo[1];
        
        ServiceListener listener = new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
                System.out.println("Service added: " + event.getName());
                jmdns.requestServiceInfo(event.getType(), event.getName());
            }
            
            @Override
            public void serviceRemoved(ServiceEvent event) {
                System.out.println("Service removed: " + event.getName());
                discoveredServices.remove(event.getName());
            }
            
            @Override
            public void serviceResolved(ServiceEvent event) {
                System.out.println("Service resolved: " + event.getInfo());
                if (event.getName().equals(serviceName)) {
                    discoveredServices.put(event.getName(), event.getInfo());
                    foundService[0] = event.getInfo();
                    latch.countDown();
                }
            }
        };
        
        jmdns.addServiceListener(serviceType, listener);
        try {
            if (!latch.await(DISCOVERY_TIMEOUT, TimeUnit.SECONDS)) {
                System.err.println("Service discovery timeout");
                return null;
            }
            return foundService[0];
        } finally {
            jmdns.removeServiceListener(serviceType, listener);
        }
    }
    
    public void discoverServicesAsync(String serviceType, Consumer<ServiceInfo> callback) {
        ServiceListener listener = new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
                jmdns.requestServiceInfo(event.getType(), event.getName());
            }
            
            @Override
            public void serviceRemoved(ServiceEvent event) {
                discoveredServices.remove(event.getName());
            }
            
            @Override
            public void serviceResolved(ServiceEvent event) {
                discoveredServices.put(event.getName(), event.getInfo());
                callback.accept(event.getInfo());
            }
        };
        
        jmdns.addServiceListener(serviceType, listener);
    }
    
    public Map<String, ServiceInfo> listDiscoveredServices(String serviceType) {
        Map<String, ServiceInfo> result = new HashMap<>();
        for (Map.Entry<String, ServiceInfo> entry : discoveredServices.entrySet()) {
            if (entry.getValue().getType().equals(serviceType)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    
    public void shutdown() throws IOException {
        jmdns.close();
    }
}

