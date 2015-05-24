package edu.sjsu.cmpe.cache.client;

import java.util.Comparator;
import java.util.HashMap;

public class MapComparator implements Comparator<String>{

	HashMap<String, Integer> vMap;
	
	public MapComparator(HashMap<String, Integer> vMap) {
		this.vMap = vMap;
	}
	
	
	@Override
	public int compare(String val1, String val2) {
		if(vMap.get(val1) >= vMap.get(val2)){
			return -1;
		}else{
			return 1;
		}
	}

}
