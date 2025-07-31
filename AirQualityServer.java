package climate.airquality;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class AirQualityServer {
    private Server server;
    private final int port;
    private final String serviceType = "_airquality._tcp.local.";
    private final String serviceName = "AirQualityMonitorService";

    public AirQualityServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new AirQualityMonitorImpl())
                .build()
                .start();
        
        registerWithJmDNS();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down gRPC server");
            try {
                AirQualityServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
        }));
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private void registerWithJmDNS() {
        try {
            JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
            ServiceInfo serviceInfo = ServiceInfo.create(serviceType, serviceName, port, "Air Quality Monitoring Service");
            jmdns.registerService(serviceInfo);
            System.out.println("Registered service with jmDNS: " + serviceInfo);
        } catch (IOException e) {
            System.err.println("Could not register with jmDNS: " + e.getMessage());
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    static class AirQualityMonitorImpl extends AirQualityMonitorGrpc.AirQualityMonitorImplBase {
        private final Random random = new Random();

        @Override
        public void streamAirQuality(SensorRequest request, StreamObserver<AirQualityReading> responseObserver) {
            try {
                String location = request.getLocation();
                System.out.println("Starting air quality stream for location: " + location);
                
           
                for (int i = 0; i < 20; i++) {
                    if (responseObserver.isCancelled()) {
                        return;
                    }
                    
                    AirQualityReading reading = AirQualityReading.newBuilder()
                            .setCo2(400 + random.nextFloat() * 600)
                            .setNo2(random.nextFloat() * 50)        
                            .setPm25(random.nextFloat() * 35)    
                            .setTimestamp(String.valueOf(System.currentTimeMillis()))
                            .build();
                    
                    responseObserver.onNext(reading);
                    Thread.sleep(1000); 
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                responseObserver.onCompleted();
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        AirQualityServer server = new AirQualityServer(50051);
        server.start();
        System.out.println("Air Quality Monitor Server started, listening on " + server.port);
        server.blockUntilShutdown();
    }
}
