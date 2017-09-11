#!/bin/bash

# ${IMAGE_NAME}
IMAGE_NAME=$1
# ${SEBAL_MOUNT_POINT}/$IMAGES_DIR_NAME/
IMAGES_DIR_PATH=$2
# ${SEBAL_MOUNT_POINT}/$RESULTS_DIR_NAME/
RESULTS_DIR_PATH=$3
# ${SEBAL_MOUNT_POINT}/$RESULTS_DIR_NAME/${IMAGE_NAME}
OUTPUT_IMAGE_DIR=$4
# ${SEBAL_MOUNT_POINT}/$IMAGES_DIR_NAME/${IMAGE_NAME}/${IMAGE_NAME}"_MTL.txt"
IMAGE_MTL_PATH=$5
# ${SEBAL_MOUNT_POINT}/$IMAGES_DIR_NAME/${IMAGE_NAME}/${IMAGE_NAME}"_MTLFmask"
IMAGE_MTL_FMASK_PATH=$6
# ${SEBAL_MOUNT_POINT}/$RESULTS_DIR_NAME/${IMAGE_NAME}/${IMAGE_NAME}"_station.csv"
IMAGE_STATION_FILE_PATH=$7

# Global variables
SANDBOX=$(pwd)
SEBAL_DIR_PATH=$SANDBOX/SEBAL
CONF_FILE=sebal.conf
LIBRARY_PATH=/usr/local/lib
BOUNDING_BOX_PATH=example/boundingbox_vertices
TMP_DIR_PATH=/mnt
IMAGE_MTL_FILE_PATH=$SANDBOX/$IMAGE_NAME/$IMAGE_NAME"_MTL.txt"

R_EXEC_DIR=$SEBAL_DIR_PATH/workspace/R
R_ALGORITHM_VERSION=Algoritmo-completo-v12042017.R
R_RASTER_TMP_DIR=/mnt/rasterTmp
MAX_TRIES=2

OUTPUT_IMAGE_DIR=$RESULTS_DIR_PATH/$IMAGE_NAME
SCRIPTS_DIR=scripts
SWIFT_CLI_DIR=swift-client
LOG4J_PATH=$SEBAL_DIR_PATH/log4j.properties

FIXED_IMAGE_NAME="LT52160651994139CUB00"

# This function clean environment by deleting raster temp dir if exists
function cleanRasterEnv {
  if [ -d $R_RASTER_TMP_DIR ]
  then
    sudo rm -r $R_RASTER_TMP_DIR
  fi

}

# This function untare image and creates an output dir into mounted dir
function untarImageAndPrepareDirs {
  cp -r $IMAGES_DIR_PATH/$IMAGE_NAME $SANDBOX

  echo "Image file name is $IMAGE_NAME"

  # untar image
  echo "Untaring image $IMAGE_NAME"
  cd $SANDBOX/$IMAGE_NAME
  sudo tar -xvzf $IMAGE_NAME".tar.gz"

  echo "Creating image output directory"
  sudo mkdir -p $OUTPUT_IMAGE_DIR

  cd $SANDBOX
}

