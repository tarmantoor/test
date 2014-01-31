package com.ebay.redlemur.codegen.event;

import org.ebayopensource.redlemur.agent.event.impl.BaseEvent;

import com.ebay.redlemur.codegen.types.DeviceType;
import com.ebay.redlemur.codegen.types.IDPType;
import com.ebay.redlemur.codegen.types.StatusType;

public class LoginEvent extends BaseEvent{
   public DeviceType device;
   public StatusType loginResult;
   public IDPType idp;
   public int passwordLength;
   public boolean isUserIdentifiedPriorLogin;
   public boolean didUserLoginAsIdentifiedUser;
   public int noOfAttemptsBeforeSuccess;
   
	public LoginEvent(){
		super("LoginEvent");
	}
	
	public DeviceType getDevice() {
		return device;
	}
	
	public StatusType getLoginResult() {
		return loginResult;
	}
	
	public IDPType getIdp() {
		return idp;
	}
	
	public int getPasswordLength() {
		return passwordLength;
	}
	
	public boolean getIsUserIdentifiedPriorLogin() {
		return isUserIdentifiedPriorLogin;
	}
	
	public boolean getDidUserLoginAsIdentifiedUser() {
		return didUserLoginAsIdentifiedUser;
	}
	
	public int getNoOfAttemptsBeforeSuccess() {
		return noOfAttemptsBeforeSuccess;
	}
}
