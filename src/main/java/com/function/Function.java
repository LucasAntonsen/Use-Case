package com.function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

import org.json.*;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.commons.csv.*;

import com.azure.storage.file.share.*;

public class Function {
    
    @FunctionName("SalesforceRestApiQuery")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        // Get Salesforce access token from request body
        String accessToken = request.getBody().orElse("Failure");
        
        if(accessToken != "Failure"){

            // Querying Salesforce for Contact data
            try{
                // Providing the website URL
                URL url = new URL("https://hotmailca4-dev-ed.develop.my.salesforce.com/services/data/v55.0/query?q=SELECT%20Name,%20Email%20FROM%20CONTACT%20LIMIT%20200");

                // Creating an HTTP connection
                HttpURLConnection MyConn = (HttpURLConnection) url.openConnection();

                // Set the request method to "GET"
                MyConn.setRequestMethod("GET");

                // Create authorization string for request
                String authString = "Bearer " + accessToken;

                // Set the request property to "Authorization" and pass the authorization string
                MyConn.setRequestProperty("Authorization", authString);

                // Collect the response code
                int responseCode = MyConn.getResponseCode();
                context.getLogger().info("GET Response Code :: " + responseCode);

                // Interpreting the output of the Salesforce query
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Create a reader with the input stream reader
                    BufferedReader in = new BufferedReader(new InputStreamReader(MyConn.getInputStream()));
                    String inputLine;

                    // Create a string buffer
                    StringBuffer response = new StringBuffer();

                    // Write each of the input line
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    // Create contacts JSON object from the response
                    JSONObject contactJSON = new JSONObject(response.toString());

                    // Access records from contacts object and save to JSONArray
                    JSONArray recordsArray = contactJSON.getJSONArray("records");
                    
                    // Create a CSV printer
                    CSVPrinter printer = new CSVPrinter(new FileWriter("contacts.csv"), CSVFormat.DEFAULT);

                    // Create header row
                    printer.printRecord("Name", "Email");

                    // Create data rows from the records array
                    for (int i = 0; i < recordsArray.length(); i++) {

                        // Get record
                        JSONObject record = recordsArray.getJSONObject(i);

                        // Get record name
                        String name = record.getString("Name");

                        // Set email to empty string in case of no email value
                        String email = "";

                        // Set email
                        if(!record.isNull("Email")){
                            email = record.getString("Email");
                        }

                        // Add data row
                        printer.printRecord(name, email);
                    }

                    // Close the printer after the file is complete
                    printer.flush();
                    printer.close();

                    // Get connection string for File Share storage
                    String connectStr = System.getenv("AzureWebJobsStorage");

                    // Create client to access File Share storage
                    try{
                        ShareDirectoryClient dirClient = new ShareFileClientBuilder()
                            .connectionString(connectStr).shareName("fileshare")
                            .resourcePath("testdirectory")
                            .buildDirectoryClient();

                        // Construct a ShareFileClient that interacts with the contacts.csv
                        ShareFileClient fileClient = dirClient.getFileClient("contacts.csv");

                        // Call create if file does not exist in directory
                        fileClient.create(1024);

                        // Upload file to target directory testdirectory
                        fileClient.uploadFromFile("contacts.csv");

                    }catch (Exception e){
                        // Failure response
                        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Exception when attempting to upload contacts.csv to File Share").build();
                    }

                    // Successful response
                    return request.createResponseBuilder(HttpStatus.OK).body("Received Salesforce Contact data and wrote to contacts.csv. Saved file to File Share").build();
                } else {
                    // Failure response
                    return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Failed to receive Salesforce Contact data").build();
                }

            }catch (IOException e) {
                // Failure response
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Exception when attempting to access Salesforce").build();
            }

        }else{
            // Failure response
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Failed to receive Salesforce access token").build();
        }
    }
}
