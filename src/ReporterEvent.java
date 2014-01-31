package com.ebay.redlemur.codegen.event;

import org.ebayopensource.redlemur.agent.eventprocessor.operators.outcome.IOperatorOutcome;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReporterEvent {
	String   eventType; //Probably move to enums
	String   outcomeName;
	String[] outcomeKeyNames;
	String[] outcomeKeyValues;
	
	String[] outcome;
	String[] outcomeValueNames;
	String[] outcomeValueValues;
	
	IOperatorOutcome  outcomeValue;
	
	private static String DELIMITER = "#";
	private static String KEY_DELIMITER = "::";
	
	/* Returns each outcome in the following format
	 *      eventype#outcomename#key1=val1::key2=val2::key3=val3#outcomeValue
	 * e.g
	 *      CheckoutEvent#Checkout.By(PaymentUsed-Status).Count#PaymentUsed=CC::Status=Fail#value=200 
	 */
	public String serializeInDetailAsString() {
		StringBuffer sb = new StringBuffer();
		sb.append(eventType); sb.append(DELIMITER);
		sb.append(outcomeName); sb.append(DELIMITER);
		for(int i=0;i<outcomeKeyNames.length;i++) {
			if(i!=0) {
				sb.append(KEY_DELIMITER);
			}
			sb.append(outcomeKeyNames[i]); sb.append("=");
			sb.append(outcomeKeyValues[i]);
		}
		sb.append(DELIMITER);
		sb.append(outcomeValue.toString());
		
		return sb.toString();
	}
	
	/* Returns each outcome in the following format
	 *      eventypeid#outcomenameid#val1::val2::val3#outcomeValue
	 * e.g
	 *      1#2#CC::Fail#200 
	 */
	public String serializeInCompactAsString() {
		StringBuffer sb = new StringBuffer();
		sb.append(getEventTypeId(eventType)); sb.append(DELIMITER);
		sb.append(getOutcomeNameId(outcomeName)); sb.append(DELIMITER);
		for(int i=0;i<outcomeKeyValues.length;i++) {
			if(i!=0) {
				sb.append(KEY_DELIMITER);
			}
			sb.append(outcomeKeyValues[i]);
		}
		sb.append(DELIMITER);
		sb.append(outcomeValue.toString());		
		return sb.toString();
	}
	
	/* Returns each outcome in the following format
	 *      eventype#outcomename#key1=val1::key2=val2::key3=val3#outcomeValue
	 * e.g
	 *      {"eventtype":"CheckoutEvent",
	 *       "outcomeName":"Checkout.By(PaymentUsed-Status).Count",
	 *       "keys": [ {"PaymentUsed":"CC"}, {"Status":"Fail"}],
	 *       "outcomeValue":"200"
	 *       } 
	 */
	public JSONObject serializeAsJSON() throws Exception{
		JSONObject outputJSONObject = new JSONObject();
		outputJSONObject.put("eventtype", eventType);
		outputJSONObject.put("outcomeName", outcomeName);
		
		JSONArray keys = new JSONArray();
		for(int i=0;i<outcomeKeyNames.length;i++) {
			JSONObject keyJSONObject = new JSONObject();
			keyJSONObject.put(outcomeKeyNames[i], outcomeKeyValues[i]);
			keys.put(keyJSONObject);
		}
		outputJSONObject.put("keys",keys);
		outputJSONObject.put("outputValue", outcomeValue.toJson().toString());
		
		return outputJSONObject;
	}
	
	private static int getEventTypeId(String eventType) {
		return 1;
	}
	
	private static int getOutcomeNameId(String outcomeName) {
		return 1;
	}
}
