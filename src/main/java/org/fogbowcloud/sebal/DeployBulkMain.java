package org.fogbowcloud.sebal;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.fogbowcloud.sebal.wrapper.TaskType;
import org.fogbowcloud.sebal.wrapper.Wrapper;

public class DeployBulkMain {

	public static void main(String[] args) throws Exception {
		String mtlFilePath = args[0];
		String fileName = new File(mtlFilePath).getName();
		String mtlName = fileName.substring(0, fileName.indexOf("_"));

		String outputDir = args[1];

		int leftX = Integer.parseInt(args[2]);
		int upperY = Integer.parseInt(args[3]);
		int rightX = Integer.parseInt(args[4]);
		int lowerY = Integer.parseInt(args[5]);

		int numberOfPartitions = Integer.parseInt(args[6]);
		int partitionIndex = Integer.parseInt(args[7]);

		String boundingBoxPath = args[8];
				
		String confFile = args[9];
		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(confFile);
		properties.load(input);

		String fmaskFilePath = null;
		if (args.length > 9) {
			fmaskFilePath = args[10];
		}
		
		XPartitionInterval imagePartition = BulkHelper.getSelectedPartition(leftX, rightX,
				numberOfPartitions, partitionIndex);

		Wrapper wrapper = new Wrapper(mtlFilePath, outputDir, imagePartition.getIBegin(),
				imagePartition.getIFinal(), upperY, lowerY, mtlName, boundingBoxPath, properties, fmaskFilePath);
		wrapper.doTask(TaskType.F1);
	}
}
