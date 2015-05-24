package edu.sjsu.cmpe.cache.client;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;




import java.io.IOException;
import com.mashape.unirest.http.Unirest;

public class Client {

	public static void main(String[] args) throws Exception {

		ArrayList<CacheServiceInterface> serverList = new ArrayList<CacheServiceInterface>();

		
		CacheServiceInterface cacheOne = new DistributedCacheService("http://localhost:3000");
		CacheServiceInterface cacheTwo = new DistributedCacheService("http://localhost:3001");
		CacheServiceInterface cacheThree = new DistributedCacheService("http://localhost:3002");
		serverList.add(cacheOne);
		serverList.add(cacheTwo);
		serverList.add(cacheThree);

		


		CRDTClient clientCRDT = new CRDTClient(serverList);
		clientCRDT.writeToCache(1, "a");

		

		System.out.println("\nTurn OFF Server A Now !\n");
		
		Thread.sleep(50000);

			
		clientCRDT = new CRDTClient(serverList);
		clientCRDT.writeToCache(1, "b");
			

		System.out.println("Turn ON Server A Now");
		Thread.sleep(50000);
		
		clientCRDT.readCache(1);
		
	}



}
