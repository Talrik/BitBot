package de.fgra.community.bitBot_01.ticker;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.Currencies;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.service.polling.PollingMarketDataService;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class KrakenTickerAgent extends TickerAgent {

	private Ticker myTicker;
	private Exchange kraken;
	private PollingMarketDataService marketDataService;
	
	protected void setup(){

		setTickerName("KrakenTicker");
		setTickerType("price-ticker");
		setTickInt(5000);
		kraken = ExchangeFactory.INSTANCE.createExchange("com.xeiam.xchange.kraken.KrakenExchange");
		marketDataService = kraken.getPollingMarketDataService();
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





