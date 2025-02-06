package org.opensourcetrader.marketsimulator.api;


import org.opensourcetrader.marketsimulator.exchange.Exchange;
import org.opensourcetrader.marketsimulator.exchange.OrderBook;
import org.opensourcetrader.marketsimulator.exchange.Side;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;


@RestController
@RequestMapping("/api/exchangesimulator")
public class ExchangeSimulator {
    Exchange exchange;

    public ExchangeSimulator(Exchange exchange) {
        this.exchange = exchange;
    }

    @GetMapping(value = "/book", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getBook(@RequestParam String symbol){
        OrderBook orderBook = exchange.getOrderBook(symbol);
        var buys = orderBook.getBuyOrders();
        var sells = orderBook.getSellOrders();

        var result = "Bids\n";
        for (var bid : buys) {
            result += bid.getRemainingQty() + "@" + bid.getPrice() + "\t" + bid.getClOrdId() +"\n";
        }

        result += "\nAsks\n";
        for(var ask : sells) {
            result += ask.getRemainingQty() + "@" + ask.getPrice() + "\t" + ask.getClOrdId() +"\n";
        }

        return result;
    }

    @PostMapping(value = "/order", produces = MediaType.APPLICATION_JSON_VALUE)
    public String addOrder(@RequestParam("Symbol") String symbol,
                           @RequestParam("Quantity") String qty,
                           @RequestParam("Price") BigDecimal price,
                           @RequestParam("Side") boolean sideBoolean) {
        OrderBook orderBook = exchange.getOrderBook(symbol);
        Side side = sideBoolean ? Side.BUY : Side.SELL;
        return orderBook.addOrder(side, Integer.parseInt(qty), price, "");
    }
}
