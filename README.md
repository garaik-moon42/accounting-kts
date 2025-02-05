# accounting-kts
This toolset is configured to be run with IntelliJ IDEA. After cloning this repo simply open the directory as an IDEA Kotlin project.

To run the application please copy the `resources/accounting.properties.sample` file as `resources/accounting.properties` 
and provide the necessary details in it.  

To acquire an Airtable Access Token visit: https://airtable.com/create/tokens

To access Google Drive API you need to set up a client ID and obtain a client secret here:

https://console.cloud.google.com/auth/clients

Here it is described how to do so:
https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid

Once you have the client secret file (typically called `credentials.json`) please put it in the `resources` directory and configure its name in the `accounting.properties` file.
