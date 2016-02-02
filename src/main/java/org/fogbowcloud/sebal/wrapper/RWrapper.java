package org.fogbowcloud.sebal.wrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.esa.beam.framework.datamodel.Product;
import org.fogbowcloud.sebal.BoundingBoxVertice;
import org.fogbowcloud.sebal.ClusteredPixelQuenteFrioChooser;
import org.fogbowcloud.sebal.PixelQuenteFrioChooser;
import org.fogbowcloud.sebal.SEBALHelper;
import org.fogbowcloud.sebal.model.image.BoundingBox;
import org.fogbowcloud.sebal.model.image.Image;
import org.fogbowcloud.sebal.model.image.ImagePixel;

public class RWrapper {
	
	private String mtlFile;
    private int iBegin;
    private int iFinal;
    private int jBegin;
    private int jFinal;
    private String outputDir;
    private PixelQuenteFrioChooser pixelQuenteFrioChooser;
    private List<BoundingBoxVertice> boundingBoxVertices = new ArrayList<BoundingBoxVertice>();
    private String fmaskFilePath;
    private String rScriptFilePath;
    
	private static final Logger LOGGER = Logger.getLogger(Wrapper.class);
    
	public RWrapper(Properties properties) throws IOException {
		String mtlFilePath = properties.getProperty("mtl_file_path");
		if (mtlFilePath == null || mtlFilePath.isEmpty()) {
			LOGGER.error("Property mtl_file_path must be set.");
			throw new IllegalArgumentException("Property mtl_file_path must be set.");
		}
		this.mtlFile = mtlFilePath;
		
		String iBeginStr = properties.getProperty("i_begin_interval");
		String iFinalStr = properties.getProperty("i_final_interval");
		String jBeginStr = properties.getProperty("j_begin_interval");
		String jFinalStr = properties.getProperty("j_final_interval");
		
		if (iBeginStr == null || iFinalStr == null || jBeginStr == null || jFinalStr == null) {
			LOGGER.error("Interval properties (i_begin_interval, i_final_interval, j_begin_interval, and j_final_interval) must be set.");
			throw new IllegalArgumentException(
					"Interval properties (i_begin_interval, i_final_interval, j_begin_interval, and j_final_interval) must be set.");
		}
		this.iBegin = Integer.parseInt(iBeginStr);
		this.iFinal = Integer.parseInt(iFinalStr);
		this.jBegin = Integer.parseInt(jBeginStr);
		this.jFinal = Integer.parseInt(jFinalStr);
		
		LOGGER.debug("i interval: (" + iBegin + ", " + iFinal + ")");
		LOGGER.debug("j interval: (" + jBegin + ", " + jFinal + ")");
		
		boundingBoxVertices = SEBALHelper.getVerticesFromFile(properties.getProperty("bounding_box_file_path"));

		this.pixelQuenteFrioChooser = new ClusteredPixelQuenteFrioChooser(properties);

		String fileName = new File(mtlFile).getName();
		String mtlName = fileName.substring(0, fileName.indexOf("_"));
		String outputDir = properties.getProperty("output_dir_path");

		if (outputDir == null || outputDir.isEmpty()) {
    		this.outputDir = mtlName;
    	} else {
    		if (!new File(outputDir).exists() || !new File(outputDir).isDirectory()) {
    			new File(outputDir).mkdirs();
    		}
    		this.outputDir = outputDir + "/" + mtlName;
    	}
		
		fmaskFilePath = properties.getProperty("fmask_file_path");
	}
	
	public RWrapper(String mtlFile, String outputDir, int iBegin, int iFinal, int jBegin,
			int jFinal, String mtlName, String boundingBoxFileName, Properties properties,
			String fmaskFilePath, String rScriptFilePath) throws IOException {
		this.mtlFile = mtlFile;
		this.iBegin = iBegin;
		this.iFinal = iFinal;
		this.jBegin = jBegin;
		this.jFinal = jFinal;

		boundingBoxVertices = SEBALHelper.getVerticesFromFile(boundingBoxFileName);

		// this.pixelQuenteFrioChooser = new RandomPixelQuenteFrioChooser();
		// this.pixelQuenteFrioChooser = new DefaultPixelQuenteFrioChooser();
		this.pixelQuenteFrioChooser = new ClusteredPixelQuenteFrioChooser(properties);
		if (outputDir == null) {
			this.outputDir = mtlName;
		} else {
			if (!new File(outputDir).exists() || !new File(outputDir).isDirectory()) {
				new File(outputDir).mkdirs();
			}
			this.outputDir = outputDir + "/" + mtlName;
		}
		this.fmaskFilePath = fmaskFilePath;
	}
	
	public void doTask(String taskType) throws Exception {
		try {
        	if(taskType.equalsIgnoreCase(TaskType.PREPROCESS)) {
        		preProcessingPixels(pixelQuenteFrioChooser);
                return;
        	}        	
        	if(taskType.equalsIgnoreCase(TaskType.F1RCALL)) {
        		rF1ScriptCaller(rScriptFilePath);
        		return;
        	}
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(128);
        }
	}
	
