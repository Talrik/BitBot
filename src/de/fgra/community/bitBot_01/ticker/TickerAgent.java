package de.fgra.community.bitBot_01.ticker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;

import com.google.gson.Gson;
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

public class TickerAgent extends Agent {
	
	private String name = "Ticker";
	private String type = "price-ticker";
	private int tickInt = 1000;
	private int offset = 100;
	private int factor = 10;
	private double variation = 1;
	private CurrencyUnit currency = CurrencyUnit.USD;
	private BigMoney last = BigMoney.of(currency, offset);
	private BigMoney calcPrice = last;
	private BigMoney high = BigMoney.of(currency, 0);
	private BigMoney low = BigMoney.of(currency, 1000);
	private BigDecimal volume = BigDecimal.valueOf(10000);
	private AID[] traders;
	private Ticker ticker;
	private Gson gson = new Gson();
	
	protected void setup(){
		
		// Register the ticker service in the yellow pages
				DFAgentDescription dfd = new DFAgentDescription();
				dfd.setName(getAID());
				ServiceDescription sd = new ServiceDescription();
				sd.setType(getTickerType());
				sd.setName(getTickerName());
				dfd.addServices(sd);
				try {
					DFService.register(this, dfd);
				}
				catch (FIPAException fe) {
					fe.printStackTrace();
				}
		// Add ticker behaviour		
				addBehaviour(new TickerBehaviour(this, tickInt) {
					protected void onTick() {
						ticker = updateTicker();
						// Update the list of trading agents
						updateTraders();
						// Perform the request
						if (traders != null && traders.length != 0 ){
						myAgent.addBehaviour(new PublishPrice());
						}
					}
				} );
	}
	
	
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Ticker-agent "+getAID().getName()+" terminating.");
	}
	
	private void updateTraders(){
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType("trading");
		template.addServices(sd);
		try {
			DFAgentDescription[] result = DFService.search(this, template); 
			traders = new AID[result.length];
			for (int i = 0; i < result.length; ++i) {
//				System.out.println("Found the following trader agents:");
				traders[i] = result[i].getName();
//				System.out.println(traders[i].getName());
			}
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

	}
	
	public String getTickerName(){
		return name;
	}
	
	public void setTickerName(String name){
		this.name = name;
	}
	
	public String getTickerType(){
		return name;
	}
	
	public void setTickerType(String type){
		this.type = type;
	}
	
	public int getTickInt(){
		return this.tickInt;
	}
	
	public void setTickInt(int interval){
		this.tickInt = interval;
	}
	
	public Ticker updateTicker(){
		
		variation++;
		last = calcPrice.rounded(2, RoundingMode.HALF_UP).withScale(2);
		calcPrice = BigMoney.of(currency, offset+factor*(Math.sin(variation/10)));
		if(high.isLessThan(calcPrice)){high = calcPrice;}
		if(low.isGreaterThan(calcPrice)){low = calcPrice;}
		BigMoney bid = calcPrice.multipliedBy(1.001).rounded(2, RoundingMode.HALF_UP);
		BigMoney ask = calcPrice.multipliedBy(0.999).rounded(2, RoundingMode.HALF_UP);
		System.out.println("New price is "+calcPrice.rounded(2, RoundingMode.HALF_UP).withScale(2));
		ticker = Ticker.TickerBuilder.newInstance()
				.withAsk(ask)
				.withBid(bid)
				.withHigh(high)
				.withLast(last)
				.withVolume(volume)
				.withTradableIdentifier("BTC").build();
		return ticker;
	}
	
	private class PublishPrice extends Behaviour {
		private HashMap<AID,Order> interestedTraders = new HashMap<AID,Order>();// The agent who provide offers 
		private int repliesCnt = 0; // The counter of replies from seller agents
		private MessageTemplate mt; // The template to receive replies
		private int step = 0;

		public void action() {
			// publish the price to all traders
			switch (step) {
			case 0:
				// Send the cfp to all traders
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < traders.length; ++i) {
					cfp.addReceiver(traders[i]);
				}
				cfp.setContent(gson.toJson(ticker));
				cfp.setConversationId("priceUpdate");
				cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
				cfp.addReplyTo(getAID());
//				System.out.println("Sending price to"+ cfp.getAllReceiver().next());
				myAgent.send(cfp);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("trade-proposal"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				// Receive all proposals/refusals from seller agents
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer 
						LimitOrder order = gson.fromJson(reply.getContent(), LimitOrder.class);
//						Order order = gson.fromJson(reply.getContent(),Order.class);  
						interestedTraders.put(reply.getSender(), order);
						if (order instanceof LimitOrder){
							if(order.getType().equals(Order.OrderType.BID)){
								System.out.println(reply.getSender()+" wants to buy " + order.getTradableAmount() + " " + order.getTradableIdentifier() + " for up to " +((LimitOrder)order).getLimitPrice());
							}else{	System.out.println(reply.getSender()+" wants to sell " + order.getTradableAmount() + " " + order.getTradableIdentifier() + " for not less than " +((LimitOrder)order).getLimitPrice());
							}
							}else{
								if(order.getType().equals(Order.OrderType.BID)){
									System.out.println(reply.getSender()+" wants to buy " + order.getTradableAmount() + " " + order.getTradableIdentifier()+".");
								}
								else{
							System.out.println(reply.getSender()+" wants to sell " + order.getTradableAmount() + " " + order.getTradableIdentifier() +".");	
						}
					}
					repliesCnt++;
					if (repliesCnt >= traders.length) {
						// We received all replies
						step = 2; 
					}
				}
				else {
					block();
				}}
				break;
			case 2:
				// Send the purchase order to the traders that provided offers
				for (Map.Entry<AID, Order> entry : interestedTraders.entrySet())
				{
					ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
					order.addReceiver(entry.getKey());
					order.setContent(String.valueOf(entry.getValue()));
					order.setConversationId("confirm-trading");
					myAgent.send(order);
				}
				break;
			}        
		}

		public boolean done() {
			return (step == 2);
		}
	}  // End of inner class RequestPerformer
	
}
