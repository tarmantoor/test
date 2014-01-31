
package com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import com.ebay.app.common.util.StringUtil;
import com.ebay.domain.SharedShippingDomainBiz.enums.RunaDeliveryEstimateTestTypeEnum;
import com.ebay.domain.core.common.type.Pair;
import com.ebay.domain.lookup.common.siteparm.SiteParameterHelper;
import com.ebay.domain.sharedbuying.biz.shipping.impl.ThirdPartyCallHelper;
import com.ebay.domain.shipping.biz.RunaDeliveryEstimateConfigBean;
import com.ebay.globalenv.util.StringUtils;
import com.ebay.integ.account.common.CountryEnum;
import com.ebay.integ.dal.dao.CreateException;
import com.ebay.integ.dal.dao.FinderException;
import com.ebay.integ.dal.dao.UpdateException;
import com.ebay.integ.shippingdeliveryestmttrack.ShippingDeliveryEstimateTrack;
import com.ebay.integ.shippingdeliveryestmttrack.ShippingDeliveryEstimateTrackDAO;
import com.ebay.kernel.calwrapper.CalEventHelper;
import com.ebay.kernel.calwrapper.CalTransaction;
import com.ebay.kernel.calwrapper.CalTransactionFactory;
import com.ebay.marketplace.personalization.v1.services.geolocationservice.GetLocationByIpResponse;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.AnalyticalDeliveryEstimateRequests;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.AnalyticalDeliveryEstimateResponses;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.AnalyticalDeliveryEstimatesResult;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.BatchAnalyticalDeliveryEstimatesRequest;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.BatchAnalyticalDeliveryEstimatesResponse;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.DeliveryEstimate;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.EstimationTypeFlag;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.FnFFlag;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.FullBatchAnalyticalDeliveryEstimatesResponse;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.GetVersionRequest;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.GetVersionResponse;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.Item;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.ItemDeliveryEstimate;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.ServiceDeliveryEstimate;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.ShippingDeliveryEstimateClient;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.ShippingDeliveryEstimateService;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.ShippingService;
import com.ebay.marketplace.shipping.v1.services.shippingdeliveryestimateservice.impl.DeliveryEstimateLoggingHelper.LoggingFormat;

