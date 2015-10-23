package org.fogbowcloud.sebal;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.sebal.model.image.DefaultImagePixel;
import org.fogbowcloud.sebal.model.image.GeoLoc;
import org.fogbowcloud.sebal.model.image.HOutput;
import org.fogbowcloud.sebal.model.image.Image;
import org.fogbowcloud.sebal.model.image.ImagePixel;
import org.fogbowcloud.sebal.model.image.ImagePixelOutput;
import org.fogbowcloud.sebal.model.satellite.JSONSatellite;
import org.fogbowcloud.sebal.model.satellite.Satellite;
import org.fogbowcloud.sebal.wrapper.Wrapper;
import org.junit.Test;

public class TestImageLandsat5 {
	
	private PixelQuenteFrioChooser pixelQuenteFrioChooser;
	private static final Logger LOGGER = Logger.getLogger(TestImageLandsat5.class);
	private List<BoundingBoxVertice> boundingBoxVertices;
	private TestImageHelper imageHelper;
	private String filePath;
	private Satellite satellite;
	private Properties properties;
	
	public TestImageLandsat5() throws IOException {
		this.properties = new Properties();
		this.pixelQuenteFrioChooser = new ClusteredPixelQuenteFrioChooser(this.properties);
		imageHelper = new TestImageHelper();
		boundingBoxVertices = new ArrayList<BoundingBoxVertice>();
		filePath = "/home/esdras/Documentos/Fogbow/Estudo/SEBAL";
		satellite = null;
	}

