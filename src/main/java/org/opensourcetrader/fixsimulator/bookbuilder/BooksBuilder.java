package org.opensourcetrader.fixsimulator.bookbuilder;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.opensourcetrader.fixsimulator.exchange.*;

import java.io.FileReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BooksBuilder {
    private Exchange exchange;

    private ScheduledThreadPoolExecutor executor;

    private List<BookBuilder> builders = new ArrayList<>();

    public BooksBuilder(Exchange exchange, String depthDataPath, String symbolsToRunStr,
                       int numExecutorThreads, long updateInterval, double minQty, double variation,
                       int tickScale, double tradeProbability, int maxDepth, double cancelProbability) throws  Exception {
        this.exchange = exchange;
        FileReader fr = new FileReader(depthDataPath);
        Gson gson = new Gson();
        Depth[] depths  = gson.fromJson(fr,Depth[].class);
        Map<String, Depth> symToDepth = new HashMap<>();
        Arrays.stream(depths).forEach(depth -> symToDepth.put(depth.symbol, depth));

        Set<String> symsToRun = new HashSet<>();
        if(symbolsToRunStr.equals("*")) {
            symsToRun.addAll(symToDepth.keySet());
        } else {
            symsToRun.addAll(Arrays.asList(symbolsToRunStr.split(",")));
        }
        //TODO this can be abstracted away
        this.executor = new ScheduledThreadPoolExecutor(numExecutorThreads);

        symsToRun.forEach(s->{
            builders.add(new BookBuilder(exchange.getOrderBook(s), symToDepth.get(s), executor, updateInterval, minQty,
                    variation, tickScale, tradeProbability, maxDepth, cancelProbability));
        });
    }


    static  class BookBuilder {
        private  static final String BOOK_BUILDER_ORDERID_PREPEND = "BOOKBUILDER";

        BookBuilder(OrderBook book, Depth initialDepth, ScheduledThreadPoolExecutor se, long updateInterval,
                    double minQty, double variation, int tickScale, double tradeProbability, int maxDepth,
                    double cancelProbability) {
            initialDepth.bids.forEach(b -> {
                var price = new BigDecimal(b.price).setScale(tickScale, RoundingMode.HALF_EVEN);
                book.addOrder(Side.BUY, b.size, price, newOrderId());
            });

            initialDepth.asks.forEach(a -> {
                var price= new BigDecimal(a.price).setScale(tickScale, RoundingMode.HALF_EVEN);
                book.addOrder(Side.SELL, a.size, price, newOrderId());
            });

            int totalBidQty = initialDepth.bids.stream().mapToInt(b-> b.size).sum();
            int totalAskQty = initialDepth.asks.stream().mapToInt(a -> a.size).sum();


            se.scheduleAtFixedRate(()-> {
                try {
                    updateBookQty(book, minQty, variation, tickScale, totalBidQty, book.getBuyOrders(), initialDepth.bids,
                            Side.BUY, maxDepth);
                    updateBookQty(book, minQty, variation, tickScale, totalAskQty, book.getSellOrders(), initialDepth.asks,
                            Side.SELL, maxDepth);

                    if (Math.random() < tradeProbability) {
                        hitTopOfBook(book, book.getSellOrders(), Side.BUY);
                    }

                    if (Math.random() < tradeProbability) {
                        hitTopOfBook(book, book.getBuyOrders(), Side.SELL);
                    }

                    if (Math.random() < cancelProbability) {
                        cancelOrder(book, book.getBuyOrders());
                    }
                } catch (Exception e) {
                    log.error("Exception in book builder"+e);
                }
            }, updateInterval, updateInterval, TimeUnit.MILLISECONDS);
        }

        private String newOrderId() {
            return BOOK_BUILDER_ORDERID_PREPEND+ UUID.randomUUID().toString() ;
        }

        private void updateBookQty(OrderBook book,  double minQty, double variation, int tickScale, int totalQty,
                                   Order[] orders, List<Depth.Line> lines, Side side, int maxDepth) {
            var qQty = Arrays.stream(orders).findFirst().stream().mapToDouble(Order::getRemainingQty).sum();
            if (qQty < totalQty * minQty && orders.length < maxDepth) {
                int idx = (int) Math.random() * lines.size();
                var line = lines.get(idx);
                var price = line.price - ((line.price * Math.random()-0.5) * variation);
                var qty = (int) (line.size - (line.size *(Math.random() -0.5) * variation));

                var bdPrice = new BigDecimal(price);
                bdPrice = bdPrice.setScale(tickScale, RoundingMode.HALF_EVEN);
                book.addOrder(side, qty, bdPrice, newOrderId());
            }
        }

        private void hitTopOfBook(OrderBook book, Order[] orders, Side side){
            if (orders.length >0){
                int numOrders = (int) (orders.length *0.5 *Math.random());
                long qty =0;
                BigDecimal price = new BigDecimal(0);

                for(int i =0; i< numOrders; i++){
                    qty = Math.round(orders[i].getRemainingQty());
                    price = orders[i].getPrice();
                }

                if (qty >0){
                    book.addOrder(side, (int) qty, price, newOrderId());
                }
            }
        }


        private void cancelOrder(OrderBook book, Order[] orders) {
            if (orders.length > 0) {
                int idx = (int) Math.round((orders.length-1) * Math.random());
                var order = orders[idx];

                if (order.getClOrdId().startsWith(BOOK_BUILDER_ORDERID_PREPEND)) {
                    try {
                        book.deleteOrder(order.getOrderId());
                    } catch (OrderDeletionException e) {
                        log.info("Failed to delete order: "+ e);
                    }
                }
            }
        }
    }
}
