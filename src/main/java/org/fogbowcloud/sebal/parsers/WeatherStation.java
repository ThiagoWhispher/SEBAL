package org.fogbowcloud.sebal.parsers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;


public class WeatherStation {

	private static final String NOAA_URL_PREFIX = "noaa_url_prefix";
	private static final String NOAA_TMP_TOKEN = "noaa_tmp_token";
	private static final String NOAA_WND_TOKEN = "noaa_wnd_token";
	private static final String NOAA_DEW_TOKEN = "noaa_dew_token";

	private static final String SEP = "--------------------";
	private static final double R = 6371; // km
	private static final long A_DAY = 1000 * 60 * 60 * 24;
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("YYYYMMdd");
	private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("dd/MM/YYYY;hhmm");
	
	private static final Logger LOGGER = Logger.getLogger(WeatherStation.class);
	
	private Map<String, String> cache = new HashMap<String, String>();
	private JSONArray stations;
	private HttpClient httpClient;
	private Properties properties;
	
	public WeatherStation() throws URISyntaxException, HttpException, IOException {
		this (new Properties());
	}
	
	public WeatherStation(Properties properties) throws URISyntaxException, HttpException,
			IOException {
		this.httpClient = initClient();
		this.stations = new JSONArray(IOUtils.toString(
				new FileInputStream("stations.json")));
		this.properties = properties;
	}

	private List<JSONObject> findNearestStation(double lat, double lon) {
		List<JSONObject> orderedStations = new LinkedList<JSONObject>();
		double minDistance = Double.MAX_VALUE;
		for (int i = 0; i < stations.length(); i++) {
			JSONObject station = stations.optJSONObject(i);
			double d = d(lat, lon, station.optDouble("lat"), station.optDouble("lon"));
			if (d < minDistance) {
				minDistance = d;
			}
			station.put("d", d);
			orderedStations.add(station);
		}
		
		Collections.sort(orderedStations, new Comparator<JSONObject>() {

			@Override
			public int compare(JSONObject o1, JSONObject o2) {
				return ((Double)o1.optDouble("d")).compareTo((Double)o2.optDouble("d"));
			}
		});
		
		return orderedStations;
	}
	
