package com.ebay.redlemur.codegen.event;

import org.ebayopensource.redlemur.agent.event.impl.BaseEvent;

import com.ebay.redlemur.codegen.types.ChannelType;
import com.ebay.redlemur.codegen.types.ErrorCode;
import com.ebay.redlemur.codegen.types.PaymentType;
import com.ebay.redlemur.codegen.types.StatusType;

public class CheckoutEvent extends BaseEvent{
	public int siteId;
	public double amount;
	public StatusType status;
	public ChannelType channel;
	public PaymentType paymentUsed;
	public ErrorCode errorCode;
	
	public CheckoutEvent(){
		super("CheckoutEvent");
	}
	
	public int getSiteId() {
		return siteId;
	}
	
	public double getAmount() {
		return amount;
	}
	
	public StatusType getStatus() {
		return status;
	}
	
	public ChannelType getChannel() {
		return channel;
	}
	
	public PaymentType getPaymentUsed() {
		return paymentUsed;
	}
	
	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
