package org.fogbowcloud.sebal.parsers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;

import net.lingala.zip4j.core.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.sebal.wrapper.Wrapper;

public class Elevation {

    private static final int HGT_RETRY_COUNT = 3;

	private static final Logger LOGGER = Logger.getLogger(Elevation.class);

    /*
     * South America
     * http://dds.cr.usgs.gov/srtm/version2_1/SRTM3/South_America/
     * s0-16
     * w36-42
     * 
     * Eurasia
     * http://dds.cr.usgs.gov/srtm/version2_1/SRTM3/Eurasia/
     * n0-60
     * s1-13
     * e0-173
     * w1-14 
     */

    /** 1200 Intervals means 1201 positions per line and column */
    private static final int SRTM3_INTERVALS = 1200;
    private static final int SRTM3_FILE_SIZE = (SRTM3_INTERVALS + 1)
            * (SRTM3_INTERVALS + 1) * 2;
    private static final int SRTM1_INTERVALS = 3600;
    public static final int SRTM1_FILE_SIZE = (SRTM1_INTERVALS + 1)
            * (SRTM1_INTERVALS + 1) * 2;
    private static final int INVALID_VALUE_LIMIT = -15000; // Won't interpolate
                                                           // below this
                                                           // elevation in
                                                           // Meters, guess is:
                                                           // -0x8000

    private int getIntervalCount(RandomAccessFile file) throws IOException {
        long fileLength = file.length();
        if (fileLength == SRTM3_FILE_SIZE) {
            return SRTM3_INTERVALS;
        }
        if (fileLength == SRTM1_FILE_SIZE) {
            return SRTM1_INTERVALS;
        }
        throw new IOException("Elevation tile " + file + " has invalid size "
                + fileLength);
    }

    /**
     * Calculate the elevation for the destination position according the
     * theorem on intersecting lines ("Strahlensatz").
     * 
     * @param dHeight12
     *            the delta height/elevation of two sub tile positions
     * @param dLength12
     *            the length of an sub tile interval (1 / intervals)
     * @param dDiff
     *            the distance of the real point from the sub tile position
     * @return the delta elevation (relative to sub tile position)
     */
    private double calculateElevation(double dHeight12, double dLength12,
            double dDiff) {
        return (dHeight12 * dDiff) / dLength12;
    }

    public Double z(Double latitude, Double longitude) throws Exception {
        int roundLat = Math.abs(latitude.intValue());
        int roundLon = Math.abs(longitude.intValue());
        String latChar = latitude >= 0 ? "N" : "S";
        String lonChar = longitude >= 0 ? "E" : "W";

        int lat = latitude < 0 ? roundLat + 1 : roundLat;
		int lon = longitude < 0 ? roundLon + 1 : roundLon;
		String hgtFilePrefix = String.format("%s%02d%s%03d", latChar, lat, lonChar, lon);

        String hgtFile = hgtFilePrefix + ".hgt";
        String hgtZipFile = hgtFile + ".zip";

        String location = getLocation(latChar, lat, lonChar, lon); 
//        System.out.println("FileName: " + hgtFile);
//        System.out.println("Location: " + location);
        
        if (!new File(hgtZipFile).exists()) {
        	LOGGER.debug("File + " + hgtFile + " for location " + location + " doesn't exist and will be downloaded.");
            int waitTime = 1000;
            for (int i = 0; i < HGT_RETRY_COUNT; i++) {
                try {
                    String zipURL = "http://dds.cr.usgs.gov/srtm/version2_1/SRTM3/" + location + "/"
                            + hgtFile + ".zip";
                    IOUtils.copy(new URL(zipURL).openStream(),
                            new FileOutputStream(hgtZipFile));
                    ZipFile zipFile = new ZipFile(hgtZipFile);
                    zipFile.extractAll(".");
                    break;
                } catch (Throwable t) {
					LOGGER.error(
							"There was an error while downloading ou unzip elevation file and will wait "
									+ waitTime + " miliseconds.", t);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException e) {
                        // Do nothing
                    }
                    waitTime += 1000;
                }
            }
        }
        
		// For more than one process in the same machine trying to use the same
		// file
        while (true) {
            File hgt = new File(hgtFile);
            if (hgt.exists()) {
                if (hgt.length() == 2884802) {
                    break;
                }
            }
            Thread.sleep(1000);
        }

        RandomAccessFile file = new RandomAccessFile(hgtFile, "r");

        if (file == null || longitude == null || latitude == null) {
            return null;
        }

        // cut off the decimal places
        int longitudeAsInt = longitude.intValue();
        int latitudeAsInt = latitude.intValue();

        if (longitude < 0) { // If it's west longitude (negative value)
            longitudeAsInt = (longitudeAsInt - 1) * -1; // Make a positive
                                                        // number (left edge)
            longitude = ((double) longitudeAsInt + longitude)
                    + (double) longitudeAsInt; // Make positive double longitude
                                               // (needed for later calculation)
        }