public class ShippingDeliveryEstimateServiceImpl
    implements ShippingDeliveryEstimateService
{
	private static final String VERSION = "1.0";
	private static final String SERVICE_TXN_NAME = "TOTAL_SERVICE";
	private static final String GEOLOCATION_NAME = "SDES_Geolocation";

	private static final String EVENT_TYPE = "DeliveryEstimateServiceImpl";
	private static final int tzOffset = TimeZone.getDefault().getOffset(new Date().getTime()) / 60000;
	private static String stringStatus;
	
	
	@Override
	public GetVersionResponse getVersion(GetVersionRequest getVersionRequest) {
		GetVersionResponse response = new GetVersionResponse();
		response.setVersion(VERSION);
		return response;
	}

	@Override
	public AnalyticalDeliveryEstimateResponses getAnalyticalDeliveryEstimates(
			AnalyticalDeliveryEstimateRequests analyticalDeliveryEstimateRequests) {
		return new AnalyticalDeliveryEstimateResponses();
	}

	@Override
	public BatchAnalyticalDeliveryEstimatesResponse getBatchAnalyticalDeliveryEstimates(BatchAnalyticalDeliveryEstimatesRequest request) {

		FullBatchAnalyticalDeliveryEstimatesResponse fullResponse = getAnalyticalDeliveryEstimatesInternal(request, true);
		if(fullResponse == null)
		{
			return null;
		}
		BatchAnalyticalDeliveryEstimatesResponse response = new BatchAnalyticalDeliveryEstimatesResponse();
		response.setAck(fullResponse.getAck());
		response.setErrorMessage(fullResponse.getErrorMessage());
		response.setTimestamp(fullResponse.getTimestamp());
		response.setVersion(fullResponse.getVersion());
		stringStatus = response.getAck().value();
		System.out.println("Status value is  ******  "  + stringStatus);
		for(ItemDeliveryEstimate item : fullResponse.getItemDeliveryEstimate())
		{
			if(item.getDeliveryEstimate().size() == 0)
			{
				continue;
			}
			EstimationTypeFlag type = item.getDeliveryEstimate().get(0).getEstimationTypeFlag();
			switch(type)
			{
			case E : //Ebay FnF
			case R : //Runa FnF
				AnalyticalDeliveryEstimatesResult result = new AnalyticalDeliveryEstimatesResult();
				result.setFnfFlag(FnFFlag.fromValue(type.value()));
				result.setItemId(item.getItemId());
				DeliveryEstimate de = item.getDeliveryEstimate().get(0).getDeliveryEstimate();
				if(de.getMinDeliveryDate() != null)
				{
					de.getMinDeliveryDate().setTimezone(tzOffset);
				}
				if(de.getMaxDeliveryDate() != null)
				{
					de.getMaxDeliveryDate().setTimezone(tzOffset);
				}
				result.setMinArrival(de.getMinDeliveryDate());
				result.setMaxArrival(de.getMaxDeliveryDate());
				response.getAnalyticalDeliveryEstimatesResults().add(result);
				break;
			}
			
		}
		return response;
	}
	
	private FullBatchAnalyticalDeliveryEstimatesResponse getAnalyticalDeliveryEstimatesInternal(BatchAnalyticalDeliveryEstimatesRequest request, boolean limitToFnF) {
		
		long startValidate = System.nanoTime();
		
		FullBatchAnalyticalDeliveryEstimatesResponse response = new FullBatchAnalyticalDeliveryEstimatesResponse();
		DeliveryEstimateErrorHandler errorHandler = new DeliveryEstimateErrorHandler(response);

		// validate request
		if(! errorHandler.performRequestValidation(request,limitToFnF)) {
			return response;
		}
		
		long stopValidate = System.nanoTime();
		long startContext = System.nanoTime();

		AnalyticalDeliveryEstimatesContext context = new AnalyticalDeliveryEstimatesContext(request,limitToFnF);
		
		long stopContext = System.nanoTime();
		
		CalEventHelper.writeLog(context.getLoggingHelper().getTransactionType(), "Validation_Time", "" + (stopValidate - startValidate)/1000000.0, "0");
		CalEventHelper.writeLog(context.getLoggingHelper().getTransactionType(), "Context_creation_time", "" + (stopContext - startContext)/1000000.0, "0");
		//Begin transaction around entire service call
		CalTransaction totalTxn = CalTransactionFactory.create(context.getLoggingHelper().getTransactionType());
		totalTxn.setName(SERVICE_TXN_NAME);
		
		CalTransaction geoTxn = CalTransactionFactory.create(context.getLoggingHelper().getTransactionType());
		geoTxn.setName("Geolocation");
		
		// geolocation
		EstimateProviderOutput runaProviderOutput = null;
		if((StringUtils.isNullOrEmpty(request.getBuyer().getToCountry()) || StringUtils.isNullOrEmpty(request.getBuyer().getToZip()))) {
			if (DeliveryEstimateConfigFactors.EBAY_GEOLOCATION.getBoolean(context)) { // eBay geolocation
				String ipAddress = request.getBuyer().getBuyerIpAddress();
				if(! StringUtils.isNullOrEmpty(ipAddress)) {
					GetLocationByIpResponse geoResp;
					try {
						geoResp = DeliveryEstimateGeolocationHelper.getLocationByIpLocal(ipAddress);
					}  catch (Exception e) {
						CalEventHelper.writeException(GEOLOCATION_NAME, e);
						totalTxn.setStatus(e);
						totalTxn.completed();
						geoTxn.setStatus(e);
						geoTxn.completed();
						FullBatchAnalyticalDeliveryEstimatesResponse resp = new FullBatchAnalyticalDeliveryEstimatesResponse();
						errorHandler.handleException(e);
						return resp;
					}
					if(geoResp != null && geoResp.getLocation() != null && ! StringUtils.isNullOrEmpty(geoResp.getLocation().getCountry()) &&
					    (StringUtils.isNullOrEmpty(request.getBuyer().getToCountry()) || 
					     geoResp.getLocation().getCountry().equalsIgnoreCase(request.getBuyer().getToCountry()) )) {
						request.getBuyer().setToCountry(geoResp.getLocation().getCountry());
						response.setToCountry(geoResp.getLocation().getCountry());
						String zipCode = geoResp.getLocation().getZipCode();
						context.getLoggingHelper().addLogData(LoggingFormat.GEOLOCATION, geoResp);
						if(!StringUtils.isNullOrEmpty(zipCode)) {
							request.getBuyer().setToZip(zipCode);
							response.setToZip(zipCode);
							geoTxn.addData("GeolocatedTo=" + zipCode);
						}
					}
				}
			}
		}
		
		int[] eventFields =  new int[3];
		
		Item item1 = request.getItem().get(0);
	
		com.ebay.marketplace.services.Amount itemPrice = item1.getItemCost();
		
		System.out.println("Amount -----> " + itemPrice.getValue() +  "  Site ID --"  + request.getSiteId() + "---" );
		
//		SDESRedlemur.testRedlemur();
		
		double itemAmount = itemPrice.getValue();
		eventFields[0] = request.getSiteId();
        
	//	ShippingRedlemurHelper.redlemurTest();
		
		geoTxn.setStatus("0");
		geoTxn.completed();
		
		boolean ignoreFnF = DeliveryEstimateConfigFactors.IGNORE_FNF.getBoolean(context);
		
		CalTransaction eligibilityTxn = CalTransactionFactory.create(context.getLoggingHelper().getTransactionType());
		eligibilityTxn.setName("Request_Eligibility_Computation");
		
		// determine request eligibility and set providers accordingly
		if(!DeliveryEstimateBusinessLogicImpl.getInstance().isEligibleRequest(context)) {
			totalTxn.setStatus(DeliveryEstimateLoggingHelper.CAL_SUCCESS);
			totalTxn.completed();
			return response;
		}		
		boolean fnfEligibleRequest = DeliveryEstimateBusinessLogicImpl.getInstance().isFnFEligibleRequest(context);
		if (limitToFnF && !fnfEligibleRequest) {
			totalTxn.setStatus(DeliveryEstimateLoggingHelper.CAL_SUCCESS);
			totalTxn.completed();
			return response;
		}
		List<EstimateProviderEnum> providers = new ArrayList<EstimateProviderEnum>();
		if(RunaDeliveryEstimateConfigBean.getBean().isUseNativeEstimates()) 
		{
			providers.add(EstimateProviderEnum.EBAY);
		}
		if (fnfEligibleRequest && DeliveryEstimateBusinessLogicImpl.getInstance().isRunaSupportedCountry(context) &&
		    DeliveryEstimateConfigFactors.CALL_RUNA.getBoolean(context))
		{
			if (context.getRequest().getPageSource().equals(ShippingDeliveryEstimateClient.SRP.value()) && RunaDeliveryEstimateConfigBean.getBean().isUseRunaSFE())
			{
				providers.add(EstimateProviderEnum.ANALYTIC);
			}
			else if(!context.getRequest().getPageSource().equals(ShippingDeliveryEstimateClient.SRP.value()) && RunaDeliveryEstimateConfigBean.getBean().isUseRuna())
			{
				providers.add(EstimateProviderEnum.ANALYTIC);
			}
		}
		
		eligibilityTxn.setStatus("0");
		eligibilityTxn.completed();
		
		CalTransaction itemEligibilityTxn = CalTransactionFactory.create(context.getLoggingHelper().getTransactionType());
		itemEligibilityTxn.setName("Item_Eligibility_Computation");
		
		// determine and map eligible items
		Map<Pair<Long,Long>, Item> itemsLeftToProcess = new HashMap<Pair<Long,Long>, Item>(context.getRequest().getItem().size());
		Set<Pair<Long,Long>> adeEligibleItems = new HashSet<Pair<Long,Long>>(context.getRequest().getItem().size());
		Set<Pair<Long,Long>> nonADEligibleItems = new HashSet<Pair<Long,Long>>(context.getRequest().getItem().size());
		Configuration configuration = new Configuration(context);
		DeliveryEstimateBusinessLogicImpl.getInstance().groupItemByEligibility(context, configuration, limitToFnF, itemsLeftToProcess, adeEligibleItems, nonADEligibleItems);
		itemEligibilityTxn.setStatus("0");
		itemEligibilityTxn.completed();
		
		EstimateProviderOutput nativeProviderOutput = null;
		
		// process each provider estimates
		Map<Pair<Long,Long>, ProcessedItemDeliveryEstimate> currentEstimates = new HashMap<Pair<Long,Long>, ProcessedItemDeliveryEstimate>(context.getRequest().getItem().size());
		for (EstimateProviderEnum estimateProviderEnum : providers) {
			DeliveryEstimateProvider provider = estimateProviderEnum.getDeliveryEstimateProviderInstance(context);
			if (provider != null) {
				EstimateProviderOutput estimateProviderOutput = null;
				if(estimateProviderEnum == EstimateProviderEnum.ANALYTIC && runaProviderOutput != null) {
					// if Runa was already called due to geolocation, we can reuse it
					estimateProviderOutput = runaProviderOutput;
				} else {
					if(estimateProviderEnum == EstimateProviderEnum.ANALYTIC) {
						//Only call RUNA for items in the FnFEligible list
						for( Pair<Long, Long> i: nonADEligibleItems)
						{
							itemsLeftToProcess.remove(i);
						}
					}
					CalTransaction txn = CalTransactionFactory.create(context.getLoggingHelper().getTransactionType());
					txn.setName(provider.getClass().getSimpleName());
					try {
						estimateProviderOutput = provider.getDeliveryEstimates(context, itemsLeftToProcess.values());
					} catch (IllegalArgumentException e) {
						txn.setStatus(e);
						txn.completed();
						errorHandler.handleException(e);
						continue;
					}
					txn.setStatus(DeliveryEstimateLoggingHelper.CAL_SUCCESS);
					txn.completed();
					//Save the native ebay outputs so that we can reuse them later.
					if(estimateProviderEnum == EstimateProviderEnum.ANALYTIC) {
						nativeProviderOutput = estimateProviderOutput;
					}
				}
					if(estimateProviderEnum == EstimateProviderEnum.ANALYTIC) {
						//What items did we call the analytical provider with
						for( Item i: itemsLeftToProcess.values())
						{
							ProcessedItemDeliveryEstimate processedItemDeliveryEstimate = currentEstimates.get(new Pair<Long,Long>(i.getId(),i.getTransactionId()));
							if (processedItemDeliveryEstimate != null)
							{
								ItemDeliveryEstimate itemDeliveryEstimate = processedItemDeliveryEstimate.getItemDeliveryEstimate();
								for(ServiceDeliveryEstimate sde : itemDeliveryEstimate.getDeliveryEstimate())
								{
									sde.setRunaCompare(EstimationTypeFlag.N);
								}
							}
						}
					}
				CalTransaction processTxn = CalTransactionFactory.create(context.getLoggingHelper().getTransactionType());
				processTxn.setName(provider.getClass().getSimpleName() + "processing");
				if (estimateProviderOutput != null) {
					DeliveryEstimateBusinessLogicImpl.getInstance().processEstimates(
							estimateProviderOutput, 
							context, 
							currentEstimates, 
							ignoreFnF, 
							fnfEligibleRequest,
							adeEligibleItems,
							false);
					for (ProcessedItemDeliveryEstimate processedItemDeliveryEstimate : currentEstimates.values()) {
						ItemDeliveryEstimate itemDeliveryEstimate = processedItemDeliveryEstimate.getItemDeliveryEstimate();
						if (! DeliveryEstimateConfigFactors.BEST_ESTIMATE.getBoolean(context) && findFnFEstimate(itemDeliveryEstimate) != null) {
							itemsLeftToProcess.remove(new Pair<Long,Long>(itemDeliveryEstimate.getItemId(),itemDeliveryEstimate.getTransactionId())); // if item is eBay FnF don't call Runa (this may change due to BDE)
						}
					}
				}
				processTxn.setStatus("0");
				processTxn.completed();
			}
		}
		
		// Filter results and compute dates based on business day estimates
		if (currentEstimates != null) {
			for(int i = 0; i < context.getRequest().getItem().size(); i++) 
			{
				Item item = context.getRequest().getItem().get(i);
				ProcessedItemDeliveryEstimate processedItemDeliveryEstimate = currentEstimates.get(new Pair<Long,Long>(item.getId(),item.getTransactionId()));
				if(processedItemDeliveryEstimate != null)
				{
					ItemDeliveryEstimate itemDeliveryEstimate = processedItemDeliveryEstimate.getItemDeliveryEstimate();
					if (limitToFnF) {
						ServiceDeliveryEstimate fnfEstimate = findFnFEstimate(itemDeliveryEstimate);
						if (fnfEstimate != null) {
							itemDeliveryEstimate.getDeliveryEstimate().clear();
							itemDeliveryEstimate.getDeliveryEstimate().add(fnfEstimate);
							response.getItemDeliveryEstimate().add(itemDeliveryEstimate);
						}
					} else {
						response.getItemDeliveryEstimate().add(itemDeliveryEstimate);
					}
				}
			}
		}
		DeliveryEstimateBusinessLogicImpl.getInstance().populateBusinessDays(context, response.getItemDeliveryEstimate());
		
		int maxDelivery = response.getItemDeliveryEstimate().get(0).getDeliveryEstimate().get(0).getDeliveryEstimate().getMaxDelivery();
		int minDelivery = response.getItemDeliveryEstimate().get(0).getDeliveryEstimate().get(0).getDeliveryEstimate().getMinDelivery();
		
		eventFields[1] = minDelivery;
		eventFields[2] = maxDelivery;
		System.out.println("Maximum Delivery Days ----  " + maxDelivery + "  Min Delivery Days ---" + minDelivery + " Status -- " + stringStatus);
		System.out.println("Status ------> " + response.getAck().value);
		
//		for(double x : eventFields)
//			System.out.println("Array values -- " + x);
		
//		System.out.println("Response Status --"  + response.getAck().value());
		
		
		
		ShippingRedlemurHelper.redlemurTest(eventFields, itemAmount);
		
		for(ItemDeliveryEstimate i : response.getItemDeliveryEstimate()) {
			context.getLoggingHelper().addLogData(LoggingFormat.IDEstimate, i);
		}
		
		
		
		context.getLoggingHelper().writeLog();
		
		//If it's a Checkout client call we have to update database and make 2 Day Estimate time hack
		updateDatabase(context,response,nativeProviderOutput);
		
	
		
		DeliveryEstimateBusinessLogicImpl.getInstance().populateZipCode(context, response);
		
		com.ebay.marketplace.services.AckValue testStatus = response.getAck();
		
//		testStatus.value();
		
//		System.out.println("Response Status --"  + testStatus.value());
		totalTxn.setStatus(DeliveryEstimateLoggingHelper.CAL_SUCCESS);
		totalTxn.completed();
		return response;
	}
	
	private void updateDeliveryEstimateCounters(ItemDeliveryEstimate itemEstimate)
	{
		if (itemEstimate != null)
		{
			for (ServiceDeliveryEstimate serviceEstimate : itemEstimate.getDeliveryEstimate())
			{
				if (serviceEstimate.getDeliveryEstimate().getMaxDelivery() != null)
				{
					long maxDelivDays = serviceEstimate.getDeliveryEstimate().getMaxDelivery();
					
					if (maxDelivDays <= 2)
					{
						SDESHeartbeatEvent.NUM_LESSTHAN_3DAY_ESTIMATES.incrementCounter();
					}
					else if (maxDelivDays == 3)
					{
						SDESHeartbeatEvent.NUM_3DAY_ESTIMATES.incrementCounter();
					}
					else if (maxDelivDays == 4)
					{
						SDESHeartbeatEvent.NUM_4DAY_ESTIMATES.incrementCounter();
					}
					else if (maxDelivDays > 4)
					{
						SDESHeartbeatEvent.NUM_GREATERTHAN_4DAY_ESTIMATES.incrementCounter();
					}
				}
			}
		}
	}

	private void UpdateHeartbeatStatistics(AnalyticalDeliveryEstimatesContext context)
	{
		BatchAnalyticalDeliveryEstimatesRequest request = context.getRequest(); 
		
		if (request == null)
			return;
		
		
		if ( request.getBuyer() != null 
				&& request.getBuyer().getToCountry() != null)
		{
			if (CountryEnum.get(request.getBuyer().getToCountry()).equals(CountryEnum.US))
			{
				SDESHeartbeatEvent.NUM_US_REQUESTS.incrementCounter();
			}
			else
			{
				SDESHeartbeatEvent.NUM_NONUS_REQUESTS.incrementCounter();
			}
		}
		
		String pageSource = request.getPageSource();
		
		if (pageSource != null)
		{
			if (pageSource.equals(ShippingDeliveryEstimateClient.SRP.value()))
			{
				SDESHeartbeatEvent.NUM_SRP_REQUESTS.incrementCounter();
			}
			else if (pageSource.equals(ShippingDeliveryEstimateClient.API.value()))
			{
				SDESHeartbeatEvent.NUM_API_REQUESTS.incrementCounter();
			}
			else if (pageSource.equals(ShippingDeliveryEstimateClient.VI.value()))
			{
				SDESHeartbeatEvent.NUM_VI_REQUESTS.incrementCounter();
			}
			else if (pageSource.equals(ShippingDeliveryEstimateClient.C_2_B.value()))
			{
				SDESHeartbeatEvent.NUM_C2B_REQUESTS.incrementCounter();
			}
			else if (pageSource.equals(ShippingDeliveryEstimateClient.CHK.value()))
			{
				SDESHeartbeatEvent.NUM_XO_REQUESTS.incrementCounter();
			}
			else if (pageSource.equals(ShippingDeliveryEstimateClient.CS.value()))
			{
				SDESHeartbeatEvent.NUM_CART_REQUESTS.incrementCounter();
			}
			else if (pageSource.equals(ShippingDeliveryEstimateClient.PDP.value()))
			{
				SDESHeartbeatEvent.NUM_PDP_REQUESTS.incrementCounter();
			}
			else if (pageSource.equals(ShippingDeliveryEstimateClient.EUDD.value()))
			{
				SDESHeartbeatEvent.NUM_EUDD_REQUESTS.incrementCounter();
			}
			else
			{
				SDESHeartbeatEvent.NUM_UNKNOWN_REQUESTS.incrementCounter();
			}
		}
		List<Item> items = request.getItem();
		
		if (items != null)
		{
			int itemCount = items.size();
			
			if (itemCount <= 50)
			{
				SDESHeartbeatEvent.NUM_REQUESTS_50_ITEMS_OR_LESS.incrementCounter();
			}
			else if ((itemCount > 50) && (itemCount <= 100))
			{
				SDESHeartbeatEvent.NUM_REQUESTS_51_TO_100_ITEMS.incrementCounter();
			}
			else
			{
				SDESHeartbeatEvent.NUM_REQUESTS_MORE_THAN_100_ITEMS.incrementCounter();
			}
		}
	}
	
	private List<ItemDeliveryEstimate> convertDeliveryEstimates(EstimateProviderOutput estimates)
	{
		List<ItemDeliveryEstimate> converted = new ArrayList<ItemDeliveryEstimate>();
		if(estimates != null) {
		for(ItemEstimate estimate : estimates.getItemEstimates())
		{
			ItemDeliveryEstimate item = new ItemDeliveryEstimate();
			item.setItemId(estimate.getItemId());
			item.setTransactionId(estimate.getTransactionId());
			for(ItemServiceEstimate serviceEstimate : estimate.getServiceEstimates())
			{
				ServiceDeliveryEstimate newEstimate = new ServiceDeliveryEstimate();
				newEstimate.setServiceId(serviceEstimate.getServiceId());
				DeliveryEstimate deliveryEstimate = new DeliveryEstimate();
				deliveryEstimate.setMaxDelivery(serviceEstimate.getMaxDays());
				deliveryEstimate.setMinDelivery(serviceEstimate.getMinDays());
				newEstimate.setDeliveryEstimate(deliveryEstimate );
				item.getDeliveryEstimate().add(newEstimate);
			}
			converted.add(item);
			}
		}
		return converted;
	}
	
	/**
	 * Reads site parameters which determine the minimum confidence based on the display order of the shipping service.
	 * If is takes long, we can consider caching the values, but then we'd need to consider how to update when params change. 
	 */
	private static final String SHIP_SERVICE_CONFIDENCE_SITE_PARAM_TYPE = "SHIP_SERVICE_CONFIDENCE";
	private static final String SHIP_SERVICE_CONFIDENCE_SITE_PARAM_NAME_PREFIX = "SHIP_SERVICE_CONFIDENCE_";
	static public int getMinimumConfidence(int siteId, int displayOrder) {
		String paramValue = SiteParameterHelper.getSiteParameter(
				siteId,
				SHIP_SERVICE_CONFIDENCE_SITE_PARAM_TYPE,
				SHIP_SERVICE_CONFIDENCE_SITE_PARAM_NAME_PREFIX + displayOrder);
		int minimumConfidence = 0;
		if (!StringUtil.isEmpty(paramValue)) {
			try {
				minimumConfidence = Integer.valueOf(paramValue);
			} catch (Exception exception) {
				minimumConfidence = 0;
			}
		}
		return minimumConfidence;
	}
	
	/*
	 * Set values for the data object
	 */
	private void setShippingDeliveryEstimateTrackValues(
			AnalyticalDeliveryEstimatesContext context,
			Item item,
			ShippingDeliveryEstimateTrack dbRecord,
			ItemDeliveryEstimate itemDeliveryEstimate,
			ServiceDeliveryEstimate serviceDeliveryEstimate,
			ServiceDeliveryEstimate nativeEbayEstimate,
			int displayOrder)
	{
		if (context.getRequest().getBuyer().getBuyerId() == null)
		{
			// we don't want to add records for user without buyer id
			return;
		}
		
		dbRecord.setBuyerId(context.getRequest().getBuyer().getBuyerId());
		dbRecord.setCategoryId(item.getCategoryId());
		dbRecord.setFromZip(item.getSeller().getFromZip());
		String zip = context.getRequest().getBuyer().getToZip();
		if (zip != null && zip.indexOf('-') != -1) {
			int hyphenIndex = zip.indexOf('-');
			if(hyphenIndex != -1)
			{
				zip = zip.substring(0, hyphenIndex);
			}
		}
		dbRecord.setToZip(zip);
		if(context.getRequest().getBuyer().getSessionGuid()!=null)
		{
			dbRecord.setGuid(context.getRequest().getBuyer().getSessionGuid());
		}
		dbRecord.setItemId(itemDeliveryEstimate.getItemId());
		dbRecord.setSellerId(item.getSeller().getSellerId());
		dbRecord.setTransactionId(itemDeliveryEstimate.getTransactionId());
		dbRecord.setServiceId(serviceDeliveryEstimate.getServiceId());
		dbRecord.setTestType(DeliveryEstimateConfigFactors.IN_HOUSE_MODEL.getBoolean(context) ?
				RunaDeliveryEstimateTestTypeEnum.values().length :
				RunaDeliveryEstimateTestTypeEnum.valueOf(DeliveryEstimateConfigFactors.RUNA_PASSTHROUGH.getString(context)).ordinal());
		
		int minimumConfidence = getMinimumConfidence(context.getRequest().getSiteId(), displayOrder);
		
		dbRecord.setMinConfidenceLevel(minimumConfidence);
		dbRecord.setMinDeliveryEstimateDays(serviceDeliveryEstimate.getDeliveryEstimate().getMinDelivery());
		dbRecord.setMaxConfidenceLevel(minimumConfidence);
		dbRecord.setMaxDeliveryEstimateDays(serviceDeliveryEstimate.getDeliveryEstimate().getMaxDelivery());
		dbRecord.setHandlingDays(ShippingHandlingTimeHelper.getHandlingDays(item, context));
		dbRecord.setFromIsoCountryCode(item.getSeller().getFromCountry().toUpperCase());
		dbRecord.setToIsoCountryCode(context.getRequest().getBuyer().getToCountry().toUpperCase());
		if (nativeEbayEstimate != null) {
			dbRecord.setOriginalStaticMinDlvryDate(nativeEbayEstimate.getDeliveryEstimate().getMinDeliveryDate().toGregorianCalendar().getTime());
			dbRecord.setOriginalStaticMaxDlvryDate(nativeEbayEstimate.getDeliveryEstimate().getMaxDeliveryDate().toGregorianCalendar().getTime());
		}
	}
	
	/*
	 * To update the database, we assume that all lists are ordered the same, and have all the information.
	 */

	private void updateDatabase(
			AnalyticalDeliveryEstimatesContext context,
			FullBatchAnalyticalDeliveryEstimatesResponse response,
			EstimateProviderOutput nativeEbayEstimates)
	{
		//Save to DB when client = Checkout, Cart, or Commit to Buy
		if(context.isCheckoutCall())
		{
			List<ItemDeliveryEstimate> nativeEstimates = convertDeliveryEstimates(nativeEbayEstimates);
			DeliveryEstimateBusinessLogicImpl.getInstance().populateBusinessDays(context, nativeEstimates);
			
			int dbInsertCount=0;
			
			for(ItemDeliveryEstimate itemDeliveryEstimate :response.getItemDeliveryEstimate())
			{
				if(dbInsertCount>=5)
				{
					break;
				}
				ItemDeliveryEstimate nativeDeliveryEstimate = null;
				for(ItemDeliveryEstimate temp : nativeEstimates)
				{
					if(temp.getItemId() == itemDeliveryEstimate.getItemId() && temp.getTransactionId() == itemDeliveryEstimate.getTransactionId())
					{
						nativeDeliveryEstimate = temp;
						break;
					}
				}
				if(nativeDeliveryEstimate == null)
				{
					continue;
				}
				Item item = null;
				for(Item temp : context.getRequest().getItem())
				{
					if(temp.getId() == itemDeliveryEstimate.getItemId() && temp.getTransactionId() == itemDeliveryEstimate.getTransactionId())
					{
						item = temp;
						break;
					}
				}
				if(item == null)
				{
					continue;
				}
				if(itemDeliveryEstimate.getDeliveryEstimate().size() != nativeDeliveryEstimate.getDeliveryEstimate().size() ||
				   itemDeliveryEstimate.getDeliveryEstimate().size() != ShippingHandlingTimeHelper.getShippingService(item).size())
				{
					//Shipping Service mismatch
					continue;
				}
				for(ServiceDeliveryEstimate serviceDeliveryEstimate:itemDeliveryEstimate.getDeliveryEstimate())
				{
					if(!(serviceDeliveryEstimate.getEstimationTypeFlag()==EstimationTypeFlag.R || //Runa FnF
						 serviceDeliveryEstimate.getEstimationTypeFlag()==EstimationTypeFlag.D || //Analytical 2 Day
					     serviceDeliveryEstimate.getEstimationTypeFlag()==EstimationTypeFlag.B || //BDE
					     serviceDeliveryEstimate.getRunaCompare()==EstimationTypeFlag.G || //Runa Equal 
					     serviceDeliveryEstimate.getRunaCompare()==EstimationTypeFlag.V)) //Runa Worse
					{
						//No Runa results for this, don't put anything in the table.
						continue;
					}
					ServiceDeliveryEstimate nativeServiceEstimate = null;
					for(ServiceDeliveryEstimate temp : nativeDeliveryEstimate.getDeliveryEstimate())
					{
						if(temp.getServiceId() == serviceDeliveryEstimate.getServiceId())
						{
							nativeServiceEstimate = temp;
							break;
						}
					}
					if(nativeServiceEstimate == null)
					{
						continue;
					}
					int displayOrder = -1;
					for(ShippingService temp : ShippingHandlingTimeHelper.getShippingService(item))
					{
						if(temp.getServiceId() == serviceDeliveryEstimate.getServiceId())
						{
							displayOrder = temp.getDisplayOrder();
							break;
						}
					}
					if(displayOrder == -1)
					{
						continue;
					}
					try {
						dbInsertCount++;
						
						ShippingDeliveryEstimateTrack existingEstimate = ShippingDeliveryEstimateTrackDAO.getInstance().findTrackByTransactionIdentityAndServiceId(
								itemDeliveryEstimate.getItemId(), itemDeliveryEstimate.getTransactionId(), serviceDeliveryEstimate.getServiceId(), ShippingDeliveryEstimateTrackDAO.ReadSets.FULL);
						setShippingDeliveryEstimateTrackValues(context,
								item,
								existingEstimate,
								itemDeliveryEstimate,
								serviceDeliveryEstimate,
								nativeServiceEstimate,
								displayOrder);
						try {
							ShippingDeliveryEstimateTrackDAO.getInstance().update(existingEstimate);
						} catch (UpdateException e) {
							ThirdPartyCallHelper.logDataToCal(EVENT_TYPE, e.getCalEventName(), e.getLocalizedMessage() +" "+ e.getStackTrace().toString());
						}
					} catch (FinderException e) {
						/*
						 * Add new entry
						 */
						ShippingDeliveryEstimateTrack newEstimate = ShippingDeliveryEstimateTrackDAO.getInstance().createLocal();
						setShippingDeliveryEstimateTrackValues(context,
								item,
								newEstimate,
								itemDeliveryEstimate,
								serviceDeliveryEstimate,
								nativeServiceEstimate,
								displayOrder);
						try {
							ShippingDeliveryEstimateTrackDAO.getInstance().insert(newEstimate);
						} catch (CreateException e1) {
							ThirdPartyCallHelper.logDataToCal(EVENT_TYPE, e.getCalEventName(), e.getLocalizedMessage() +" "+ e.getStackTrace().toString());
						}
					}
				}
			}
		}
	}
	

	private ServiceDeliveryEstimate findFnFEstimate(ItemDeliveryEstimate itemDeliveryEstimate) {
		for (ServiceDeliveryEstimate serviceDeliveryEstimate : itemDeliveryEstimate.getDeliveryEstimate()) {
			if (serviceDeliveryEstimate.getEstimationTypeFlag().equals(EstimationTypeFlag.E) || 
				serviceDeliveryEstimate.getEstimationTypeFlag().equals(EstimationTypeFlag.R)) {
				return serviceDeliveryEstimate;
			}
		}
		return null;
	}

	@Override
	public FullBatchAnalyticalDeliveryEstimatesResponse getFullBatchAnalyticalDeliveryEstimates(BatchAnalyticalDeliveryEstimatesRequest request) {
		
		final int RETURN_TREATMENT = 1;
		final int RETURN_SITE_DEFAULT = 2;
		AnalyticalDeliveryEstimatesContext ctx = new AnalyticalDeliveryEstimatesContext(request, false);
		int treatmentConfig = DeliveryEstimateConfigFactors.TREATED_FLAG_TYPE.getInteger(ctx);
		
		//Don't do treated flag work on non-VI pages.
		if(! request.getPageSource().equals(DeliveryEstimateBusinessLogicImpl.PAGE_SOURCES.vi.toString()) ||
				!(treatmentConfig == RETURN_TREATMENT || treatmentConfig == RETURN_SITE_DEFAULT))
			return getAnalyticalDeliveryEstimatesInternal(request, false);
		
		FullBatchAnalyticalDeliveryEstimatesResponse resp = null;
		String compareFactor = "";
		
		if(treatmentConfig == RETURN_TREATMENT) {
			resp = getAnalyticalDeliveryEstimatesInternal(request, false);
		} else {
			compareFactor = request.getConfigFactor();
			request.setConfigFactor("");
			resp = getAnalyticalDeliveryEstimatesInternal(request, false);
		}
		
		/*We are going to look at only the first entry because treated flag only applies to single-item VI calls */
		request.setConfigFactor(compareFactor);
		Item first = request.getItem().get(0);
		ItemDeliveryEstimate firstIDE = resp.getItemDeliveryEstimate().get(0);
		
		request.getItem().clear();
		/* No estimates, nothing to compare. Returning early */
		if(first != null && firstIDE != null)
			request.getItem().add(first);
		else
			return resp;
		
		FullBatchAnalyticalDeliveryEstimatesResponse compareResp = getAnalyticalDeliveryEstimatesInternal(request, false);
		boolean allChanged = false;
		
		//Compare estimate info for changes which affect all estimates
		if( 	! nullOrContentEqual(resp.getToCountry(),compareResp.getToCountry()) ||
				! nullOrContentEqual(resp.getToZip(),compareResp.getToZip())) {
			allChanged = true;
		}
		
		ItemDeliveryEstimate toCompare = compareResp.getItemDeliveryEstimate().get(0);
		
		//Compare individual estimates for differences
		for( ServiceDeliveryEstimate sde : firstIDE.getDeliveryEstimate()) {
			if(allChanged) {
				sde.setTreatedFlag(true);
				continue;
			}
			ServiceDeliveryEstimate compSDE = null;
			for(ServiceDeliveryEstimate compSDEs : toCompare.getDeliveryEstimate()) {
				if(compSDEs.getServiceId() == sde.getServiceId()) {
					compSDE = compSDEs;
					break;
				}
			}
			if(compSDE == null)
				sde.setTreatedFlag(true);
			else {
				//This should really be done with a .equals() on SDE objects,
				//but the SOA framework doesn't seem to play with JAXB addons...
				if(! (nullOrContentEqual(sde.getEstimationTypeFlag(), compSDE.getEstimationTypeFlag()) &&
						nullOrContentEqual(sde.getDeliveryEstimate().getMinDelivery(), compSDE.getDeliveryEstimate().getMinDelivery()) &&
						nullOrContentEqual(sde.getDeliveryEstimate().getMaxDelivery(), compSDE.getDeliveryEstimate().getMaxDelivery()) &&
						nullOrContentEqual(sde.getDeliveryEstimate().getMinDeliveryDate(), compSDE.getDeliveryEstimate().getMinDeliveryDate()) &&
						nullOrContentEqual(sde.getDeliveryEstimate().getMaxDeliveryDate(), compSDE.getDeliveryEstimate().getMaxDeliveryDate())))
					sde.setTreatedFlag(true);
			}
		}
		
		return resp;
	}
	
	/* Checks for equality with both null being treated as equal */
	private boolean nullOrContentEqual(Object one, Object two) {
		if(one == null && two == null)
			return true;
		
		if(one != null && two != null && one.equals(two))
			return true;
		
		return false;
	}
	
	private myfix(){
	if(compSDE == null)
				sde.setTreatedFlag(true);
		
	}
}
