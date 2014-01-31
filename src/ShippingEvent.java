package com.ebay.redlemur.codegen.event;

import org.ebayopensource.redlemur.agent.event.impl.BaseEvent;

public class ShippingEvent extends BaseEvent{
	public int siteId;
	public double amount;
	public int estimateLower;
	public int estimateHigher;
	public String estimationType;
	public String status;
	public String client;
	
	public ShippingEvent(){
		super("ShippingEvent");
	}
	
	public int getSiteId() {
		return siteId;
	}
	
	public double getAmount() {
		return amount;
	}
	
	public int getHighEstimate() {
		return estimateHigher;
	}
	
	public int getLowEstimate() {
		return estimateLower;
	}
	
	public String getStatus() {
		return status;
	}
	
	public String getEstimationType() {
		return estimationType;
	}
	
	public String getClient()
	{
		return client;
	}
}
