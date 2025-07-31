package climate.thermostat;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ThermostatServer {
    private Server server;
    private final int port;
    private final String serviceType = "_thermostat._tcp.local.";
    private final String serviceName = "SmartThermostatService";
    private final Map<String, Float> roomTemperatures = new HashMap<>();

    public ThermostatServer(int port) {
        this.port = port;
        roomTemperatures.put("Living Room", 22.0f);
        roomTemperatures.put("Bedroom", 20.0f);
        roomTemperatures.put("Kitchen", 21.0f);
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new SmartThermostatImpl())
                .build()
                .start();
        
        registerWithJmDNS();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down gRPC server");
            try {
                ThermostatServer.this.stop();
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
            ServiceInfo serviceInfo = ServiceInfo.create(serviceType, serviceName, port, "Smart Thermostat Service");
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

    static class SmartThermostatImpl extends SmartThermostatGrpc.SmartThermostatImplBase {
        @Override
        public void setTemperature(TempRequest request, StreamObserver<TempResponse> responseObserver) {
            String room = request.getRoom();
            float desiredTemp = request.getDesiredTemp();

            String status = "Temperature set to " + desiredTemp + "°C in " + room;
            String powerUsage = "Estimated power usage: " + (Math.abs(desiredTemp - 20) * 0.5) + " kW";
            
            TempResponse response = TempResponse.newBuilder()
                    .setStatus(status)
                    .setPowerUsage(powerUsage)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public StreamObserver<OccupancyUpdate> streamOccupancy(StreamObserver<HVACState> responseObserver) {
            return new StreamObserver<OccupancyUpdate>() {
                @Override
                public void onNext(OccupancyUpdate update) {
                    // Adjust HVAC based on occupancy
                    String room = update.getRoom();
                    boolean isOccupied = update.getIsOccupied();
                    
                    HVACState state = HVACState.newBuilder()
                            .setMode(isOccupied ? "Comfort" : "Eco")
                            .setCurrentTemp(isOccupied ? 22.0f : 18.0f)
                            .setEfficiency(isOccupied ? "Standard" : "Energy Saving")
                            .build();
                    
                    responseObserver.onNext(state);
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("Error in occupancy stream: " + t.getMessage());
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ThermostatServer server = new ThermostatServer(50052);
        server.start();
        System.out.println("Smart Thermostat Server started, listening on " + server.port);
        server.blockUntilShutdown();
    }
}