    private void rF1ScriptCaller(String rScriptFilePath) throws IOException, InterruptedException {
    	LOGGER.info("Calling F1 R script...");
    	
    	long now = System.currentTimeMillis();    	
    	
    	Process p = Runtime.getRuntime().exec("Rscript " + rScriptFilePath);
    	p.waitFor();
    	
    	LOGGER.info("F1 R script execution time is " + (System.currentTimeMillis() - now));
	}
    
	public void preProcessingPixels(PixelQuenteFrioChooser pixelQuenteFrioChooser) 
    		throws Exception{
    	LOGGER.info("Pre processing pixels...");
    	
    	long now = System.currentTimeMillis();
        Product product = SEBALHelper.readProduct(mtlFile, boundingBoxVertices);
        
        BoundingBox boundingBox = null;
        if (boundingBoxVertices.size() > 3) {
        	boundingBox = SEBALHelper.calculateBoundingBox(boundingBoxVertices, product);
        	LOGGER.debug("bounding_box: X=" + boundingBox.getX() + " - Y=" + boundingBox.getY());
        	LOGGER.debug("bounding_box: W=" + boundingBox.getW() + " - H=" + boundingBox.getH());
        }             
                
        Image image = SEBALHelper.readPixels(product, iBegin, iFinal, jBegin,
                jFinal, pixelQuenteFrioChooser, boundingBox, fmaskFilePath);
        
		Image preProcessedImage = SEBALHelper.readPreProcessedData(image, product,
				boundingBoxVertices, pixelQuenteFrioChooser);
        
        LOGGER.debug("Pre process time read = " + (System.currentTimeMillis() - now));
        
        saveElevationOutput(preProcessedImage);
        saveTaOutput(preProcessedImage);
        
        saveDadosOutput(preProcessedImage);
        
        LOGGER.info("Pre process execution time is " + (System.currentTimeMillis() - now));
    }
	
    private void saveDadosOutput(Image image) {
    	long now = System.currentTimeMillis();
		String dadosFileName = getDadosFileName();
		String resultLine = new String();		

		File outputFile = new File(dadosFileName);
		try {
			FileUtils.write(outputFile, "");
			for (int i = 0; i < 2; i++) {
				if(i == 0) {
					resultLine = getRow("File Images", "File Elevation", "MTL", "File Ta", "Output Path", "Prefix");
				} else {
				resultLine = getRow();
				}
				FileUtils.write(outputFile, resultLine, true);
				i++;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		LOGGER.debug("Saving process output time="
				+ (System.currentTimeMillis() - now));		
	}
    
	private String getDadosFileName() {
		return SEBALHelper.getDadosFilePath(outputDir, "");
	}
	
    
	private void saveElevationOutput(Image image) {
		long now = System.currentTimeMillis();
		List<ImagePixel> pixels = image.pixels();
		String elevationPixelsFileName = getElevationFileName();
		int count = 0;
		String resultLine = new String();		

		File outputFile = new File(elevationPixelsFileName);
		try {
			FileUtils.write(outputFile, "");
			for (ImagePixel imagePixel : pixels) {
				if(count == 0) {
					resultLine = getRow("latitude", "longitude", "elevation");
				} else {
				resultLine = getRow(imagePixel.geoLoc().getLat(), imagePixel.geoLoc().getLon(),
						imagePixel.z());
				}
				FileUtils.write(outputFile, resultLine, true);
				count++;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		LOGGER.debug("Saving process output time="
				+ (System.currentTimeMillis() - now));
	}
	
	private void saveTaOutput(Image image) {
		long now = System.currentTimeMillis();
		List<ImagePixel> pixels = image.pixels();
		String weatherPixelsFileName = getWeatherFileName();
		int count = 0;
		String resultLine = new String();
		
		File outputFile = new File(weatherPixelsFileName);
		try {
			FileUtils.write(outputFile, "");
			for (ImagePixel imagePixel : pixels) {
				if(count == 0) {
					resultLine = getRow("latitude", "longitude", "Ta");
				} else {					
				resultLine = getRow(imagePixel.geoLoc().getLat(), imagePixel.geoLoc().getLon(),
						imagePixel.Ta());
				}
				
				FileUtils.write(outputFile, resultLine, true);
				count++;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		LOGGER.debug("Saving process output time="
				+ (System.currentTimeMillis() - now));
	}
	
    private String getWeatherFileName() {
    	return SEBALHelper.getWeatherFilePath(outputDir, "", iBegin, iFinal, jBegin, jFinal);
    }
    
    private String getElevationFileName() {
    	return SEBALHelper.getElevationFilePath(outputDir, "", iBegin, iFinal, jBegin, jFinal);
    }
    
    private static String getRow(Object... rowItems) {
        StringBuilder sb = new StringBuilder();
        for (Object rowItem : rowItems) {
            sb.append(rowItem).append(",");
        }
        sb.setCharAt(sb.length() - 1, '\n');
        return sb.toString();
    }

}
