package org.opensourcetrader.marketsimulator.orderentryserver;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.opensourcetrader.marketsimulator.exchange.Exchange;
import org.opensourcetrader.marketsimulator.exchange.Side;
import org.opensourcetrader.marketsimulator.orderentryserver.api.OrderEntryServiceGrpc;
import org.opensourcetrader.marketsimulator.orderentryserver.api.Orderentryapi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class OrderEntryService {

    private Exchange exchange;

    public OrderEntryService(@Autowired Exchange exchange) {
        this.exchange = exchange;
    }

    private Server server;

    public void start() throws IOException {
        int port = 50061;
        server  = ServerBuilder.forPort(port)
                .addService(new OrderEntryServiceImpl(this.exchange))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        log.info("Order entry service started, listening on port: {}",port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("*** shutting down order entry service gRPC server since JVM is shutting down");
                try {
                    OrderEntryService.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server shut down");
            }
        });
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }


    public  void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    static class OrderEntryServiceImpl extends OrderEntryServiceGrpc.OrderEntryServiceImplBase {
        Exchange exchange;
         Map<String, String> clOrdIdToSymbol = new ConcurrentHashMap<>();
         Map<String, String> clOrdIdToBookId = new ConcurrentHashMap<>();

         OrderEntryServiceImpl(Exchange exchange){
             this.exchange = exchange;
         }

        @Override
        public void submitNewOrder(Orderentryapi.NewOrderParams request, StreamObserver<Orderentryapi.OrderId> responseObserver) {
            var book =  this.exchange.getOrderBook(request.getSymbol());
            clOrdIdToSymbol.put(request.getClOrderId(), request.getSymbol());

            Side side = request.getOderSide() == Orderentryapi.Side.BUY ? Side.BUY : Side.SELL;
            var qntB = BigInteger.valueOf(request.getQuantity().getMantissa());
            var exp = BigInteger.valueOf(request.getQuantity().getExponent());
            int qty = qntB.multiply(exp).intValue();

            BigDecimal price = BigDecimal.valueOf(request.getPrice().getMantissa(), -1 * request.getPrice().getExponent());

            var id = book.addOrder(side, qty, price, request.getClOrderId());
            clOrdIdToBookId.put(request.getClOrderId(), id);

            var orderId = Orderentryapi.OrderId.newBuilder().setOrderId(id).build();
            responseObserver.onNext(orderId);
            responseObserver.onCompleted();
        }


        @Override
        public void cancelOrder(Orderentryapi.OrderId request, StreamObserver<Orderentryapi.Empty> responseObserver) {
            var symbol = clOrdIdToSymbol.get(request.getOrderId());

            if (symbol == null) {
                responseObserver.onError(new Exception("Symbol not found for client order id: "+ request.getOrderId()));
            }

            var localOrderId = clOrdIdToBookId.get(request.getOrderId());
            if (localOrderId == null){
                responseObserver.onError(new Exception("Local order id not found for the client order id: "+request.getOrderId()));
            }

            var book = this.exchange.getOrderBook(symbol);

            try {
                book.deleteOrder(localOrderId);
            } catch (Exception e) {
                responseObserver.onError(new Exception("Failed to cancel the order: "+ request.getOrderId()));
            }

            responseObserver.onNext(Orderentryapi.Empty.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

}
