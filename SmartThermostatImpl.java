public class SmartThermostatImpl extends SmartThermostatGrpc.SmartThermostatImplBase {
    @Override
    public void setTemperature(TempRequest request, StreamObserver<TempResponse> responseObserver) {
        if (Context.current().getDeadline() != null && 
            Context.current().getDeadline().isExpired()) {
            responseObserver.onError(Status.DEADLINE_EXCEEDED.asRuntimeException());
            return;
        }
        
    }
}
