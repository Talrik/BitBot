package de.fgra.community.bitBot_01.trader;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;

import org.joda.money.Money;
import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import com.google.gson.Gson;
import com.xeiam.xchange.currency.Currencies;
import com.xeiam.xchange.currency.MoneyUtils;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.Order.OrderType;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.Wallet;

public class TraderAgent extends Agent {
	
	private AccountInfo account;
	
	private BigMoney usd = BigMoney.of(CurrencyUnit.USD, 20);;
	private BigMoney stock = BigMoney.of(CurrencyUnit.of(Currencies.BTC), 0);
	private Wallet usdWallet;
	private Wallet btcWallet;
	private double fee = 0.2;
	private BigMoney limit = BigMoney.of(CurrencyUnit.USD, 15);
	private double stop_loss = 90;
	private double minimal_win = 5;
	private Ticker ticker = Ticker.TickerBuilder.newInstance().build();
	private BigMoney oldStockPrice;
	private BigMoney stockPrice;
	private double old_stock_1st_derivative = 0;
	private double stock_1st_derivative = 0;
	private double stock_1st_deriv_num = 0;
	private double stock_2nd_derivative = 0;
	private String state = "unknown";
	private double prob_stop_loss_sell = 0;
	private BigMoney last_payed_stock_price;
	private Gson gson = new Gson();
	private boolean tradeToTicker = true;
	private boolean order;
	private ACLMessage msg;
	
	private AID tickerAgent;
	
	protected void setup() {
		// Printout a welcome message
		System.out.println("Hello! Trader-agent "+getAID().getName()+" is ready.");
		account = new AccountInfo(getAID().getName(), null);
		
		// Get the budget of the agent as a start-up argument
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			usd = BigMoney.parse((String) args[0]);
		}
		usdWallet = new Wallet("USD", usd); 
			System.out.println("Budget in "+usdWallet.getBalance());
		if(args != null && args.length > 1){
			stock= BigMoney.parse((String) args[1]);
		}
		btcWallet = new Wallet(Currencies.BTC,stock);
			System.out.println("Budget in "+btcWallet.getBalance());
		if(args != null && args.length > 2){	
			fee = Integer.parseInt((String) args[2]);
		}
			System.out.println("Fee is "+fee+"%");
		if(args != null && args.length > 3){
			limit = BigMoney.parse((String) args[3]);
		}
			System.out.println("Limit in "+limit);
		
		account = new AccountInfo(getAID().getName(), null);
			
		
		// Register the book-selling service in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("trading");
		sd.setName("Test-Trader");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		
		// Add a Behaviour that looks up prices every 5 seconds
		this.addBehaviour(new BitBotTrade());
			
		
	}
	/**
	 * @author phil
	 *
	 */
	private class BitBotTrade extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			msg = myAgent.receive(mt);
			if (msg != null) {
				// CFP Message received. Process it
				ticker = gson.fromJson(msg.getContent(),ticker.getClass());  

				if (stockPrice == null){stockPrice = ticker.getLast();}
//				System.out.println("Message received, last ticker price is "+ticker.getLast());
//				ACLMessage reply = msg.createReply();
				
				// calculate current price
				oldStockPrice = stockPrice;
				stockPrice = ticker.getLast();
				// calculating 1st and 2nd derivative of price development
				old_stock_1st_derivative = stock_1st_derivative;
//				System.out.println("Old Stock 1st Derivative: "+old_stock_1st_derivative);
				stock_1st_derivative = stockPrice.getAmount().subtract(oldStockPrice.getAmount()).doubleValue();
//				System.out.println("Stock 1st Derivative: "+stock_1st_derivative);
				stock_2nd_derivative = stock_1st_derivative-old_stock_1st_derivative;		
//				System.out.println("Stock 2nd Derivative: "+stock_2nd_derivative);
				
				if (stock_1st_derivative < 0){
					stock_1st_deriv_num = (stock_1st_deriv_num <= 0)?stock_1st_deriv_num - 1 : 0;
				}
				if (stock_1st_derivative > 0){
					stock_1st_deriv_num = (stock_1st_deriv_num >= 0)?stock_1st_deriv_num + 1 : 0;
				}
				// don't trade if state is unknown
				if (state.equals("unknown")){
					if (stock_1st_deriv_num < -3) {
						stock_1st_deriv_num=0;
						state = "falling";
					}
					if (stock_1st_deriv_num > 3) {
						stock_1st_deriv_num=0;
						state = "rising";
					}
					refuseToTrade();
				}
				// trade according to these rules
				else{
					if (stock_1st_deriv_num < -3){ 
						stock_1st_deriv_num = 0;
						// really falling
						if (state.equals("stop_loss_falling")){
							stopLossTrade();
						}						
						else{
							fallingTrade();
						}
						
					}else if (stock_1st_deriv_num > 3){
						stock_1st_deriv_num = 0;
						// really rising
						risingTrade();
						
					}
					else{
					//	System.out.println("Price is constant ... no need to trade");
						refuseToTrade();
						// price constant - do nothing
					}
				}
				System.out.println("Price: "+stockPrice +" - State "+state+ " - Trend: "+stock_1st_deriv_num);	
			}
			else{
				block();
			}
		}
		private void refuseToTrade(){
			ACLMessage reply = msg.createReply();
			reply.setPerformative(ACLMessage.REFUSE);
			reply.setConversationId("trade-proposal");
			reply.setContent("0");
		//	System.out.println("Sending Message to refuse trade."); 
			myAgent.send(reply);
		}
		private String placeOrder(Order order){
			ACLMessage reply = msg.createReply();
			reply.setPerformative(ACLMessage.PROPOSE);
			reply.setContent(gson.toJson(order, order.getClass()));
			reply.setConversationId("trade-proposal");
			reply.setReplyWith(order.getId());
			myAgent.send(reply);
			//MessageTemplate mt = MessageTemplate.MatchConversationId("confirm-trading");/*
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchConversationId("confirm-trading"),
					MessageTemplate.MatchInReplyTo(reply.getReplyWith()));
					
			ACLMessage confirm = myAgent.blockingReceive(mt, 1000);
			if (confirm != null){
				return confirm.getContent();
			}
			else return null;
		}
		
