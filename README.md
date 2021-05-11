# FIREEngine

**Repo Owner:** Vlad Shioshvili [@vshioshvili](https://github.com/vshioshvili)  

FIRE Engine component of the patient level monitoring (PLM)

## Overview

This repository contains the java code to read in FHIR bundles or Questionnaire Response from a queue, aggregate the patient data based on the mappings from OCL retrieved, generate the output in ADX format, and optionally import the data into a HMIS instance. It can be used in two ways:
- As a stand-alone application, to manually start the java app.
- As a component in the End-to-End application 
Configure with the queue and trigger mediators to read the bundles from the queue and the mediator triggers the java app to run.

## Use

<h3>Installation</h3>

1)	Get the jar and properties files from the repo

```
- PLMTransformationApp.jar 
- patientLevelMonitor.properties
```

2) Create a folder for the queue to store the bundle or questionnaire response files. Make sure the indicator profile defined in the bundle or questionannire response files matches one of the profiles defined in the OCL data elements.
3) Set the properties in patientLevelMonitor.properties 

- oclDomain (required) The URL of the OCL to get the mappings, such as https://xxxxx.openconceptlab.org/orgs/PEPFAR/sources/PLM/v0.1/export/
- oclVersion (optional, leave blank for now)
-	logPath (requried) The path to the folder for the log files, e.g. /User/xxx/.../logs
- adxPath (required) The path to the folder for generated ADX output files, such as /Users/xyz/adx_output 
- parcialProcessingAllowed (required, true|false) The flag to indicate if the process continues when an error occurs. If set to true, the process logs the error and continues. Otherwise the program stops processing when an error occurs.
- archiveFilesInQueue (required, true|false) The flag to indicate if the files in the queue are to be moved to the archive folder after processing. 
- archivePath (optional, required if archiveFilesInQueue is true, leave blank otherwise) The path to the folder where the files in queue are moved to after processing
- importIntoHMIS (required, true|false) The flag to indicate if import the aggregated data into HMIS instance. If false, it will only generate ADX file and not import data into HMIS.
- inputBundleType (required, resource|questionnaireResponse) The string value to indicate the type of the input bundle file to be processed.  The type can be either `resource` or `questionnaireResponse`.
- dhisdomain (optional, required if importIntoHMIS is true, leave blank otherwise) The domain of the HMIS instance, such as https://test.datim.org
- username (optional, required if importIntoHMIS is true, leave blank otherwise) The user name to login to the HMIS instance
- password (optional, required if importIntoHMIS is true , leave blank otherwise) The password to login into the HMIS instance
- schematronValidatorLocation (optional, if provided it validates/filters the patient data against the schematron validation rules) The location for the schematron validator, likely through a mediator such as https://test.ohie.datim.org:xxxx/schematron_validator/  


<h3>Run as a stand-alone application</h3>

From terminal, run the following command to start the java app where the `p` argument points to the properties file, and the  `q` argument points to the queue directory.


```
java -jar /path_to_jar/PLMTransformationApp.jar -p=/path_to_properties/patientLevelMonitor.properties -q=/path_to_queue/queue

```

<h3>Run as a component in End-to-End application </h3>

This java app can be used together with the MAPPER and BUNDLE MAKER through the mediators to become an end-to-end application.

- Install file queue and trigger mediators. See details:
  - https://github.com/pepfar-datim/PLM/tree/master/src/openhim-mediator-plmfilequeue
  - https://github.com/pepfar-datim/PLM/tree/master/src/openhim-mediator-triggerMediator

Once the mediators are installed,
Go to `plmfilequeue` mediator in OpenHIM console, click on Configuration to verify the File Queue location is pointing to the correct queue folder. The bundle files will be generated from POS.

Go to the `triggerMediator` in OpenHIM console, click on Configuration, verify the locations of the jar and properties files, queue as defined in the plmfilequerue mediator.

The java app will be triggered from the triggerMediator by POS through the bundle maker.

<h3>Logging</h3>

Log files will be written in the logPath directory defined in the properties file. The log is generated in yyyy-mm-dd.log format, such as 2019-10-23.log.

<h3>ADX output</h3>
ADX files generated can be found in the adxPath directory defined in the properties file.

<h3>Diagram</h3>
https://github.com/pepfar-datim/FIREEngine/blob/main/diagrams/prats.pdf

