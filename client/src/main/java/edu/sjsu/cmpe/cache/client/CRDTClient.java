package edu.sjsu.cmpe.cache.client;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import javax.print.attribute.standard.Severity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.Future;

public class CRDTClient {

	public int sCount = 0;
	public int fCount = 0;
	public int wCount = 0;
	public int addKey;
	public int delSuccess = -1;
	public String oldValue;

	
	public ArrayList<CacheServiceInterface> serverList;
	public ArrayList<CacheServiceInterface> writeFailedServers;
	public ArrayList<CacheServiceInterface> successServerList = new ArrayList<CacheServiceInterface>();	
	public static HashMap<String, Integer> savedReadValue = new HashMap<String, Integer>();
	public HashMap<String, String> valuesByServers = new HashMap<String, String>();

	CRDTClient(ArrayList<CacheServiceInterface> serverList) 
	
	{
		this.serverList = serverList;
	}



	public void writeToCache(int key, String value) throws IOException 
	{

		boolean writeSuccess = false;
		addKey = key;

		this.sCount = 0;
		this.fCount = 0;

		this.wCount = serverList.size();

		savedIntermediateState();

		this.sCount = 0;
		this.fCount = 0;

		this.wCount = serverList.size();

		this.successServerList = new ArrayList<CacheServiceInterface>();

		writeFailedServers = new ArrayList<CacheServiceInterface>();

		for (final CacheServiceInterface server : serverList) 
		{
			HttpResponse<JsonNode> res = null;
			try {
				res = Unirest
						.put(server.toString() + "/cache/{key}/{value}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(key))
						.routeParam("value", value)
						.asJsonAsync(new Callback<JsonNode>(){

							@Override
							public void failed(UnirestException e) 
							{
								wCount--;
								fCount++;
								callbackWrite();
								writeFailedServers.add(server);
							}

							@Override
							public void completed(HttpResponse<JsonNode> res) 
							{
								if (res.getCode() != 200) 
								{
									wCount--;
									fCount++;
								} 
								else 
								{
									wCount--;
									sCount++;
									successServerList.add(server);
								}
								callbackWrite();
							}

							@Override
							public void cancelled() 
							{
								wCount--;
								fCount++;
								callbackWrite();

							}
						}).get();
			} catch (Exception e) {	}

			if (res == null || res.getCode() != 200) { }

		}


	}



	public void readCache(int key) throws IOException 
	{   

		boolean writeSuccess = false;
		addKey = key;

		this.sCount = 0;
		this.fCount = 0;
		this.wCount = serverList.size();

		for (final CacheServiceInterface server : serverList) 
		{
			Future<HttpResponse<JsonNode>> res = null;
			String tempServer = server.toString();
			try {
				res = Unirest
						.get(server.toString() + "/cache/{key}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(key))
						.asJsonAsync(new Callback<JsonNode>() {

							@Override
							public void failed(UnirestException e) {
								wCount--;
								fCount++;
								callbackRead();

							}

							@Override
							public void completed(HttpResponse<JsonNode> res) 
							{
								wCount--;
								sCount++;
								String value = "";
								if(res.getBody() != null)
								{
									value = res.getBody().getObject().getString("value");
								}
								valuesByServers.put(server.toString(), value);
								Integer getExistingCounter = savedReadValue.get(value);
								if(getExistingCounter == null)
								{
									savedReadValue.put(value, 1);
								}else
								{
									savedReadValue.put(value, getExistingCounter+1);
								}
								callbackRead();
							}

							@Override
							public void cancelled() 
							{
								wCount--;
								fCount++;
								callbackRead();
							}
						});
			} catch (Exception e) {	e.printStackTrace(); }


		}
	}




	public void deleteFromCache(int key) throws IOException{ 

		this.sCount = 0;
		this.fCount = 0;
		this.wCount = successServerList.size();

		for (CacheServiceInterface server : successServerList) 
		{
			HttpResponse<JsonNode> res = null;

			try {
				Unirest
				.delete(server.toString() + "/cache/{key}")
				.header("accept", "application/json")
				.routeParam("key", Long.toString(key))
				.asJsonAsync(new Callback<JsonNode>() {

					@Override
					public void failed(UnirestException e) {
						wCount--;
						fCount++;
						callbackDelete();

					}

					@Override
					public void completed(HttpResponse<JsonNode> res) 
					{
						if (res.getCode() != 204) 
						{
							wCount--;
							fCount++;
						} 
						else 
						{
							wCount--;
							sCount++;
						}
						callbackDelete();

					}

					@Override
					public void cancelled() 
					{
						wCount--;
						fCount++;
						callbackDelete();

					}
				});

			} catch (Exception e) {	System.err.println(e); }

		}

	}



	public void callbackWrite() 
	{

		if (wCount == 0 && fCount >= 2) 
		{

			try {
				System.out.println("Failed write - Rolling back values to the original! "+addKey+" => "+oldValue);
				deleteFromCache(addKey);

			} catch (IOException e) { e.printStackTrace(); }
		}

	}



	public void callbackDelete() {

		if (wCount == 0 && fCount == 0) {
			delSuccess++;
			writeFailedRollback();
		}else{
			try {
				deleteFromCache(addKey);
			} catch (IOException e) { e.printStackTrace(); }
		}

	}



	public void callbackRead() {

		if (wCount == 0 && sCount == 3) 
		{
			String repairVal = getRepairVal();
			repairCache(repairVal);

		}
		else{ }

	}



	
	public void repairCache(String repairVal){

		this.sCount = 0;
		this.fCount = 0;
		this.wCount = successServerList.size();

		ArrayList<CacheServiceInterface> updateServerList = new ArrayList<CacheServiceInterface>();

		for (Entry<String, String> server : valuesByServers.entrySet()) {

			if(server.getValue() != null && !server.getValue().equals(repairVal)){

				updateServerList.add(new DistributedCacheService(server.getKey().toString()));
			}

		}

		if(updateServerList.size() >  0){

			for(CacheServiceInterface server : updateServerList){
				System.out.println("\nRead-Repaired the value on => "+ server.toString() + "\n");
				try {
					HttpResponse<JsonNode> res = Unirest.put(server.toString() + "/cache/{key}/{value}")
							.header("accept", "application/json")
							.routeParam("key", Long.toString(addKey))
							.routeParam("value", repairVal)
							.asJson();
				} catch (UnirestException e) {	e.printStackTrace(); }
			}

		}else{

		}
		try {
			Unirest.shutdown();
		} catch (IOException e) { e.printStackTrace(); }
	}




	private String getRepairVal(){

		MapComparator mapComparator = new MapComparator(savedReadValue);
		SortedMap<String, Integer> sortedMap = new TreeMap<String, Integer>(mapComparator);
		sortedMap.putAll(savedReadValue);
		return sortedMap.firstKey();

	}



	private void savedIntermediateState(){
		for(CacheServiceInterface server:serverList){
			try {
				HttpResponse<JsonNode> res = Unirest.get(server + "/cache/{key}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(addKey)).asJson();
				oldValue = res.getBody().getObject().getString("value");
				if(oldValue == null) { continue; }
				else { break; }
			} catch (Exception e) {	continue; }
		}
	}



	private void writeFailedRollback(){

		for(CacheServiceInterface successServerList : this.successServerList)
		{
			String prevValue = oldValue;

			try {
				HttpResponse<JsonNode> res = Unirest.put(successServerList.toString() + "/cache/{key}/{value}")
						.header("accept", "application/json")
						.routeParam("key", Long.toString(addKey))
						.routeParam("value", prevValue)
						.asJson();
			     } catch (Exception e) {	}
		}

	}



}
