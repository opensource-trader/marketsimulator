package org.opensourcetrader.marketsimulator;

import lombok.extern.slf4j.Slf4j;
import org.opensourcetrader.marketsimulator.bookbuilder.BooksBuilder;
import org.opensourcetrader.marketsimulator.exchange.Exchange;
import org.opensourcetrader.marketsimulator.marketdataserver.MarketDataService;
import org.opensourcetrader.marketsimulator.orderentryserver.OrderEntryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AppGrpcRunner implements CommandLineRunner {

    MarketDataService marketDataService;
    OrderEntryService orderEntryService;
    BooksBuilder booksBuilder;
    Exchange exchange;


    @Value("${app.file_store_path}")
    static String fileStorepath;

    @Value("${app.fix_server_port}")
    static Integer fixServerPort;

    @Value("${app.target_comp_ids}")
    static String targetCompIds;

    static {
        targetCompIds = "";
    }

    AppGrpcRunner(@Autowired MarketDataService marketDataService,
                  @Autowired OrderEntryService orderEntryService,
                  @Autowired BooksBuilder booksBuilder,
                  @Autowired Exchange exchange
                   ) {
        this.marketDataService = marketDataService;
        this.orderEntryService = orderEntryService;
        this.booksBuilder = booksBuilder;
        this.exchange = exchange;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            marketDataService.start();
            orderEntryService.start();
        } catch (Exception e) {
            log.error("Error starting services:" + e.getMessage());
        } finally {
            orderEntryService.stop();
            marketDataService.stop();
            orderEntryService.blockUntilShutdown();
            marketDataService.blockUntilShutdown();
        }

    }




}
