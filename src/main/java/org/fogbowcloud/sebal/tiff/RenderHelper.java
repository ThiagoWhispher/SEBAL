package org.fogbowcloud.sebal.tiff;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.fogbowcloud.sebal.SEBALHelper;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.osr.SpatialReference;

import ucar.ma2.InvalidRangeException;

public class RenderHelper {

	private static double PIXEL_SIZE = 0.00027;

	public static final String TIFF = "tiff";
	public static final String BMP = "bmp";
	public static final String NET_CDF = "netcdf";

	public static void main(String[] args) throws ParseException, Exception {
		String csvFile = args[1];
		String mtlFilePath = args[2];
		String outputFilePrefix = args[3];
		int maskWidth = Integer.parseInt(args[4]);
		int maskHeight = Integer.parseInt(args[5]);

		long daysSince1970 = calculatingTimeDimension(mtlFilePath);

		render(csvFile, outputFilePrefix, maskWidth, maskHeight, daysSince1970, TIFF, NET_CDF, BMP);
	}

	public static long calculatingTimeDimension(String mtlFilePath) throws Exception,
			ParseException {
		Product product = SEBALHelper.readProduct(mtlFilePath, null);
		MetadataElement metadataRoot = product.getMetadataRoot();
		String dateAcquiredStr = metadataRoot.getElement("L1_METADATA_FILE")
				.getElement("PRODUCT_METADATA").getAttribute("DATE_ACQUIRED").getData()
				.getElemString();

		DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		Date date1970 = format.parse("1970-01-01");
		Date dateAcquired = format.parse(dateAcquiredStr);

		long daysSince1970 = (dateAcquired.getTime() - date1970.getTime()) / (24 * 60 * 60 * 1000);
		return daysSince1970;
	}

	private static class BandVariableBuilder {
		private String imgPrefix;
		private String outputPath;
		private int maskWidth;
		private int maskHeight;
		private Double ulLon;
		private Double ulLat;
		private Integer initialI;
		private Integer initialJ;
		private String[] drivers;

		public BandVariableBuilder(String imgPrefix, String outputPath, int maskWidth,
				int maskHeight, Double ulLon, Double ulLat, Integer initialI, Integer initialJ, String[] drivers) {
			this.imgPrefix = imgPrefix;
			this.outputPath = outputPath;
			this.maskWidth = maskWidth;
			this.maskHeight = maskHeight;
			this.ulLon = ulLon;
			this.ulLat = ulLat;
			this.initialI = initialI;
			this.initialJ = initialJ;
			if (drivers != null) {
				this.drivers = drivers;
			} else {
				this.drivers = new String[]{NET_CDF};
			}
		}

		public BandVariable build(String varName, int columnIdx) {
			return new BandVariable(varName, imgPrefix, outputPath, maskWidth, maskHeight, ulLon,
					ulLat, initialI, initialJ, columnIdx, drivers);
		}
	}

	private static class BandVariable {

		private int columnIdx;
		private Band tiffBand;
		private Band bmpBand;
		private Band netCDFBand;
		private double[] rasterTiff;
		private double[] rasterBmp;
		private Integer initialI;
		private Integer initialJ;
		private int maskWidth;
		private int maskHeight;
		private double[] rasterNetCDF;
		private String netCDFFile;
		private String varName;
		private String[] drivers;

		public BandVariable(String varName, String imgPrefix, String outputPath, int maskWidth,
				int maskHeight, Double ulLon, Double ulLat, Integer initialI, Integer initialJ,
				int columnIdx, String[] drivers) {

			this.varName = varName;
			this.maskWidth = maskWidth;
			this.maskHeight = maskHeight;
			this.initialI = initialI;
			this.initialJ = initialJ;
			this.columnIdx = columnIdx;
			this.drivers = drivers;

			for (String driver : drivers) {
				if (driver.equals(TIFF)){
					Driver tiffDriver = gdal.GetDriverByName("GTiff");
					String tiffFile = new File(outputPath, imgPrefix + "_" + varName + ".tiff")
					.getAbsolutePath();
					Dataset dstTiff = tiffDriver.Create(tiffFile, maskWidth, maskHeight, 1,
							gdalconstConstants.GDT_Float64);
					this.tiffBand = createBand(dstTiff, ulLon, ulLat);
				} else if (driver.equals(BMP)) {
					Driver bmpDriver = gdal.GetDriverByName("BMP");
					String bmpFile = new File(outputPath, imgPrefix + "_" + varName + ".bmp")
					.getAbsolutePath();
					Dataset dstBmp = bmpDriver.Create(bmpFile, maskWidth, maskHeight, 1,
							gdalconstConstants.GDT_Byte);
					this.bmpBand = createBand(dstBmp, ulLon, ulLat);
				} else if (driver.equals(NET_CDF)) {
					Driver netCDFDriver = gdal.GetDriverByName("NetCDF");
					this.netCDFFile = new File(outputPath, imgPrefix + "_" + varName + ".nc")
					.getAbsolutePath();
					Dataset dstNetCDF = netCDFDriver.Create(netCDFFile, maskWidth, maskHeight, 1,
							gdalconstConstants.GDT_Float64);
					this.netCDFBand = createBand(dstNetCDF, ulLon, ulLat);
				}
			}
			this.rasterTiff = new double[maskHeight * maskWidth];
			this.rasterBmp = new double[maskHeight * maskWidth];
			this.rasterNetCDF = new double[maskHeight * maskWidth];
		}

