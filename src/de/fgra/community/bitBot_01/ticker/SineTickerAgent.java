package de.fgra.community.bitBot_01.ticker;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;

import com.google.gson.Gson;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.Currencies;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.trade.LimitOrder;

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

public class SineTickerAgent extends TickerAgent{

	private int offset = 100;
	private int factor = 10;
	private double variation = 1;
	private CurrencyUnit currency = CurrencyUnit.USD;
	private BigMoney last = BigMoney.of(currency, offset);
	private BigMoney calcPrice = last;
	private BigMoney high = BigMoney.of(currency, 0);
	private BigMoney low = BigMoney.of(currency, 1000);
	private BigDecimal volume = BigDecimal.valueOf(10000);
	private Ticker myTicker;

	protected void setup(){
		setTickerName("SineTicker");
		setTickerType("price-ticker");
		setTickInt(5000);
		super.setup();
	}

	@Override
	public Ticker updateTicker(){
		// Get the latest ticker data showing BTC to USD
		variation++;
		last = calcPrice.rounded(2, RoundingMode.HALF_UP).withScale(2);
		calcPrice = BigMoney.of(currency, offset+factor*(Math.sin(variation/10)));
		if(high.isLessThan(calcPrice)){high = calcPrice;}
		if(low.isGreaterThan(calcPrice)){low = calcPrice;}
		BigMoney bid = calcPrice.multipliedBy(1.001).rounded(2, RoundingMode.HALF_UP);
		BigMoney ask = calcPrice.multipliedBy(0.999).rounded(2, RoundingMode.HALF_UP);
		System.out.println("New price is "+last);
		myTicker = Ticker.TickerBuilder.newInstance()
				.withAsk(ask)
				.withBid(bid)
				.withHigh(high)
				.withLast(last)
				.withVolume(volume)
				.withTradableIdentifier("BTC").build();
		return myTicker;
	}




}