	@Test
	public void acceptanceTest() throws Exception {
		
		String desiredFlag = "desiredValues";
		Image desiredValues = imageHelper.readPixelsFromCSV(filePath, this.pixelQuenteFrioChooser, 
				desiredFlag, satellite);
		
		List<ImagePixel> desiredList = desiredValues.pixels();
		
		Image updatedImage = F1(this.pixelQuenteFrioChooser);
		
		List<ImagePixel> obtainedValues = updatedImage.pixels();
		
		// List<ImagePixel> obtainedValues = processPixelsFromFile(obtainedValuesFile);
		//DefaultImagePixel obtainedImagePixel = new DefaultImagePixel();
		// DefaultImagePixel obtainedImagePixel = processPixelsFromFile(obtainedValuesFile);
		// Modify processPixelsFromFile() to return a image instead of a list, this way is possible to use the code
		// beneath here
		
		// See if the arguments on 'for' are correct
		for (int i = 0; i < obtainedValues.size(); i++) {
			//System.out.println(i);			
			GeoLoc desiredGeoLoc = desiredList.get(i).geoLoc();
			ImagePixelOutput desiredOutput = desiredList.get(i).output();

			// Verify how to fix this
			GeoLoc obtainedGeoLoc = obtainedValues.get(i).geoLoc();
			ImagePixelOutput obtainedOutput = obtainedValues.get(i).output();

			assertEquals(obtainedGeoLoc.getI(), desiredGeoLoc.getI());
			assertEquals(obtainedGeoLoc.getJ(), desiredGeoLoc.getJ());
			
			double[] desiredL = desiredList.get(i).L();
			double[] obtainedL = obtainedValues.get(i).L();
			
			for(int j = 0; j < desiredList.get(i).L().length; j++) {
				System.out.println(desiredL[j] + " - " + obtainedL[j]);
				assertField(desiredL[j], obtainedL[j]);
			}
			
			double[] desiredRho = desiredOutput.getRho();
			double[] obtainedRho = obtainedOutput.getRho();
			
			for(int j = 0; j < desiredList.get(i).output().getRho().length; j++)
				assertField(desiredRho[j], obtainedRho[j]);
			
			assertField(desiredOutput.G(), obtainedOutput.G());
			assertField(desiredOutput.Rn(), obtainedOutput.Rn());
			assertField(desiredOutput.getLambdaE(), obtainedOutput.getLambdaE());
			assertField(desiredOutput.getTs(), obtainedOutput.getTs());
			assertField(desiredOutput.getNDVI(), obtainedOutput.getNDVI());
			assertField(desiredOutput.SAVI(), obtainedOutput.SAVI());
			assertField(desiredOutput.getAlpha(), obtainedOutput.getAlpha());
			assertField(desiredOutput.getZ0mxy(), obtainedOutput.getZ0mxy());
			assertField(desiredOutput.getEpsilonZero(), obtainedOutput.getEpsilonZero());
			assertField(desiredOutput.getEpsilonNB(), obtainedOutput.getEpsilonNB());
			assertField(desiredOutput.getRLDown(), obtainedOutput.getRLDown());
			assertField(desiredOutput.getEpsilonA(), obtainedOutput.getEpsilonA());
			assertField(desiredOutput.getRLUp(), obtainedOutput.getRLUp());
			assertField(desiredOutput.getIAF(), obtainedOutput.getIAF());
			assertField(desiredOutput.getEVI(), obtainedOutput.getEVI());
			assertField(desiredOutput.getRSDown(), obtainedOutput.getRSDown());
			assertField(desiredOutput.getTauSW(), obtainedOutput.getTauSW());
			assertField(desiredOutput.getAlphaToa(), obtainedOutput.getAlphaToa());
			// Verify how to fix this
			/*HOutput desiredHOutIncial = desiredOutput.gethOuts().get(0);
			HOutput obtainedHOutIncial = obtainedOutput.gethOuts().get(0);
			
			assertField(desiredHOutIncial.getH(), obtainedHOutIncial.getH());
			assertField(desiredHOutIncial.getA(), obtainedHOutIncial.getA());
			assertField(desiredHOutIncial.getB(), obtainedHOutIncial.getB());
			assertField(desiredHOutIncial.getRah(), obtainedHOutIncial.getRah());
			assertField(desiredHOutIncial.getuAsterisk(), obtainedHOutIncial.getuAsterisk());
			assertField(desiredHOutIncial.getL(), obtainedHOutIncial.getL());

			HOutput desiredHOutFinal = desiredOutput.gethOuts().get(1);
			HOutput obtainedHOutFinal = obtainedOutput.gethOuts().get(1);
			
			assertField(desiredHOutFinal.getH(), obtainedHOutFinal.getH());
			assertField(desiredHOutFinal.getA(), obtainedHOutFinal.getA());
			assertField(desiredHOutFinal.getB(), obtainedHOutFinal.getB());
			assertField(desiredHOutFinal.getRah(), obtainedHOutFinal.getRah());
			assertField(desiredHOutFinal.getuAsterisk(), obtainedHOutFinal.getuAsterisk());
			assertField(desiredHOutFinal.getL(), obtainedHOutFinal.getL());

			assertField(desiredOutput.G(), obtainedOutput.G());
			assertField(desiredOutput.Rn(), obtainedOutput.Rn());
			assertField(desiredOutput.getLambdaE(), obtainedOutput.getLambdaE());
			assertField(desiredOutput.getTs(), obtainedOutput.getTs());
			assertField(desiredOutput.getNDVI(), obtainedOutput.getNDVI());
			assertField(desiredOutput.SAVI(), obtainedOutput.SAVI());
			assertField(desiredOutput.getAlpha(), obtainedOutput.getAlpha());
			assertField(desiredOutput.getZ0mxy(), obtainedOutput.getZ0mxy());
			assertField(desiredOutput.getEpsilonZero(), obtainedOutput.getEpsilonZero());
			assertField(desiredOutput.getEpsilonNB(), obtainedOutput.getEpsilonNB());
			assertField(desiredOutput.getRLDown(), obtainedOutput.getRLDown());
			assertField(desiredOutput.getEpsilonA(), obtainedOutput.getEpsilonA());
			assertField(desiredOutput.getRLUp(), obtainedOutput.getRLUp());
			assertField(desiredOutput.getIAF(), obtainedOutput.getIAF());
			assertField(desiredOutput.getEVI(), obtainedOutput.getEVI());
			assertField(desiredOutput.getRSDown(), obtainedOutput.getRSDown());
			assertField(desiredOutput.getTauSW(), obtainedOutput.getTauSW());
			assertField(desiredOutput.getAlphaToa(), obtainedOutput.getAlphaToa());*/
		}
	}
	
	public Image F1(PixelQuenteFrioChooser pixelQuenteFrioChooser) throws Exception {
		
		System.out.println("Executing F1 phase...");
		//LOGGER.info("Executing F1 phase...");
		long now = System.currentTimeMillis();
		
		satellite = new JSONSatellite("landsat5");
		
		String obtainedFlag = "obtainedValues";
		// See if processPixelsFromFile can be used instead
		Image image = imageHelper.readPixelsFromCSV(filePath, pixelQuenteFrioChooser, obtainedFlag, 
				satellite);
		
		boolean cloudDetection = false;
		
		image.width(0);
		image.height(0);
		
		Image updatedImage = new SEBAL().processPixelQuentePixelFrio(image,
                satellite, boundingBoxVertices, image.width(), image.height(), cloudDetection);
		
		/*saveProcessOutput(updatedImage);
        savePixelQuente(updatedImage, getPixelQuenteFileName());
        savePixelFrio(updatedImage, getPixelFrioFileName());*/
        LOGGER.info("F1 phase execution time is " + (System.currentTimeMillis() - now));
		
        return updatedImage;
		
	}

	private void assertField(double desiredValue, double obtainedValue) {
		assertEquals(desiredValue, obtainedValue, Math.abs(desiredValue * 0.05));
	}

	interface PixelParser {
		ImagePixel parseLine(String[] line);
	}

}