        if (latitude < 0) { // If it's a south latitude (negative value)
            latitudeAsInt = (latitudeAsInt - 1) * -1; // Make a positive number
                                                      // (bottom edge)
            latitude = ((double) latitudeAsInt + latitude)
                    + (double) latitudeAsInt; // Make positive double latitude
                                              // (needed for later calculation)
        }

        int intervalCount = getIntervalCount(file);
        int longitudeIntervalIndex = (int) ((longitude - (double) longitudeAsInt) * intervalCount);
        int latitudeIntervalIndex = (int) ((latitude - (double) latitudeAsInt) * intervalCount);

        if (longitudeIntervalIndex >= intervalCount) {
            longitudeIntervalIndex = intervalCount - 1;
        }

        if (latitudeIntervalIndex >= intervalCount) {
            latitudeIntervalIndex = intervalCount - 1;
        }

        double dOffLon = longitude - (double) longitudeAsInt; // The longitude
                                                              // value offset
                                                              // within a tile
        double dOffLat = latitude - (double) latitudeAsInt; // The latitude
                                                            // value offset
                                                            // within a tile

        double dLeftTop; // The left top position of a sub tile
        double dLeftBottom; // The left bottom position of a sub tile
        double dRightTop; // The right top position of a sub tile
        double dRightBottom; // The right bootm position of a sub tile
        int pos; // The index of the elevation into the hgt file

        pos = (((intervalCount - latitudeIntervalIndex) - 1) * (intervalCount + 1))
                + longitudeIntervalIndex; // The index for the left top
                                          // elevation
        file.seek(pos * 2); // We have 16-bit values for elevation, so multiply
                            // by 2
        dLeftTop = file.readShort(); // Now read the left top elevation from hgt
                                     // file

        pos = ((intervalCount - latitudeIntervalIndex) * (intervalCount + 1))
                + longitudeIntervalIndex; // The index for the left bottom
                                          // elevation
        file.seek(pos * 2); // We have 16-bit values for elevation, so multiply
                            // by 2
        dLeftBottom = file.readShort(); // Now read the left bottom elevation
                                        // from hgt file

        pos = (((intervalCount - latitudeIntervalIndex) - 1) * (intervalCount + 1))
                + longitudeIntervalIndex + 1; // The index for the right top
                                              // elevation
        file.seek(pos * 2); // We have 16-bit values for elevation, so multiply
                            // by 2
        dRightTop = file.readShort(); // Now read the right top elevation from
                                      // hgt file

        pos = ((intervalCount - latitudeIntervalIndex) * (intervalCount + 1))
                + longitudeIntervalIndex + 1; // The index for the right bottom
                                              // elevation
        file.seek(pos * 2); // We have 16-bit values for elevation, so multiply
                            // by 2
        dRightBottom = file.readShort(); // Now read the right bottom top
                                         // elevation from hgt file

        // if one of the read elevation values is not valid, we cannot
        // interpolate
        if ((dLeftTop < INVALID_VALUE_LIMIT)
                || (dLeftBottom < INVALID_VALUE_LIMIT)
                || (dRightTop < INVALID_VALUE_LIMIT)
                || (dRightBottom < INVALID_VALUE_LIMIT)) {
            file.close();
            return null;
        }

        // the delta between top lat value and requested latitude (offset within
        // a sub tile)
        double dDeltaLon = dOffLon - (double) longitudeIntervalIndex
                * (1.0 / (double) intervalCount);
        // the delta between left lon value and requested longitude (offset
        // within a sub tile)
        double dDeltaLat = dOffLat - (double) latitudeIntervalIndex
                * (1.0 / (double) intervalCount);

        // the interpolated elevation calculated from left top to left bottom
        double dLonHeightLeft = dLeftBottom
                - calculateElevation(dLeftBottom - dLeftTop,
                        1.0 / (double) intervalCount, dDeltaLat);
        // the interpolated elevation calculated from right top to right bottom
        double dLonHeightRight = dRightBottom
                - calculateElevation(dRightBottom - dRightTop,
                        1.0 / (double) intervalCount, dDeltaLat);

        // interpolate between the interpolated left elevation and interpolated
        // right elevation
        double dElevation = dLonHeightLeft
                - calculateElevation(dLonHeightLeft - dLonHeightRight,
                        1.0 / (double) intervalCount, dDeltaLon);
        // round the interpolated elevation
        file.close();
        return dElevation + 0.5;
    }

    /*
     * TODO Implement for other locations
     * South America
     * http://dds.cr.usgs.gov/srtm/version2_1/SRTM3/South_America/
     * s0-16
     * w36-42
     * 
     * Eurasia
     * http://dds.cr.usgs.gov/srtm/version2_1/SRTM3/Eurasia/
     * n0-60
     * s1-13
     * e0-173
     * w1-14 
     */
	private String getLocation(String latChar, int lat, String lonChar, int lon) {
		if ("N".equals(latChar) || "E".equals(lonChar) || lon <= 14) {
			return "Eurasia";
		} 		
		return "South_America";
	}
}
