package de.fgra.community.bitBot_01.ticker;

import java.io.IOException;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.Currencies;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.service.polling.PollingMarketDataService;

public class BTCeTickerAgent extends TickerAgent {

	private Ticker myTicker;
	private Exchange btce;
	private PollingMarketDataService marketDataService;
	
	protected void setup(){
		setTickerName("BTCeTicker");
		setTickerType("price-ticker");
		setTickInt(5000);
		btce = ExchangeFactory.INSTANCE.createExchange("com.xeiam.xchange.btce.v3.BTCEExchange");
		marketDataService = btce.getPollingMarketDataService();
		super.setup();
	}
	
	@Override
	public Ticker updateTicker(){
	// Get the latest ticker data showing BTC to USD
		try {
			myTicker = marketDataService.getTicker(Currencies.BTC, Currencies.USD);
		} catch (ExchangeException
				| NotAvailableFromExchangeException
				| NotYetImplementedForExchangeException
				| IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return myTicker;
	}
}
