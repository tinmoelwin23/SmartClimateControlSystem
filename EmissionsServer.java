package climate.emissions;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EmissionsServer {
private Server server;
private final int port;
private final String serviceType = "_emissions._tcp.local.";
private final String serviceName = "CarbonEmissionTrackerService";

public EmissionsServer(int port) {
this.port = port;
}

public void start() throws IOException {
server = ServerBuilder.forPort(port)
.addService(new CarbonEmissionTrackerImpl())
.build()
.start();

registerWithJmDNS();

Runtime.getRuntime().addShutdownHook(new Thread(() -> {
System.err.println("Shutting down gRPC server");
try {
EmissionsServer.this.stop();
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
ServiceInfo serviceInfo = ServiceInfo.create(serviceType, serviceName, port, "Carbon Emission Tracking Service");
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

static class CarbonEmissionTrackerImpl extends CarbonEmissionTrackerGrpc.CarbonEmissionTrackerImplBase {
@Override
public StreamObserver<UsageEntry> logDeviceUsage(StreamObserver<EmissionSummary> responseObserver) {
return new StreamObserver<UsageEntry>() {
final List<UsageEntry> entries = new ArrayList<>();
float totalCO2 = 0;

@Override
public void onNext(UsageEntry entry) {
entries.add(entry);
float co2 = entry.getDurationHours() * entry.getPowerRatingKw() * 0.5f;
totalCO2 += co2;
System.out.println("Logged usage: " + entry.getDeviceType() + " for " + 
entry.getDurationHours() + " hours, emitting " + co2 + " kg CO2");
}

@Override
public void onError(Throwable t) {
System.err.println("Error in device usage stream: " + t.getMessage());
}

@Override
public void onCompleted() {
String suggestions;
if (totalCO2 > 10) {
suggestions = "High emissions detected! Consider using energy-efficient devices.";
} else if (totalCO2 > 5) {
suggestions = "Moderate emissions. You could optimize device usage times.";
} else {
suggestions = "Good job! Your emissions are low.";
}

EmissionSummary summary = EmissionSummary.newBuilder()
.setTotalCo2(totalCO2)
.setSuggestions(suggestions)
.build();

responseObserver.onNext(summary);
responseObserver.onCompleted();
}
};
}
}

public static void main(String[] args) throws IOException, InterruptedException {
EmissionsServer server = new EmissionsServer(50053);
server.start();
System.out.println("Carbon Emission Tracker Server started, listening on " + server.port);
server.blockUntilShutdown();
}
}
