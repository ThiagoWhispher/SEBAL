package org.fogbowcloud.sebal.model.satellite;

import java.io.FileInputStream;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

public class JSONSatellite implements Satellite {

	private JSONObject json;

	public JSONSatellite(String jsonPath) throws Exception {
		this.json = new JSONObject(IOUtils.toString(new FileInputStream(jsonPath)));
	}
	
	@Override
	public String landsatName() {
		return this.json.toString();
	}
	
	@Override
	public double LLambdaMin(int band) {
		return json.optJSONArray("band" + band).optDouble(3);
	}

	@Override
	public double LLambdaMax(int band) {
		return json.optJSONArray("band" + band).optDouble(4);
	}

	@Override
	public double ESUNsat5(int band) {
		return json.optJSONArray("band" + band).optDouble(7);
	}
	
	@Override
	public double ESUNsat7(int band) {
		return json.optJSONArray("band" + band).optDouble(9);
	}
	
	@Override
	public double ESUNsat8(int band) {
		return json.optJSONArray("band" + band).optDouble(11);
	}

	@Override
	public double K1() {
		return json.optDouble("k1");
	}

	@Override
	public double K2() {
		return json.optDouble("k2");
	}

}