	private double d(double lat1, double lon1, double lat2, double lon2) {
		double dLat = Math.toRadians(lat2-lat1);
		double dLon = Math.toRadians(lon2-lon1);
		lat1 = Math.toRadians(lat1);
		lat2 = Math.toRadians(lat2);
		double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
		        Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat1) * Math.cos(lat2); 
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
		return R * c;
	}
	
	public void persistStations() throws IOException {
		JSONArray stations = new JSONArray();
		for (String line  : IOUtils.readLines(new FileInputStream("stations.html"))) {
			if (line.contains("var html")) {
				String[] split = line.split("<br />");
				JSONObject json = new JSONObject();
				String split0 = split[0];
				String split00 = split0.substring(split[0].lastIndexOf(":") + 1, 
						split[0].indexOf("</b>")).trim();
				
				json.put("id", split00.split("-")[0].trim());
				json.put("name", split00.substring(split00.indexOf("-") + 1).trim());
				json.put("lat", Double.parseDouble(split[1].substring(split[1].indexOf(":") + 1).trim()));
				json.put("lon", Double.parseDouble(split[2].substring(split[2].indexOf(":") + 1).trim()));
				json.put("altitude", Double.parseDouble(split[3].substring(
						split[3].indexOf(":") + 1).replaceAll("m", "").trim()));
				stations.put(json);
			}
		}
		IOUtils.write(stations.toString(2), new FileOutputStream("stations.json"));
	}

	private HttpClient initClient() throws IOException,
			ClientProtocolException, UnsupportedEncodingException {
		BasicCookieStore cookieStore = new BasicCookieStore();
		HttpClient httpClient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();
		
		HttpGet homeGet = new HttpGet(
				"http://www.inmet.gov.br/projetos/rede/pesquisa/inicio.php");
		httpClient.execute(homeGet);
		
		HttpPost homePost = new HttpPost(
				"http://www.inmet.gov.br/projetos/rede/pesquisa/inicio.php");

		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("mUsuario", ""));
		nvps.add(new BasicNameValuePair("mGerModulo", ""));
		nvps.add(new BasicNameValuePair("mCod", "abmargb@gmail.com"));
		nvps.add(new BasicNameValuePair("mSenha", "9oo9xyyd"));
		nvps.add(new BasicNameValuePair("mGerModulo", "PES"));
		nvps.add(new BasicNameValuePair("btnProcesso", " Acessar "));
		
		homePost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
		HttpResponse homePostResponse = httpClient.execute(homePost);
		EntityUtils.toString(homePostResponse.getEntity());
		return httpClient;
	}
	
	// TODO: this needs to be done to each variable
	// TODO: this must verify if variable's response contains all times for that day (00:00, 12:00 and 18:00)
	private JSONArray readNOAAStation(String id, String inicio, String fim) throws Exception {
		
		// url prefix example: https://www7.ncdc.noaa.gov/rest/services/values/ish
		String noaaUrlPrefix = properties.getProperty(NOAA_URL_PREFIX);
		String noaaTMPToken = properties.getProperty(NOAA_TMP_TOKEN);
		String noaaWNDToken = properties.getProperty(NOAA_WND_TOKEN);
		String noaaDEWToken = properties.getProperty(NOAA_DEW_TOKEN);
		JSONArray dataArray = new JSONArray();
		
		readWeatherVariable(id, "TMP", inicio, fim, noaaUrlPrefix, noaaTMPToken, dataArray);
		readWeatherVariable(id, "WND", inicio, fim, noaaUrlPrefix, noaaWNDToken, dataArray);
		readWeatherVariable(id, "DEW", inicio, fim, noaaUrlPrefix, noaaDEWToken, dataArray);
		
		for (int i = 0; i < dataArray.length(); i++) {
			JSONObject stationDataRecord = dataArray.optJSONObject(i);
			String temp = stationDataRecord.optString("TempBulboSeco");
			String windDir = stationDataRecord.optString("DirecaoVento");
			String windSpeed = stationDataRecord.optString("VelocidadeVento");
			String dew = stationDataRecord.optString("TempBulboUmido");
			
			if (!temp.isEmpty() && temp != null && !windDir.isEmpty()
					&& windDir != null && !windSpeed.isEmpty()
					&& windSpeed != null && !dew.isEmpty() && dew != null) {
				return dataArray;
			}
		}
		
		return null;
	}

	private void readWeatherVariable(String id, String weatherVar,
			String inicio, String fim, String noaaUrlPrefix, String noaaToken,
			JSONArray dataArray) throws Exception {
		String url = noaaUrlPrefix + File.separator + id + "099999"
				+ File.separator + weatherVar + File.separator + inicio
				+ File.separator + fim + "?output=csv&token=" + noaaToken;
		
		String data = cache.get(url);
		
		if (data == null) {
			try {
				HttpGet dataGet = new HttpGet(url);
				HttpResponse dataResponse = httpClient.execute(dataGet);
				data = EntityUtils.toString(dataResponse.getEntity());
				cache.put(url, data);
			} catch (Exception e) {
				cache.put(url, "FAILED");
				LOGGER.error("Setting URL " + url + " as FAILED.");
				throw e;
			}
		} else if (data.equals("FAILED")) {
			throw new Exception();
		}
		
		if(data != null && !data.isEmpty()) {			
			String[] meta = data.split("\n");
			
			for (int i = 0; i < meta.length; i++) {
				JSONObject jsonObject = new JSONObject();
				String[] lineSplit = meta[i].split(",");
				
				if (lineSplit[2].equals(inicio)) {
					jsonObject.put("Estacao", lineSplit[0]);
					jsonObject.put("Data", lineSplit[2]);
					jsonObject.put("Hora", lineSplit[3]);
					if(weatherVar.equals("TMP")) {					
						jsonObject.put("TempBulboSeco", lineSplit[5]);
					} else if(weatherVar.equals("WND")) {
						jsonObject.put("DirecaoVento", lineSplit[5]);
						jsonObject.put("VelocidadeVento", lineSplit[8]);
					} else if(weatherVar.equals("DEW")) {
						jsonObject.put("TempBulboUmido", lineSplit[5]);
					}
				}
				dataArray.put(jsonObject);
			}
		}
	}
	
	private JSONArray readStation(HttpClient httpClient, String id, String inicio, String fim)
			throws Exception {
		String url = "http://www.inmet.gov.br/projetos/rede/pesquisa/gera_serie_txt.php?"
						+ "&mRelEstacao=" + id 
						+ "&btnProcesso=serie"
						+ "&mRelDtInicio=" + inicio
						+ "&mRelDtFim=" + fim
						+ "&mAtributos=1,1,,,1,1,,1,1,,,,,,,,";
		String data = cache.get(url);
		if (data == null) {
			try {
				HttpGet dataGet = new HttpGet(url);
				HttpResponse dataResponse = httpClient.execute(dataGet);
				data = EntityUtils.toString(dataResponse.getEntity());
				data = data.substring(data.indexOf("<pre>") + 5, data.indexOf("</pre>"));
				cache.put(url, data);
			} catch (Exception e) {
				cache.put(url, "FAILED");
				LOGGER.error("Setting URL " + url + " as FAILED.");
				throw e;
			}
		} else if (data.equals("FAILED")) {
			throw new Exception();
		}
		
		String[] meta = data.split(SEP)[4].trim().split("\n");
		
		JSONArray dataArray = new JSONArray();
		
		String[] splitHeader = meta[0].split(";");
		
		for (int i = 1; i < meta.length; i++) {
			JSONObject jsonObject = new JSONObject();
			String[] lineSplit = meta[i].split(";");
			for (int j = 0; j < lineSplit.length; j++) {
				jsonObject.put(splitHeader[j], lineSplit[j]);
			}
			dataArray.put(jsonObject);
		}
		
		for (int i = 0; i < dataArray.length(); i++) {
			JSONObject stationDataRecord = dataArray.optJSONObject(i);
			String temp = stationDataRecord.optString("TempBulboSeco");
			String vel = stationDataRecord.optString("VelocidadeVento");
			
			if (!temp.isEmpty() && !vel.isEmpty()) {
				return dataArray;
			}
		}
		
		cache.put(url, "FAILED");
		throw new Exception();
	}

	private JSONObject findClosestRecord(Date date, List<JSONObject> stations) {
		Date inicio = new Date(date.getTime() - A_DAY);
		Date fim = new Date(date.getTime() + A_DAY);
		
		JSONObject closestRecord = null;
		Long smallestDiff = Long.MAX_VALUE;
		
		for (JSONObject station : stations) {
			try {
				JSONArray stationData = readStation(httpClient, station.optString("id"), 
						DATE_FORMAT.format(inicio), DATE_FORMAT.format(fim));
				for (int i = 0; i < stationData.length(); i++) {
					JSONObject stationDataRecord = stationData.optJSONObject(i);
					String dateValue = stationDataRecord.optString("Data");
					String timeValue = stationDataRecord.optString("Hora");
					
					if (!stationDataRecord.optString("TempBulboSeco").isEmpty() && !stationDataRecord.optString("VelocidadeVento").isEmpty()) {
						Date recordDate = DATE_TIME_FORMAT.parse(dateValue + ";" + timeValue);
						long diff = Math.abs(recordDate.getTime() - date.getTime());
						if (diff < smallestDiff) {
							smallestDiff = diff;
							closestRecord = stationDataRecord;
						}						
					}
					
				}
				
				return closestRecord;
			} catch (Exception e) {
				LOGGER.error("Error while reading station.", e);
			}
		}
		return null;
		
	}
	
	private String readFullNOAARecord(Date date, List<JSONObject> stations, int numberOfDays) {
		Date inicio = new Date(date.getTime() - numberOfDays * A_DAY);
		Date fim = new Date(date.getTime() + numberOfDays * A_DAY);
		
		for (JSONObject station : stations) {
			try {
				JSONArray stationData = readNOAAStation(station.optString("id"), DATE_FORMAT.format(inicio),
						DATE_FORMAT.format(fim));

				JSONObject closestRecord = null;
				Long smallestDiff = Long.MAX_VALUE;

				for (int i = 0; i < stationData.length(); i++) {
					JSONObject stationDataRecord = stationData.optJSONObject(i);
					String dateValue = stationDataRecord.optString("Data");
					String timeValue = stationDataRecord.optString("Hora");

					Date recordDate = DATE_TIME_FORMAT.parse(dateValue + ";"
							+ timeValue);
					long diff = Math.abs(recordDate.getTime() - date.getTime());
					if (diff < smallestDiff) {
						smallestDiff = diff;
						closestRecord = stationDataRecord;
					}
					
					if (!closestRecord.optString("Data").isEmpty()
							&& !closestRecord.optString("Hora").isEmpty()
							&& !closestRecord.optString("TempBulboSeco")
									.isEmpty()
							&& !closestRecord.optString("TempBulboUmido")
									.isEmpty()
							&& !closestRecord.optString("VelocidadeVento")
									.isEmpty()
							&& Double.parseDouble(closestRecord
									.optString("VelocidadeVento")) >= 0.3
							&& !closestRecord.optString("DirecaoVento")
									.isEmpty()) {
						return generateStationData(stationData, closestRecord);
					} else if(Double.parseDouble(closestRecord
							.optString("VelocidadeVento")) < 0.3) {
						closestRecord.remove("VelocidadeVento");
						closestRecord.put("VelocidadeVento", "0.3");
					}
				} 
			} catch(Exception e) {
				LOGGER.error("Error while reading full NOAA record", e);
			}
		}		
		
		return null;
	}
	
	private String readFullRecord(Date date, List<JSONObject> stations, int numberOfDays) {
		Date inicio = new Date(date.getTime() - numberOfDays * A_DAY);
		Date fim = new Date(date.getTime() + numberOfDays * A_DAY);
		
		for (JSONObject station : stations) {
			try {
				JSONArray stationData = readStation(httpClient,
						station.optString("id"), DATE_FORMAT.format(inicio),
						DATE_FORMAT.format(fim));

				JSONObject closestRecord = null;
				Long smallestDiff = Long.MAX_VALUE;

				for (int i = 0; i < stationData.length(); i++) {
					JSONObject stationDataRecord = stationData.optJSONObject(i);
					String dateValue = stationDataRecord.optString("Data");
					String timeValue = stationDataRecord.optString("Hora");

					Date recordDate = DATE_TIME_FORMAT.parse(dateValue + ";"
							+ timeValue);
					long diff = Math.abs(recordDate.getTime() - date.getTime());
					if (diff < smallestDiff) {
						smallestDiff = diff;
						closestRecord = stationDataRecord;
					}
					
					if (!closestRecord.optString("Data").isEmpty()
							&& !closestRecord.optString("Hora").isEmpty()
							&& !closestRecord.optString("TempBulboSeco")
									.isEmpty()
							&& !closestRecord.optString("TempBulboUmido")
									.isEmpty()
							&& !closestRecord.optString("VelocidadeVento")
									.isEmpty()
							&& Double.parseDouble(closestRecord
									.optString("VelocidadeVento")) >= 0.3
							&& !closestRecord.optString("UmidadeRelativa")
									.isEmpty()
							&& !closestRecord.optString("DirecaoVento")
									.isEmpty()
							&& !closestRecord.optString("PressaoAtmEstacao")
									.isEmpty()) {
						return generateStationData(stationData, closestRecord);
					} else if(Double.parseDouble(closestRecord
							.optString("VelocidadeVento")) < 0.3) {
						closestRecord.remove("VelocidadeVento");
						closestRecord.put("VelocidadeVento", "0.3");
					}
				} 
			} catch(Exception e) {				
					
			}
		}		
		
		return null;
	}
	
	private String readClosestRecord(Date date, List<JSONObject> stations, int numberOfDays) {
		Date inicio = new Date(date.getTime() - numberOfDays * A_DAY);
		Date fim = new Date(date.getTime() + numberOfDays * A_DAY);
		
		for (JSONObject station : stations) {
			try {
				JSONArray stationData = readStation(httpClient,
						station.optString("id"), DATE_FORMAT.format(inicio),
						DATE_FORMAT.format(fim));

				JSONObject closestRecord = null;
				Long smallestDiff = Long.MAX_VALUE;

				for (int i = 0; i < stationData.length(); i++) {
					JSONObject stationDataRecord = stationData.optJSONObject(i);
					String dateValue = stationDataRecord.optString("Data");
					String timeValue = stationDataRecord.optString("Hora");

					Date recordDate = DATE_TIME_FORMAT.parse(dateValue + ";"
							+ timeValue);
					long diff = Math.abs(recordDate.getTime() - date.getTime());
					if (diff < smallestDiff) {
						smallestDiff = diff;
						closestRecord = stationDataRecord;
					}
				}
			
				if (!closestRecord.optString("Data").isEmpty()
						&& !closestRecord.optString("Hora").isEmpty()
						&& !closestRecord.optString("TempBulboSeco").isEmpty()
						&& !closestRecord.optString("VelocidadeVento")
								.isEmpty()) {
					return generateStationData(stationData, closestRecord);
				}
				
				
			} catch (Exception e) {
//				LOGGER.error("Error while reading station.", e);
//				return null;
			}
		}
		return null;
		
	}

	private String generateStationData(JSONArray stationData, JSONObject closestRecord) {
		StringBuilder toReturn = new StringBuilder();
		for (int i = 0; i < stationData.length(); i++) {
			JSONObject stationDataRecord = stationData.optJSONObject(i);

			String dateValue = stationDataRecord.optString("Data");
			String timeValue = stationDataRecord.optString("Hora");
			String temBulboSeco = stationDataRecord.optString("TempBulboSeco");
			String temBulboUmido = stationDataRecord
					.optString("TempBulboUmido");
			String estacao = stationDataRecord.optString("Estacao");
			String umidadeRelativa = stationDataRecord
					.optString("UmidadeRelativa");
			String pressaoAtmEstacao = stationDataRecord
					.optString("PressaoAtmEstacao");
			String direcaoVento = stationDataRecord.optString("DirecaoVento");
			String velocidadeVento = stationDataRecord
					.optString("VelocidadeVento");
			
			if(closestRecord.optString("TempBulboSeco").isEmpty()) {
				temBulboSeco = "NA";
			}		
			if(closestRecord.optString("UmidadeRelativa").isEmpty()) {
				umidadeRelativa = "NA";
			}			
			if(closestRecord.optString("TempBulboUmido").isEmpty()) {
				temBulboUmido = "NA";
			}			
			if(closestRecord.optString("PressaoAtmEstacao").isEmpty()) {
				pressaoAtmEstacao = "NA";
			}			
			if(closestRecord.optString("DirecaoVento").isEmpty()) {
				direcaoVento = "NA";
			}			
			if(closestRecord.optString("VelocidadeVento").isEmpty()) {
				velocidadeVento = "NA";
			}
			
			toReturn.append(estacao + ";" + dateValue + ";" + timeValue + ";"
					+ temBulboSeco + ";" + temBulboUmido + ";"
					+ umidadeRelativa + ";" + pressaoAtmEstacao + ";"
					+ direcaoVento + ";" + velocidadeVento + ";\n");
		}
		return toReturn.toString().trim();
	}

	public double Ta(double lat, double lon, Date date) {
		List<JSONObject> station = findNearestStation(lat, lon);
		JSONObject record = findClosestRecord(date, station);
		if (record == null) {
			return Double.NaN;
		}
//		System.out.println("record: " + record);
		//TODO review it
//		return Double.parseDouble(record.optString("TempBulboSeco"));
//		return 32.23;
//		return 18.21; //Europe
		if (properties.getProperty("temperatura_ar") != null) {
			return Double.parseDouble(properties.getProperty("temperatura_ar"));
		}
		return Double.parseDouble(record.optString("TempBulboSeco"));	
	}
	
	public double ux(double lat, double lon, Date date) {
		List<JSONObject> station = findNearestStation(lat, lon);
		JSONObject record = findClosestRecord(date, station);
		if (record == null) {
			return Double.NaN;
		}
		//TODO review it
//		return Math.max(Double.parseDouble(record.optString("VelocidadeVento")), 1.);
//		return 4.388;
//		return 2.73; //Europe
//		return Double.parseDouble(properties.getProperty("velocidade_vento"));
		if (properties.getProperty("velocidade_vento") != null) {
			return Double.parseDouble(properties.getProperty("velocidade_vento"));
		}
		return Math.max(Double.parseDouble(record.optString("VelocidadeVento")), 1.);
	}

	public double zx(double lat, double lon) {
//		List<JSONObject> station = findNearestStation(lat, lon);
//		return station.get(0).optDouble("altitude");
		//TODO Procurar a altitude do sensor da velocidade do vento
//		return 6.;
//		return 7.3; //Europe
		if (properties.getProperty("altitude_sensor_velocidade") != null){
			return Double.parseDouble(properties.getProperty("altitude_sensor_velocidade"));
		}
		return 6.;
	}

	public double d(double lat, double lon) {
		return 4. * 2/3;
	}
	
	public double hc(double lat, double lon) {
//		return 7.3; //Europe
//		return 4.0;
		if (properties.getProperty("hc") != null) {
			return Double.parseDouble(properties.getProperty("hc"));
		}
		return 4.0;
	}
	
	public String getNOAAStationData(double lat, double lon, Date date) {
		List<JSONObject> station = findNearestStation(lat, lon);
		return readFullRecord(date, station, 0);
	}

	public String getStationData(double lat, double lon, Date date) {
		List<JSONObject> station = findNearestStation(lat, lon);
		//return readClosestRecord(date, station, 0);
		//return readFullRecord(date, station, 0);
		return readFullNOAARecord(date, station, 0);
	}
	
	public Properties getProperties() {
		return properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	public static void main(String[] args) throws Exception {
		Properties properties = new Properties();
		FileInputStream input = new FileInputStream("sebal.conf");
		properties.load(input);

		WeatherStation weatherStation = new WeatherStation();		
		weatherStation.setProperties(properties);
		weatherStation.readNOAAStation("82753", "19950101", "19950102");
	}
}
