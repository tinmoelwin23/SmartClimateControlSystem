package climate.thermostat;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.*;
import java.util.concurrent.*;

public class EnhancedSmartThermostat extends SmartThermostatGrpc.SmartThermostatImplBase {
    private final Map<String, Float> currentTemperatures = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledChanges = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, List<TemperatureSchedule>> schedules = new ConcurrentHashMap<>();
    private final EnergyOptimizer energyOptimizer = new EnergyOptimizer();
    
    private static class TemperatureSchedule {
        int hour;
        float temperature;
        String days;  
        
        TemperatureSchedule(int hour, float temperature, String days) {
            this.hour = hour;
            this.temperature = temperature;
            this.days = days;
        }
    }
    
    @Override
    public void setTemperature(TempRequest request, StreamObserver<TempResponse> responseObserver) {
        String room = request.getRoom();
        float desiredTemp = request.getDesiredTemp();
        
        
        cancelScheduledChange(room);
        
        return applyTemperatureChange(room, desiredTemp, responseObserver);
    }
    
    @Override
    public StreamObserver<OccupancyUpdate> streamOccupancy(StreamObserver<HVACState> responseObserver) {
        return new StreamObserver<OccupancyUpdate>() {
            @Override
            public void onNext(OccupancyUpdate update) {
                String room = update.getRoom();
                boolean occupied = update.getIsOccupied();
                
                
                float optimalTemp = energyOptimizer.calculateOptimalTemp(
                    room, 
                    occupied,
                    getCurrentSchedule(room)
                );
                
                applyOptimalTemperature(room, optimalTemp, responseObserver);
            }
            
            @Override
            public void onError(Throwable t) {
                System.err.println("Occupancy stream error: " + t.getMessage());
            }
            
            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
    
    private void applyOptimalTemperature(String room, float temp, 
                                       StreamObserver<HVACState> responseObserver) {
        try {
            
            if (Math.random() < 0.05) {  // 5% chance of failure
                throw new IOException("HVAC communication failed");
            }
            
            currentTemperatures.put(room, temp);
            
            HVACState state = HVACState.newBuilder()
                .setMode(temp == energyOptimizer.getEcoTemp() ? "Eco" : "Comfort")
                .setCurrentTemp(temp)
                .setEfficiency(energyOptimizer.getEfficiencyRating(room, temp))
                .setPowerUsage(energyOptimizer.calculatePowerUsage(room, temp))
                .build();
            
            responseObserver.onNext(state);
        } catch (Exception e) {
            System.err.println("Error applying temperature to " + room + ": " + e.getMessage());
            
            scheduler.schedule(() -> applyOptimalTemperature(room, temp, responseObserver), 
                             1, TimeUnit.SECONDS);
        }
    }
    
    private void applyTemperatureChange(String room, float temp, 
                                      StreamObserver<TempResponse> responseObserver) {
        try {
            currentTemperatures.put(room, temp);
            
            TempResponse response = TempResponse.newBuilder()
                .setStatus("Temperature set to " + temp + "°C in " + room)
                .setPowerUsage(energyOptimizer.calculatePowerUsage(room, temp))
                .setEfficiency(energyOptimizer.getEfficiencyRating(room, temp))
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to set temperature: " + e.getMessage())
                .asRuntimeException());
        }
    }
    
    
    @Override
    public void scheduleTemperature(ScheduleRequest request, 
                                  StreamObserver<ScheduleResponse> responseObserver) {
        String room = request.getRoom();
        schedules.putIfAbsent(room, new ArrayList<>());
        
        TemperatureSchedule schedule = new TemperatureSchedule(
            request.getHour(),
            request.getTemperature(),
            request.getDays()
        );
        
        schedules.get(room).add(schedule);
        
        
        cancelScheduledChange(room);
        
        scheduleNextChange(room);
        
        ScheduleResponse response = ScheduleResponse.newBuilder()
            .setStatus("Schedule added for " + room)
            .setNextChangeTime(getNextChangeTime(room))
            .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    
    private void scheduleNextChange(String room) {
        
        long delay = calculateNextDelay(room);
        
        if (delay > 0) {
            ScheduledFuture<?> future = scheduler.schedule(
                () -> executeScheduledChange(room),
                delay,
                TimeUnit.MILLISECONDS
            );
            
            scheduledChanges.put(room, future);
        }
    }
    
    private void executeScheduledChange(String room) {
        
        float temp = getScheduledTemp(room);
        applyTemperatureChange(room, temp, new StreamObserver<TempResponse>() {
            @Override public void onNext(TempResponse response) {}
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() {
                
                scheduleNextChange(room);
            }
        });
    }
    
    private static class EnergyOptimizer {
        private static final float ECO_TEMP = 18.0f;
        private static final float COMFORT_TEMP = 22.0f;
        
        float calculateOptimalTemp(String room, boolean occupied, String currentSchedule) {
            
            if (!occupied) {
                return ECO_TEMP;
            }
            
            
            if (currentSchedule != null && currentSchedule.contains("NIGHT")) {
                return COMFORT_TEMP - 1.0f;
            }
            
            return COMFORT_TEMP;
        }
        
        float getEcoTemp() {
            return ECO_TEMP;
        }
        
        String calculatePowerUsage(String room, float temp) {
            float baseUsage = 1.5f;  // kW
            float delta = Math.abs(temp - 20.0f);  // 20°C is ideal
            float usage = baseUsage + (delta * 0.2f);
            return String.format("%.2f kW", usage);
        }
        
        String getEfficiencyRating(String room, float temp) {
            if (temp <= ECO_TEMP) {
                return "Excellent";
            } else if (temp <= COMFORT_TEMP) {
                return "Good";
            } else {
                return "Fair";
            }
        }
    }
}
