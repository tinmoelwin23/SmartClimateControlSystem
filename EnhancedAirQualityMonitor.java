package climate.airquality;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EnhancedAirQualityMonitor extends AirQualityMonitorGrpc.AirQualityMonitorImplBase {
    private final Map<String, List<AirQualityReading>> historicalData = new ConcurrentHashMap<>();
    private final Map<String, Float> alertThresholds = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthMonitor = Executors.newSingleThreadScheduledExecutor();
    private boolean isHealthy = true;
    
    public EnhancedAirQualityMonitor() {
        alertThresholds.put("co2", 800.0f);  
        alertThresholds.put("no2", 30.0f);   
        alertThresholds.put("pm25", 25.0f);  
        
        healthMonitor.scheduleAtFixedRate(this::checkHealth, 0, 1, TimeUnit.MINUTES);
    }
    
    @Override
    public void streamAirQuality(SensorRequest request, StreamObserver<AirQualityReading> responseObserver) {
        if (!isHealthy) {
            responseObserver.onError(Status.UNAVAILABLE
                .withDescription("Service temporarily unavailable")
                .asRuntimeException());
            return;
        }
        
        String location = request.getLocation();
        historicalData.putIfAbsent(location, new ArrayList<>());
        
        new Thread(() -> {
            Random random = new Random();
            try {
                for (int i = 0; i < 30; i++) {
                    if (responseObserver.isCancelled()) {
                        return;
                    }
                    
                   
                    boolean anomaly = random.nextDouble() < 0.1;
                    
                    AirQualityReading reading = AirQualityReading.newBuilder()
                        .setCo2(anomaly ? 1500 + random.nextFloat() * 1000 : 
                                400 + random.nextFloat() * 600)
                        .setNo2(anomaly ? 60 + random.nextFloat() * 40 : 
                                random.nextFloat() * 50)
                        .setPm25(anomaly ? 50 + random.nextFloat() * 30 : 
                                 random.nextFloat() * 35)
                        .setTimestamp(String.valueOf(System.currentTimeMillis()))
                        .setAnomaly(anomaly)
                        .build();
                    
                    historicalData.get(location).add(reading);
                    
                    checkThresholds(reading, location);
                    
                    responseObserver.onNext(reading);
                    Thread.sleep(1000);
                }
                responseObserver.onCompleted();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    private void checkThresholds(AirQualityReading reading, String location) {
        StringBuilder alert = new StringBuilder();
        if (reading.getCo2() > alertThresholds.get("co2")) {
            alert.append(String.format("High CO2 (%.1f ppm) ", reading.getCo2()));
        }
        if (reading.getNo2() > alertThresholds.get("no2")) {
            alert.append(String.format("High NO2 (%.1f ppb) ", reading.getNo2()));
        }
        if (reading.getPm25() > alertThresholds.get("pm25")) {
            alert.append(String.format("High PM2.5 (%.1f μg/m³) ", reading.getPm25()));
        }
        
        if (alert.length() > 0) {
            System.out.println("ALERT at " + location + ": " + alert);
        }
    }
    
    private void checkHealth() {
        isHealthy = Math.random() > 0.1;
        if (!isHealthy) {
            System.out.println("Service health check failed - entering degraded mode");
        }
    }
    
    
    @Override
    public void getHistoricalData(HistoricalDataRequest request, 
                                StreamObserver<HistoricalDataResponse> responseObserver) {
        String location = request.getLocation();
        int hours = request.getHours();
        
        if (!historicalData.containsKey(location)) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription("No data for location: " + location)
                .asRuntimeException());
            return;
        }
        
        List<AirQualityReading> filtered = historicalData.get(location).stream()
            .filter(r -> isWithinHours(r.getTimestamp(), hours))
            .toList();
        
        HistoricalDataResponse response = HistoricalDataResponse.newBuilder()
            .addAllReadings(filtered)
            .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    private boolean isWithinHours(String timestamp, int hours) {
        long time = Long.parseLong(timestamp);
        long cutoff = System.currentTimeMillis() - (hours * 3600 * 1000);
        return time >= cutoff;
    }
    
    @Override
    public void setAlertThresholds(ThresholdRequest request, 
                                 StreamObserver<ThresholdResponse> responseObserver) {
        if (request.hasCo2()) {
            alertThresholds.put("co2", request.getCo2());
        }
        if (request.hasNo2()) {
            alertThresholds.put("no2", request.getNo2());
        }
        if (request.hasPm25()) {
            alertThresholds.put("pm25", request.getPm25());
        }
        
        ThresholdResponse response = ThresholdResponse.newBuilder()
            .setStatus("Thresholds updated successfully")
            .putAllCurrentThresholds(alertThresholds)
            .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
