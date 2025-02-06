package org.opensourcetrader.marketsimulator.marketdataserver;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.opensourcetrader.marketsimulator.exchange.Exchange;
import org.opensourcetrader.marketsimulator.marketdataservice.api.FixSimMarketDataServiceGrpc;
import org.fixprotocol.components.MarketData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MarketDataService {
    private Exchange exchange;
    private int port;
    private Server server;

    public MarketDataService(@Autowired Exchange exchange, @Value("${app.market_data_server_port}") int port) {
        this.exchange = exchange;
        this.port = port;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(ServerInterceptors.intercept(new MarketDataServiceImpl(this.exchange),
                        new MdAuthInterceptor()))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        log.info("Market data service started, Listening on port: "+port);

        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                System.err.println("*** shutting down market data service gRPC server since JVM is shutting down");
                try {
                    MarketDataService.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server shut down");
            }
        });
    }


    public void  stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }


    static class MarketDataServiceImpl  extends FixSimMarketDataServiceGrpc.FixSimMarketDataServiceImplBase {
        Exchange exchange;

        MarketDataServiceImpl(Exchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public StreamObserver<MarketData.MarketDataRequest>
        connect(StreamObserver<MarketData.MarketDataIncrementalRefresh> responseObserver) {
            String subscriberId = MdAuthInterceptor.SUBSCRIBER_ID.get();

            log.info("Market data connection recieved for: "+ subscriberId);

            var connection = new Connection(exchange,responseObserver, subscriberId);

            return new StreamObserver<>() {
                @Override
                public void onNext(MarketData.MarketDataRequest request) {
                    log.info(subscriberId + " subsriber request: " + request);
                    connection.subscribe(request);
                }

                @Override
                public void onError(Throwable throwable) {
                    connection.close();
                    log.error(subscriberId + "connection error: " + throwable);
                }

                @Override
                public void onCompleted() {
                    connection.close();
                    log.info(subscriberId + " connection completed.");
                }
            };
        }

    }

}
