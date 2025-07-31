package climate.error;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;

public class GrpcErrorHandler {
    private static final Logger logger = LoggerFactory.getLogger(GrpcErrorHandler.class);
    
    public static class ServerErrorInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
            
            ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                @Override
                public void close(Status status, Metadata trailers) {
                    if (status.getCode() != Status.Code.OK) {
                        logError(status, "Server error occurred");
                    }
                    super.close(status, trailers);
                }
            };
            
            return next.startCall(wrappedCall, headers);
        }
    }
    
    
    public static class ClientErrorInterceptor implements ClientInterceptor {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
            
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            if (status.getCode() != Status.Code.OK) {
                                logError(status, "Client call failed");
                            }
                            super.onClose(status, trailers);
                        }
                    }, headers);
                }
            };
        }
    }
    
    private static void logError(Status status, String context) {
        logger.error("{}: {} - {}", context, status.getCode(), status.getDescription());
        if (status.getCause() != null) {
            logger.error("Root cause:", status.getCause());
        }
    }
    
    public static Status handleException(Throwable throwable) {
        if (throwable instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT
                .withDescription(throwable.getMessage())
                .withCause(throwable);
        } else if (throwable instanceof IllegalStateException) {
            return Status.FAILED_PRECONDITION
                .withDescription(throwable.getMessage())
                .withCause(throwable);
        } else if (throwable instanceof UnsupportedOperationException) {
            return Status.UNIMPLEMENTED
                .withDescription(throwable.getMessage())
                .withCause(throwable);
        } else {
            return Status.INTERNAL
                .withDescription("Internal server error")
                .withCause(throwable);
        }
    }
}
