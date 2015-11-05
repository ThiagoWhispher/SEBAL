package org.fogbowcloud.sebal;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.fogbowcloud.sebal.model.image.DefaultImage;
import org.fogbowcloud.sebal.model.image.DefaultImagePixel;
import org.fogbowcloud.sebal.model.image.GeoLoc;
import org.fogbowcloud.sebal.model.image.ImagePixel;
import org.fogbowcloud.sebal.model.image.ImagePixelOutput;
import org.fogbowcloud.sebal.model.satellite.Satellite;
import org.fogbowcloud.sebal.parsers.WeatherStation;
import org.fogbowcloud.sebal.wrapper.Wrapper.PixelParser;

public class TestImageHelper {
	
	private static final Logger LOGGER = Logger.getLogger(TestImageHelper.class);

	protected static DefaultImage setProperties( PixelQuenteFrioChooser pixelQuenteFrioChooser,
			Satellite satellite, WeatherStation station, List<ImagePixel> pixels,
			Double sunElevation, Date accquiredDate, double cosTheta) throws Exception {
		
		DefaultImage image = new DefaultImage(pixelQuenteFrioChooser);
        image.setDay(accquiredDate.getDay());
        // Scanning csv image to calculate and store values in another image
		for (ImagePixel pixelFromCSV : pixels) {
			
			DefaultImagePixel currentPixel = new DefaultImagePixel();
			
			currentPixel.L(pixelFromCSV.L());
			
			ImagePixelOutput output = new ImagePixelOutput();
			output = pixelFromCSV.output();
			currentPixel.setOutput(output);
			
			currentPixel.geoLoc(pixelFromCSV.geoLoc());
			currentPixel.cosTheta(cosTheta);
			
			double latitude = currentPixel.geoLoc().getLat();
			double longitude = currentPixel.geoLoc().getLon();

			// Setting elevation
			currentPixel.z(pixelFromCSV.z());
			
			// Calculate Ta based on image coordinates and date/time
			double Ta = station.Ta(latitude, longitude, accquiredDate);
			currentPixel.Ta(Ta);

			// Calculate ux based on image coordinates and date/time
			double ux = station.ux(latitude, longitude, accquiredDate);
			currentPixel.ux(ux);
			
			// Calculate zx based on image coordinates
			double zx = station.zx(latitude, longitude);
			currentPixel.zx(zx);

			// Calculate ux based on image coordinates
			double d = station.d(latitude, longitude);
			currentPixel.d(d);

			// Calculate ux based on image coordinates
			double hc = station.hc(latitude, longitude);
			currentPixel.hc(hc);
			
			// The csv pixel is then add to the other image pixel
			currentPixel.image(image);
			image.addPixel(currentPixel);
        }
		return image;
	}
	
	protected static List<ImagePixel> readExpectedPixelsFromFile(String dataFilePath,
			final Satellite satellite) throws IOException {
        return processPixelsFile(new PixelParser() {
            @Override
            public ImagePixel parseLine(String[] fields) {
                DefaultImagePixel imagePixel = new DefaultImagePixel();
                if(satellite.landsatName().equals(Satellite.LANDSAT_L7)) {
                	imagePixel.geoLoc(getGeoLocL7(fields));
                } else
                	imagePixel.geoLoc(getGeoLoc(fields));
                
                imagePixel.setOutput(getImagePixelOutput(fields));
                
                double band1 = Double.valueOf(fields[10]);
                double band2 = Double.valueOf(fields[11]);
                double band3 = Double.valueOf(fields[12]);
                double band4 = Double.valueOf(fields[13]);
                double band5 = Double.valueOf(fields[14]);
                double band6 = Double.valueOf(fields[15]);
                double band7 = Double.valueOf(fields[16]);
                double[] L = { band1, band2, band3, band4, band5, band6, band7 };
                imagePixel.L(L);
                
                double elevation = Double.valueOf(fields[9]);
                imagePixel.z(elevation);

                double[] rho = { Double.valueOf(fields[17]), Double.valueOf(fields[18]),
                		Double.valueOf(fields[19]), Double.valueOf(fields[20]),
                		Double.valueOf(fields[21]), 0.0, Double.valueOf(fields[23]) };
                imagePixel.output().setRho(rho);
                
                return imagePixel;
            }
        }, dataFilePath);
    }
	