		public void read(String[] splitLine) {
			int i = Integer.parseInt(splitLine[0]);
			int j = Integer.parseInt(splitLine[1]);
			int iIdx = i - initialI;
			int jIdx = j - initialJ;
			double val = Double.parseDouble(splitLine[columnIdx]);
			rasterTiff[jIdx * maskWidth + iIdx] = val;
			rasterNetCDF[jIdx * maskWidth + iIdx] = val;
			rasterBmp[jIdx * maskWidth + iIdx] = val * 255;
		}

		public void render(double daysSince1970) {
			for (String format : drivers) {
				if (format.equals(TIFF)){
					tiffBand.WriteRaster(0, 0, maskWidth, maskHeight, rasterTiff);
					tiffBand.FlushCache();
				} else if (format.equals(BMP)) {
					bmpBand.WriteRaster(0, 0, maskWidth, maskHeight, rasterBmp);
					bmpBand.FlushCache();
				} else if (format.equals(NET_CDF)){
					netCDFBand.WriteRaster(0, 0, maskWidth, maskHeight, rasterBmp);
					netCDFBand.FlushCache();
					
					try {
						NetCDFHelper.normalize(netCDFFile, varName, daysSince1970);
					} catch (IOException | InvalidRangeException e) {
						e.printStackTrace();
					}
				}				
			}
		}

		private static Band createBand(Dataset dstNdviTiff, Double ulLon, Double ulLat) {
			dstNdviTiff
					.SetGeoTransform(new double[] { ulLon, PIXEL_SIZE, 0, ulLat, 0, -PIXEL_SIZE });
			SpatialReference srs = new SpatialReference();
			srs.SetWellKnownGeogCS("WGS84");
			dstNdviTiff.SetProjection(srs.ExportToWkt());
			Band bandNdvi = dstNdviTiff.GetRasterBand(1);
			return bandNdvi;
		}
	}

	public static void render(String csvFile, String outputFilePrefix, int maskWidth,
			int maskHeight, double daysSince1970, String... drivers) throws IOException, FileNotFoundException {
		gdal.AllRegister();
	
		Double latMax = -360.;
		Double lonMin = +360.;
		Integer initialI = null;
		Integer initialJ = null;

		LineIterator lineIterator = IOUtils.lineIterator(new FileInputStream(csvFile),
				Charsets.UTF_8);
		while (lineIterator.hasNext()) {
			String line = (String) lineIterator.next();
			String[] lineSplit = line.split(",");
			if (initialI == null && initialJ == null) {
				initialI = Integer.parseInt(lineSplit[0]);
				initialJ = Integer.parseInt(lineSplit[1]);
			}
			Double lat = Double.parseDouble(lineSplit[2]);
			Double lon = Double.parseDouble(lineSplit[3]);
			latMax = Math.max(lat, latMax);
			lonMin = Math.min(lon, lonMin);
		}

		BandVariableBuilder bandVariableBuilder = new BandVariableBuilder(outputFilePrefix, new File(
				csvFile).getParent(), maskWidth, maskHeight, lonMin, latMax, initialI, initialJ, drivers);
		List<BandVariable> vars = new LinkedList<BandVariable>();
		vars.add(bandVariableBuilder.build("ndvi", 7));
		vars.add(bandVariableBuilder.build("evi", 18));
		vars.add(bandVariableBuilder.build("iaf", 17));
		vars.add(bandVariableBuilder.build("ts", 6));
		vars.add(bandVariableBuilder.build("alpha", 9));
		vars.add(bandVariableBuilder.build("rn", 5));
		vars.add(bandVariableBuilder.build("g", 4));

		lineIterator = IOUtils.lineIterator(new FileInputStream(csvFile), Charsets.UTF_8);

		while (lineIterator.hasNext()) {
			String line = (String) lineIterator.next();
			String[] lineSplit = line.split(",");
			for (BandVariable var : vars) {
				var.read(lineSplit);
			}
		}

		for (BandVariable var : vars) {
			var.render(daysSince1970);
		}
	}

}
