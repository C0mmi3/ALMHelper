package com.main.helpers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by ikfl27 on 26.04.2016.
 */
public abstract class HelperBase {
    public Properties config = new Properties();
    public final Logger log = LogManager.getLogger(this.getClass().getName());
    public String cookies = "";
    private String ALM_CREDENTIALS;

    public HelperBase() {
        setConfig();
    }

    /*
Config init
Reading properties from config file
Called from HelperBase constructor
I should move config from here to make it global
*/
    public void setConfig() {

        try {
            config.load(this.getClass().getResourceAsStream("/Settings/config.properties"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            log.error(e.toString());
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.toString());
        }
    }

    public void getVersion() {
        log.info("Current version: " + config.getProperty("alm.helper.version"));
        log.info("Build date: " + config.getProperty("alm.helper.build.date"));
    }
 /*
    Authentication method
    Currently authData's loading from config
    authData template: login:password
    Encoding to BASE64 and sending to a server
    Saving received cookies and using in further work
     */

    public void Authenticate() {
        log.info("Starting authorization!");
        Map<String, List<String>> map;
        String authData = config.getProperty("alm.server.api.user") + ":" + config.getProperty("alm.server.api.password");
        ALM_CREDENTIALS = "Basic " + Base64.getEncoder().encodeToString(authData.getBytes());
        try {

            URL url = new URL(config.getProperty("alm.server.api.url") + "authentication/sign-in");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", ALM_CREDENTIALS);
            conn.connect();
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                throw new RuntimeException("Failed: HTTP error code: Unable to sign in. Please check your credentials!");
            } else {
                map = conn.getHeaderFields();
                conn.disconnect();
                boolean k = false;
                /*
                There are a lot of empty Path= arguments in cookies currently.
                 I should find a way to erase them
                                                 */
                for (int i = 0; i < map.size(); i++) {
                    if (map.containsKey("Set-Cookie")) {
                        if (!k) {
                            this.cookies = cookies + map.get("Set-Cookie");
                            k = true;
                        } else this.cookies = cookies + ";" + map.get("Set-Cookie");
                    }
                }
                this.cookies = cookies.replace(",", ";");

            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            log.error(e.toString());
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.toString());
        }
        log.info("Successfuly authorized!");
    }

    /*
      Sign out method. Otherwise session will be alive next few hours
       */
    public void signOut() {
        try {
            URL url = new URL(config.getProperty("alm.server.api.url") + "authentication/sign-out");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", ALM_CREDENTIALS);
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                throw new RuntimeException("Failed: Unable to log out from ALM! Please check your credentials");
            } else {
                this.cookies = null;
                log.info("Signed out. Good bye!");
            }
        } catch (MalformedURLException e) {
            log.error(e.toString());
        } catch (IOException e) {
            log.error(e.toString());
        }
    }

     /*
    Method for receiving bug information
    Currently returning Response body and translating to String
    Then parsing to JSON using GSON
    Important fields:
    id
    owner
    status
    dev-comments
 */

    public JsonObject getDefect(String project, int id) {
        log.info("Fetching defect #" + id + " from ALM");
        JsonObject obj = new JsonObject();
        try {
            URL url = new URL(config.getProperty("alm.server.api.url") + "domains/" + config.getProperty("alm.server.api.domain")
                    + "/" + "projects/" + project + "/defects/" + id);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Cookie", cookies);
            conn.connect();
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                throw new RuntimeException("GET failed: HTTP error code: " + conn.getResponseCode());
            }
            JsonParser parser = new JsonParser();
            obj = parser.parse(getResponseMessage(conn)).getAsJsonObject();
            conn.disconnect();
        } catch (MalformedURLException e) {
            log.error(e.toString());
        } catch (IOException e) {
            log.error(e.toString());
        }
        return obj;
    }

    /*
Helper for translating Response from Stream
 */
    public byte getByteArray(InputStream is)[]throws IOException {
        int nRead;
        byte[] data = new byte[16384];
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            bout.write(data, 0, nRead);
        }
        try {
            bout.flush();
        } catch (IOException e) {
            log.error(e.toString());
        }
        return bout.toByteArray();
    }

    /*
    Update method
    Sending JSON with required for update fields
  */
    public void updateDefect(String project, JsonObject obj) throws IOException {
        log.debug(obj);
        log.info("Updating defect #" + obj.get("id").toString());
        URL url = new URL(config.getProperty("alm.server.api.url") + "domains/" + config.getProperty("alm.server.api.domain")
                + "/" + "projects/" + project + "/defects/" + obj.get("id").getAsInt());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Cookie", cookies);
        conn.setDoOutput(true);
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.YYYY");
        byte[] output = obj.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream os = conn.getOutputStream();
        os.write(output);
        conn.connect();
        os.close();
        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            log.error(getResponseMessage(conn));
            throw new RuntimeException("Update failed: HTTP error code: " + conn.getResponseCode());
        }
        log.info("Success! Defect " + obj.get("id") + " has been updated");
    }

    /*
    Response body translating method
     */
    public String getResponseMessage(HttpURLConnection conn) {
        byte bout[] = new byte[0];
        try {
            BufferedInputStream is = new BufferedInputStream(conn.getInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            bout = getByteArray(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String message = new String(bout, StandardCharsets.UTF_8);
        return message;
    }

    /*
    New bug creating method
     */
    public void createDefect(String project, JsonObject obj) {

    }

    /*
    Error processor
     */
    public void processError(HttpURLConnection conn) throws IOException {
        int errorCode;
        //Receiving error body for processing
        if ((errorCode = conn.getResponseCode()) == 503) {

        }
        if ((errorCode = conn.getResponseCode()) == 400) {

        }
        if ((errorCode = conn.getResponseCode()) == 403) {

        }
        if ((errorCode = conn.getResponseCode()) == 404) {

        }
    }

    public abstract void process();

    public abstract JsonObject prepareDefect(JsonObject obj) throws IOException;
}