/*
		private BigDecimal calculate_sales_usd_to_stock(Object userAgent,BigMoney price, BigMoney salesVol, double fee, Wallet targetWallet){
			System.out.println("To buy stock with "+ salesvol +" you get:");
			BigMoney cfee = salesvol.minus(salesvol.multipliedBy(1-(fee/100)).rounded(2, RoundingMode.HALF_UP));
			System.out.println("Fee: "+ cfee );
			BigMoney cmoney = salesvol.minus(cfee);
			System.out.println("Money to spend: "+ cmoney);
			BigMoney stock = BigMoney.of(CurrencyUnit.of(targetWallet.getCurrency()),cmoney.getAmount().divide((price.getAmount()), RoundingMode.HALF_UP));
			System.out.println("Stock you get: "+stock);				
					return stock.getAmount();
			//TODO use the order book
			/*
			 my $order_book = BitstampOpenFunction($ua, "order_book",());
			if (!$order_book) {return undef;}

			my @asks = @{ $order_book->{"asks"} }; 
			my $sale_amount;
			my $sales=0;
			foreach my $a (@asks){
				my ($price, $amount) = @{ $a };
				if ($USD < $price * $amount) {
					$sale_amount = $USD;
					$USD = 0;
				} else {
					$sale_amount = $amount * $price;
					$USD -= $amount * $price;
				}
				$sales += ($sale_amount / $price) * (1-$fee/100);
				if ($USD==0) {last;}
			}
			return sales;
			
		}
	*/
		private BigMoney calculate_sales_stock_to_usd(Object userAgent, BigMoney price, BigMoney salesVol, double fee, Wallet targetWallet){
	return null;
	/*
			System.out.println("To sell the stock of "+ salesVol +" you get:");
			BigMoney cfee = price.multipliedBy(salesVol.getAmount()).multipliedBy((fee/100)).rounded(2, RoundingMode.HALF_UP);
			System.out.println("Fee: "+ cfee );
			BigMoney cmoney = salesvol.minus(cfee);
			System.out.println("Stock to spend: "+ cmoney);
			BigMoney stock = BigMoney.of(CurrencyUnit.of(targetWallet.getCurrency()),cmoney.getAmount().divide((price.getAmount()), RoundingMode.HALF_UP));
			System.out.println("Money you get: "+stock);				
					return stock.getAmount();
			return Wallet.createInstance(usdWallet.getCurrency(), wallet.getBalance().getAmount().multiply(ticker.getLast().getAmount().multiply(BigDecimal.valueOf(1-(fee/100))))).getBalance();
			
					//TODO use the order book
			/*
			my $order_book = BitstampOpenFunction($ua, "order_book",());
	
			if (!$order_book) {return undef;}

			my @bids = @{ $order_book->{"bids"} }; 
			my $sale_amount;
			my $sales=0;
			foreach my $b (@bids){
				my ($price, $amount) = @{ $b };
				if ($BTC < $amount) {
					$sale_amount = $BTC;
					$BTC = 0;
				} else {
					$sale_amount = $amount;
					$BTC -= $amount;
				}
				$sales += $sale_amount * $price * (1-$fee/100);
				if ($BTC==0) {last;}
			}
			return $sales;
			*/
		}
		
		private boolean simpleTrade(Object userAgent, BigMoney price, BigMoney vol, Wallet sourceWallet, double fee, Wallet targetWallet){
			System.out.println("Transfer of "+ vol +" to "+ targetWallet.getCurrency() +":");
			BigMoney cfee, cmoney, stock;
			OrderType orderType;
			BigDecimal tradeableAmount;
			String tradableIdentifier;
			if(sourceWallet.getCurrency().equalsIgnoreCase("USD")){
				// we buy stocks
				orderType = (OrderType.BID);
				if (vol.getCurrencyUnit().equals(price.getCurrencyUnit())){
					// we buy for a specified amount of money
					cfee = vol.multipliedBy(fee/100).rounded(2, RoundingMode.HALF_UP);
					cmoney = vol.minus(cfee);
					stock = BigMoney.of(CurrencyUnit.of(targetWallet.getCurrency()),cmoney.getAmount().divide((price.getAmount()), RoundingMode.HALF_UP));
					System.out.println("Fee: "+cfee.withScale(2) );
					System.out.println("You spend: "+ cmoney.withScale(2) + " and a fee of "+ cfee.withScale(2));
					System.out.println("You get: "+stock);
					tradeableAmount = stock.getAmount();
					tradableIdentifier = stock.getCurrencyUnit().getCurrencyCode();
				}
				else{
					// we buy a specific amount of stocks
					cfee = price.multipliedBy((vol.getAmount().multiply(BigDecimal.valueOf(fee/100))));
					cmoney = price.multipliedBy(vol.getAmount());
					stock = vol;
					System.out.println("Fee: "+ cfee.withScale(2) );
					System.out.println("You spend: "+ (cmoney.plus(fee)).withScale(2) + " (incl. a fee of "+ cfee.withScale(2)+")");
					System.out.println("You get: "+stock);
					tradeableAmount = stock.getAmount();
					tradableIdentifier = stock.getCurrencyUnit().getCurrencyCode();
				}
			}
			else {
				// we sell stocks
				orderType = (OrderType.ASK);
				if (vol.getCurrencyUnit().equals(price.getCurrencyUnit())){
					// we sell stocks for a specific amount of money
					cfee = vol.multipliedBy(BigDecimal.valueOf(fee/100));
					cmoney = vol;
					stock = BigMoney.of(CurrencyUnit.of(targetWallet.getCurrency()),cmoney.getAmount().divide((price.getAmount()), RoundingMode.HALF_UP));
					System.out.println("Fee: "+ cfee.withScale(2) );
					System.out.println("You spend: "+ cmoney);
					System.out.println("You get: "+stock.withScale(2) + " (incl. fee of "+cfee.withScale(2)+")");
					orderType = (OrderType.ASK);
					tradeableAmount = stock.getAmount();
					tradableIdentifier = cmoney.getCurrencyUnit().getCurrencyCode();
			
				}else{
					// we sell a specific amount of stocks
					cfee = price.multipliedBy(vol.getAmount().multiply(BigDecimal.valueOf(fee/100))).withScale(2, RoundingMode.DOWN);
					cmoney = price.multipliedBy(vol.getAmount()).withScale(2, RoundingMode.DOWN);
					stock = vol;
					System.out.println("Fee: "+ cfee );
					System.out.println("You spend: "+ vol);
					System.out.println("You get: "+ cmoney.minus(cfee) + " (incl. fee of "+cfee+")");
					tradeableAmount = stock.getAmount();
					tradableIdentifier = vol.getCurrencyUnit().getCurrencyCode();
				}
			}
			String transactionCurrency = price.getCurrencyUnit().getCurrencyCode();
			BigMoney limitPrice = price;
			LimitOrder limitOrder = new LimitOrder(orderType, tradeableAmount, tradableIdentifier, transactionCurrency, msg.getReplyWith(), new Date(),limitPrice );
			String tradeResult = placeOrder(limitOrder);
			if (tradeResult!=null){
				if (sourceWallet.equals(btcWallet)){
					usdWallet = Wallet.createInstance(usdWallet.getCurrency(), usdWallet.getBalance().plus(cmoney.withScale(2)).minus(cfee.withScale(2)).withScale(2).getAmount());
					btcWallet = Wallet.createInstance(btcWallet.getCurrency(), btcWallet.getBalance().minus(stock).getAmount());
				}else{
					usdWallet = Wallet.createInstance(usdWallet.getCurrency(), usdWallet.getBalance().minus(cmoney.withScale(2)).minus(cfee.withScale(2)).withScale(2).getAmount());
					btcWallet = Wallet.createInstance(btcWallet.getCurrency(), btcWallet.getBalance().plus(stock).getAmount());
				}
				last_payed_stock_price = price.multipliedBy(1+(fee/100)).withScale(2, RoundingMode.UP);
				return true;
			}else{
				return false;
			}	
		};
		
		private void stopLossTrade(){
			if (currentStockValue(btcWallet).isPositive() ){
			System.out.println("stopLossTrade:");
			BigMoney currentStockValue = currentStockValue(btcWallet).withScale(2, RoundingMode.DOWN);
			System.out.println("Current value:"+currentStockValue);
			BigMoney paidStockValue = last_payed_stock_price.multipliedBy(btcWallet.getBalance().getAmount()).withScale(2,RoundingMode.DOWN); 
			System.out.println("Paid :"+paidStockValue);
			BigDecimal currentLoss = currentStockValue.dividedBy(paidStockValue.getAmount(), RoundingMode.HALF_UP).getAmount();
			System.out.println("Loss factor:"+currentLoss);
			//if stop loss threshold is already reached sell everything
			boolean order = false;
			if(currentLoss.multiply(BigDecimal.valueOf(100)).doubleValue() < stop_loss){
				BigDecimal amount = btcWallet.getBalance().getAmount();
				BigMoney price = ticker.getBid();
					if (amount.multiply(price.getAmount()).doubleValue() > 1.00){
						order = simpleTrade( null, price, btcWallet.getBalance(), btcWallet, fee,  usdWallet);
						} else { order = false;}     
				if (order){
					String text="Sold BTC at "+ticker.getBid()+" USD because of stop loss;   USD balance: "+usdWallet.getBalance()+
    	                           "   BTC balance: "+btcWallet.getBalance()+"\n";
				System.out.println(text);
				}
			}else{
				
			//now sell with an increasing probability even with loss
			    
				prob_stop_loss_sell++;
				double panicFactor = prob_stop_loss_sell/(6+prob_stop_loss_sell); 
			    System.out.println("Panic factor increased to : "+panicFactor+"\n");
			     if(Math.random() < panicFactor ){  // use a simulated annealing similar approach
            
              		BigMoney price = ticker.getBid();
					if (btcWallet.getBalance().getAmount().multiply(price.getAmount()).doubleValue() > 1.00){
						order = simpleTrade( null, price, btcWallet.getBalance(), btcWallet, fee,  usdWallet);
					}
					else { order = false;}
                                    
					if (order){
					String text="Sold BTC at "+ticker.getBid()+" because of permanent price loss\n   USD balance: "+usdWallet.getBalance()+
                                    	                "   BTC balance: "+btcWallet.getBalance()+"\n";
                    System.out.println(text);
					}
			    }
			}
			}
		}
		
		private void fallingTrade(){

			System.out.println("fallingTrade:");
			state="falling";
			
			BigMoney win = (ticker.getLast().multipliedBy(btcWallet.getBalance().getAmount()).multipliedBy(1-(fee/100))).minus(last_payed_stock_price.multipliedBy(btcWallet.getBalance().getAmount()));
			if (win.isPositive()) {
				BigMoney sales = ticker.getLast().multipliedBy(btcWallet.getBalance().getAmount()).multipliedBy(1-(fee/100));
			   System.out.println("last_payed: "+last_payed_stock_price+"\n"+
				"BTC: "+btcWallet.getBalance()+"\n"+
				"win: "+ win.withScale(2, RoundingMode.DOWN) + " "+(1+(minimal_win/100))+"\n"+
				"sales: "+sales+"\n");
				
			   if (win.isGreaterThan(last_payed_stock_price.multipliedBy(btcWallet.getBalance().getAmount()).multipliedBy(minimal_win/100))){
				// now selling all BTC
				   /*
				# first transfer 1 percent of earnings to own account
				#my $amount = int((($sales - $volume) / $ticker->{"bid"})*
				#			0.01*100000000) / 100000000;
				#my $order = BitstampPrivateFunction($ua, $config, "bitcoin_withdrawal",
	                                #   (amount => $amount, address => '131vhGzPugSHuCggJjxhbwFoVosEVgTom5'));
	               */
				// TODO: recalculate number of existing bitcoins as they reduced by 
				// 1 percent earnings!
				// now sell all bitcoins
				// if ($config->{"simulate-orders"} eq "no"){
					BigMoney price = ticker.getBid().multipliedBy(1.005);
					if (sales.getAmount().doubleValue() > 1.00) {
						order = simpleTrade( null, price, btcWallet.getBalance(), btcWallet, fee,  usdWallet);

					} else { order = false;}
				}
				if (order){
					/*
					last_payed_stock_price = sales.dividedBy(btcWallet.getBalance().getAmount(),RoundingMode.HALF_UP);
					usdWallet = Wallet.createInstance(usdWallet.getCurrency(), usdWallet.getBalance().plus(sales).getAmount());
					btcWallet = Wallet.createInstance(btcWallet.getCurrency(), BigDecimal.ZERO);
					*/
					String text="Sold BTC at "+last_payed_stock_price+" USD\n   USD balance: "+usdWallet.getBalance()+
					         "   BTC balance: "+btcWallet.getBalance()+"\n";
				//	DebianMail('Sold BTC',$text);
					System.out.println(text);
				//	#Log $USD, $BTC, $price;
				} else { System.out.println("Error while ordering!\n"); }
			   } else 
			   {
				if (btcWallet.getBalance().isPositive()){
					System.out.println("Start stop loss phase");
					state="stop_loss_falling";
					prob_stop_loss_sell=0;
				}
			   }
		}
		
		private void risingTrade(){
			state="rising";
			System.out.println(usdWallet.getBalance() +" "+
					btcWallet.getBalance()+"\n"+
					"Price: "+ticker.getAsk()+"\n"+
					"Limit: "+limit);
                        	if (usdWallet.getBalance().isPositive() && btcWallet.getBalance().getAmount().multiply(ticker.getAsk().getAmount()).compareTo(limit.getAmount()) <=0){
					// now buying as much BTC as possible within LIMIT
					BigMoney salesvol = limit.minus(btcWallet.getBalance().convertedTo(CurrencyUnit.USD, ticker.getAsk().getAmount()));
					if (usd.isLessThan(salesvol)) { salesvol = usd; }
					System.out.println("Salesvol: "+salesvol+"\n");
					
					BigMoney	price = ticker.getAsk().multipliedBy(1.005);
						if (salesvol.isGreaterThan(BigMoney.of(CurrencyUnit.USD, 1))) {
							order = simpleTrade(null, price, salesvol, usdWallet, fee, btcWallet);
					
						} else { order = false; }
					
					if (order){
								   //last_payed_stock_price=((last_payed_stock_price.multipliedBy(btcWallet.getBalance().getAmount()).plus(salesvol)).dividedBy(btcWallet.getBalance().getAmount().add(usd_to_sell.getAmount()), RoundingMode.HALF_UP));
							           String text="Bought BTC at "+last_payed_stock_price+" USD\n   USD balance: "+usdWallet.getBalance()+
                			                          "   BTC balance: "+btcWallet.getBalance()+"\n";
                        	        //	DebianMail('Bought BTC',$text);
	                                	System.out.println(text);
						//Log $USD, $BTC, $salesvol $BTC;
					} 
					else { System.out.println("Error while ordering!\n"); }
	                        }
		}
		
		private BigMoney currentStockValue(Wallet wallet){
			return ticker.getBid().multipliedBy(BigDecimal.valueOf(1-(fee/100)).multiply(wallet.getBalance().getAmount())).rounded(2, RoundingMode.HALF_DOWN);
		}
		
		/*
		private String ExchangePrivateFunction(Object userAgent, String config, String request, String params)
		{
			my ($ua, $config, $request, %params) = @_;
			my $nonce=time;
			my $response;

			my $signature=uc hmac_sha256_hex($nonce.$config->{"id"}.$config->{"key"}, $config->{"secret"}); 
			$params{'key'}=$config->{"key"};
			$params{'signature'}=$signature;
			$params{'nonce'}=$nonce;
			
			$response=$ua->request(POST 'https://www.bitstamp.net/api/'.$request.'/',\%params);

			if ($response->is_success){
				my $content=decode_json $response->content;
				if ($content && $content->{"error"}){
					#my @errors = @{ $content->{"error"}->{"__all__"} };
					#foreach my $e (@errors){
					#	printf("Error in ".$request." request: ".$e."\n");
					#}
					printf($response->content."\n");
					return undef;
				}
				return $content;
			}
			return undef;
		}
		*/
	}  // End of inner class Trade
	
}