	protected static List<ImagePixel> readInputPixelsFromFile(String dataFilePath,
			final Satellite satellite) throws IOException {
        return processPixelsFile(new PixelParser() {
            @Override
            public ImagePixel parseLine(String[] fields) {
                DefaultImagePixel imagePixel = new DefaultImagePixel();
                if(satellite.landsatName().equals(Satellite.LANDSAT_L7)) {
                	imagePixel.geoLoc(getGeoLocL7(fields));
                } else
                	imagePixel.geoLoc(getGeoLoc(fields));
                
                ImagePixelOutput output = new ImagePixelOutput();
                imagePixel.setOutput(output);
                
                double band1 = Double.valueOf(fields[10]);
                double band2 = Double.valueOf(fields[11]);
                double band3 = Double.valueOf(fields[12]);
                double band4 = Double.valueOf(fields[13]);
                double band5 = Double.valueOf(fields[14]);
                double band6 = Double.valueOf(fields[15]);
                double band7 = Double.valueOf(fields[16]);
                double[] L = { band1, band2, band3, band4, band5, band6, band7 };
                imagePixel.L(L);
                
                double elevation = Double.valueOf(fields[9]);
                imagePixel.z(elevation);

                return imagePixel;
            }
        }, dataFilePath);
    }
	
	private static GeoLoc getGeoLoc(String[] fields) {
        int i = 0;
        int j = 0;
        
        int zoneNumber = 24;        
        int centralMeridian = -39;       
        
        // Converting UTM to latitude and longitude
		LatLonCoordinate latLonCoordinate;
		try {
			latLonCoordinate = SEBALHelper.convertUtmToLatLon(Double.valueOf(fields[0]), 
					Double.valueOf(fields[1]),
					zoneNumber, centralMeridian);
			
			// Setting latitude in imagePixel
	        double latitude = Double.valueOf(String.format("%.10g%n",
	              latLonCoordinate.getLat()));
	        
	        // Setting longitude in imagePixel
	        double longitude = Double.valueOf(String.format("%.10g%n",
	              latLonCoordinate.getLon()));
	        
	        GeoLoc geoloc = new GeoLoc(i, j, latitude, longitude);
	        return geoloc;
		} catch (Exception e) {
			LOGGER.error("Error while converting coordinates.", e);
		}	
		
		return null;       
    }
	
	private static GeoLoc getGeoLocL7(String[] fields) {
        int i = 0;
        int j = 0;
        
        double lat = Double.valueOf(fields[0]);
		double lon = Double.valueOf(fields[1]);
		GeoLoc geoloc = new GeoLoc(i,j,lat,lon);
		return geoloc;       
    }
	
	private static ImagePixelOutput getImagePixelOutput(String[] fields) {
	        ImagePixelOutput output = new ImagePixelOutput();
	        output.setRn(Double.valueOf(fields[37]));
	        output.setTs(Double.valueOf(fields[33]));
	        output.setNDVI(Double.valueOf(fields[28]));
	        output.setSAVI(Double.valueOf(fields[29]));
	        output.setIAF(Double.parseDouble(fields[30]));
	        output.setAlpha(Double.valueOf(fields[26]));
	        output.setEpsilonZero(Double.valueOf(fields[32]));
	        output.setEpsilonNB(Double.valueOf(fields[31]));
	        output.setRLDown(Double.valueOf(fields[36]));
	        output.setEpsilonA(Double.valueOf(fields[35]));
	        output.setRLUp(Double.valueOf(fields[34]));
	        output.setRSDown(Double.valueOf(fields[27]));
	        output.setTauSW(Double.valueOf(fields[25]));
	        output.setAlphaToa(Double.valueOf(fields[24]));
	        return output;
	 }
	
	private static List<ImagePixel> processPixelsFile(PixelParser pixelParser,
            String file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        List<ImagePixel> pixels = new ArrayList<ImagePixel>();
        String line = null;
        while ((line = br.readLine()) != null) {
            String[] fields = line.split(",");
            ImagePixel imagePixel = pixelParser.parseLine(fields);
            pixels.add(imagePixel);
        }
        br.close();
        return pixels;
    }
}
