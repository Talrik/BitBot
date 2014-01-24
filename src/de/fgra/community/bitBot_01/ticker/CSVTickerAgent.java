package de.fgra.community.bitBot_01.ticker;


import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;

import com.xeiam.xchange.dto.marketdata.Ticker;


public class CSVTickerAgent extends TickerAgent {
	
	private Ticker myTicker;
	private CurrencyUnit currency = CurrencyUnit.USD;
	private BigMoney last = BigMoney.of(currency, 0);
	private BigMoney calcPrice = last;
	private BigMoney high = BigMoney.of(currency, 0);
	private BigMoney low = BigMoney.of(currency, 1000);
	private BigDecimal volume = BigDecimal.valueOf(10000);
	private BufferedReader CSVFile;
	private String dataRow;
	private Date timestamp;
	
	protected void setup(){
				setTickerName("CSVTicker");
				setTickerType("price-ticker");
				setTickInt(100);
				try{
				CSVFile = 
				        new BufferedReader(new FileReader("bitstampUSD.csv"));

				  dataRow = CSVFile.readLine(); // Read first line.
				  // The while checks to see if the data is null. If 
				  // it is, we've hit the end of the file. If not, 
				  // process the data.
				}catch(Exception e){
					e.printStackTrace();
				}
		super.setup();
	}
	
	@Override
	public Ticker updateTicker(){
	// Get the latest ticker data showing BTC to USD
		try{
			  if (dataRow != null){
				   String[] dataArray = dataRow.split(",");
				   
				   timestamp = new Date(Long.parseLong(dataArray[0])*1000);;
				   calcPrice = BigMoney.of(CurrencyUnit.USD,BigDecimal.valueOf(Double.parseDouble(dataArray[1])));
				   volume = BigDecimal.valueOf(Double.parseDouble(dataArray[2]));
				   dataRow = CSVFile.readLine(); // Read next line of data.
				  }
				 }catch(Exception e){
					e.printStackTrace();
				}
				last = calcPrice.rounded(2, RoundingMode.HALF_UP).withScale(2);
				if(high.isLessThan(calcPrice)){high = calcPrice;}
				if(low.isGreaterThan(calcPrice)){low = calcPrice;}
				BigMoney bid = calcPrice.multipliedBy(1.001).rounded(2, RoundingMode.HALF_UP);
				BigMoney ask = calcPrice.multipliedBy(0.999).rounded(2, RoundingMode.HALF_UP);
				System.out.println(timestamp +" - New price is "+calcPrice.rounded(2, RoundingMode.HALF_UP).withScale(2));
				myTicker = Ticker.TickerBuilder.newInstance()
						.withAsk(ask)
						.withBid(bid)
						.withHigh(high)
						.withLast(last)
						.withVolume(volume)
						.withTimestamp(timestamp)
						.withTradableIdentifier("BTC").build();

		return myTicker;
	}
	
	@Override
	protected void takeDown() {
		// Printout a dismissal message
		 // Close the file once all data has been read.
		try{
			CSVFile.close();
		}catch(Exception e){
			e.printStackTrace();
		}
		  // End the printout with a blank line.
		  System.out.println();
		
		System.out.println("Ticker-agent "+getAID().getName()+" terminating.");
	}
}
	