# This function calls a pre process java code to prepare a station file of a given image
function preProcessImage {
  cd $SEBAL_DIR_PATH

  sudo java -Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=4000,suspend=n -Dlog4j.configuration=file:$LOG4J_PATH -Djava.library.path=$LIBRARY_PATH -cp target/SEBAL-0.0.1-SNAPSHOT.jar:target/lib/* org.fogbowcloud.sebal.PreProcessMain nfs/fixed-image/ /nfs/fixed-image/$FIXED_IMAGE_NAME/$FIXED_IMAGE_NAME"_MTL.txt" $RESULTS_DIR_PATH/ 0 0 9000 9000 1 1 $SEBAL_DIR_PATH/$BOUNDING_BOX_PATH $SEBAL_DIR_PATH/$CONF_FILE /nfs/fixed-image/$FIXED_IMAGE_NAME/$FIXED_IMAGE_NAME"_MTLFmask"
  sudo chmod 777 $IMAGE_STATION_FILE_PATH
  echo -e "\n" >> $IMAGE_STATION_FILE_PATH
  cd ..
}

# This function prepare a dados.csv file
function creatingDadosCSV {
  echo "Creating dados.csv for image $IMAGE_NAME"

  cd $R_EXEC_DIR

  MTL_FILE_PATH=/nfs/fixed-image/$FIXED_IMAGE_NAME/$FIXED_IMAGE_NAME"_MTL.txt"
  FMASK_FILE_PATH=/nfs/fixed-image/$FIXED_IMAGE_NAME/$FIXED_IMAGE_NAME"_MTLFmask"
  STATION_FILE_PATH=/nfs/fixed-image/$FIXED_IMAGE_NAME/$FIXED_IMAGE_NAME"_station.csv"

  echo "File images;MTL;File Station Weather;File Fmask;Path Output;Current image" > dados.csv
  echo "/nfs/fixed-image/$FIXED_IMAGE_NAME;$MTL_FILE_PATH;$STATION_FILE_PATH;$FMASK_FILE_PATH;$OUTPUT_IMAGE_DIR;$IMAGE_NAME" >> dados.csv
}

# This function creates a raster tmp dir if not exists and start scripts to collect CPU and memory usage
function prepareEnvAndCollectUsage {
  # check if raster temporary dir exists
  if [ ! -d $R_RASTER_TMP_DIR ]
  then
    sudo mkdir $R_RASTER_TMP_DIR
  else
    count=`ls -1 $R_RASTER_TMP_DIR/r_tmp* 2>/dev/null | wc -l`
    if [ $count != 0 ]
    then
      sudo rm -r $R_RASTER_TMP_DIR/r_tmp*
    fi
  fi

  echo "Starting CPU and Memory collect..."
  sudo bash $SEBAL_DIR_PATH/$SCRIPTS_DIR/collect-cpu-usage.sh | sudo tee $OUTPUT_IMAGE_DIR/$IMAGE_NAME"_cpu_usage.txt" > /dev/null &
  sudo bash $SEBAL_DIR_PATH/$SCRIPTS_DIR/collect-memory-usage.sh | sudo tee $OUTPUT_IMAGE_DIR/$IMAGE_NAME"_mem_usage.txt" > /dev/null &
}

# This function executes R script
function executeRScript {
  for i in `seq $MAX_TRIES`
  do
    sudo bash $SEBAL_DIR_PATH/$SCRIPTS_DIR/executeRScript.sh $R_EXEC_DIR/$R_ALGORITHM_VERSION $R_EXEC_DIR $TMP_DIR_PATH
    PROCESS_OUTPUT=$?

    echo "executeRScript_process_output=$PROCESS_OUTPUT"
    if [ $PROCESS_OUTPUT -eq 0 ]
    then
      echo "NUMBER OF TRIES $i"
      break
    elif [ $PROCESS_OUTPUT -eq 124 ] && [ $i -ge $MAX_TRIES ]
    then
      exit 124
    else
      if [ $i -ge $MAX_TRIES ]
      then
	echo "NUMBER OF TRIES $i"
        exit 1
      fi
    fi
  done
}

# This function moves dados.csv to image results dir
function mvDadosCSV {
  STATION_FILE_PATH=/nfs/fixed-image/$FIXED_IMAGE_NAME/$FIXED_IMAGE_NAME"_station.csv"

  sudo mv dados.csv $OUTPUT_IMAGE_DIR
  sudo cp $STATION_FILE_PATH $IMAGE_STATION_FILE_PATH
  cd ../..
}

function killCollectScripts {
  echo "Killing collect CPU and Memory scripts"
  ps -ef | grep collect-cpu-usage.sh | grep -v grep | awk '{print $2}' | xargs sudo kill
  ps -ef | grep collect-memory-usage.sh | grep -v grep | awk '{print $2}' | xargs sudo kill
}

function checkProcessOutput {
  PROCESS_OUTPUT=$?

  if [ $PROCESS_OUTPUT -ne 0 ]
  then
    finally
  fi
}

# This function ends the script
function finally {
  exit $PROCESS_OUTPUT
}

#untarImageAndPrepareDirs
echo "Creating image output directory"
sudo mkdir -p $OUTPUT_IMAGE_DIR
#checkProcessOutput
#preProcessImage
#checkProcessOutput
creatingDadosCSV
checkProcessOutput
prepareEnvAndCollectUsage
checkProcessOutput
executeRScript
checkProcessOutput
mvDadosCSV
killCollectScripts
finally
