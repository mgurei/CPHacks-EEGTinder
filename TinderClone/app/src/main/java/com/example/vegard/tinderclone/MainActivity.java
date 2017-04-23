package com.example.vegard.tinderclone;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.goebl.david.Response;
import com.goebl.david.Webb;
import com.goebl.david.WebbException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    TextView nameText;
    TextView bioText;
    ImageView image;
    JSONArray photos;
    int imageNr = 0;

    String Id;
    String name;

    Webb webb = Webb.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);


        webb.setBaseUri("https://api.gotinder.com");
        webb.setDefaultHeader("User-Agent", "Tinder/4.7.1 (iPhone; iOS 9.2; Scale/2.00)");
        webb.setDefaultHeader("Content-Type","application/json");
        webb.setDefaultHeader("X-Auth-Token","e4de6e49-7124-4212-b386-b89fda4d7865");

        nameText = (TextView) findViewById(R.id.textName);
        bioText = (TextView) findViewById(R.id.bioText);
        Button acceptButton = (Button) findViewById(R.id.buttonAccept);
        Button rejectButton = (Button) findViewById(R.id.buttonReject);
        image = (ImageView) findViewById(R.id.imageView);

        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                nextPicture(imageNr);
                imageNr++;
                if (imageNr > (photos.length()-1)){
                    imageNr = 0;
                }
            }
        });

        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    accept();
                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }
        });

        rejectButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                reject();
            }
        });

        //tinderAuthenticate();

        try {
            getNewPerson();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }



    private void accept() throws JSONException {
        Response<JSONObject> response = webb
                .get("/like/" + Id )
                .ensureSuccess()
                .asJsonObject();

        JSONObject apiResult = response.getBody();
        String matchResult = apiResult.getString("match");
        if(matchResult.equals("true")){
            Toast.makeText(this, "Match with" + name, Toast.LENGTH_LONG).show();
            Log.w("MATCH", name);

        }else{
            Toast.makeText(this, "Liked", Toast.LENGTH_SHORT).show();
        }

        try {
            getNewPerson();
        } catch (JSONException e){
            e.printStackTrace();
        }


    }

    private void reject(){
        Response<JSONObject> response = webb
                .get("/pass/" + Id )
                .ensureSuccess()
                .asJsonObject();

        Toast.makeText(this, "Passed", Toast.LENGTH_SHORT).show();
        try {
            getNewPerson();
        } catch (JSONException e){
            e.printStackTrace();
        }

    }




    private void nextPicture(int i){
        try{
            JSONObject firstPhoto = photos.getJSONObject(i);
            String photoUrl = firstPhoto.getString("url");

            Log.w("url", photoUrl);

            image.setImageBitmap(getBitmapFromURL(photoUrl));
        }
        catch (JSONException e){
            e.printStackTrace();
        }



    }

    private void getNewPerson() throws JSONException {
        Response<JSONObject> response = webb
                .get("/user/recs")
                .ensureSuccess()
                .asJsonObject();

        JSONObject apiResult = response.getBody();

        JSONArray results = apiResult.getJSONArray("results");
        JSONObject firstRes = results.getJSONObject(0);

        String test = firstRes.toString();

        name = firstRes.getString("name");
        String bio = firstRes.getString("bio");
        Id = firstRes.getString("_id");


        photos = firstRes.getJSONArray("photos");
        JSONObject firstPhoto = photos.getJSONObject(0);
        String photoUrl = firstPhoto.getString("url");

        nameText.setText(name);
        bioText.setText(bio);
        image.setImageBitmap(getBitmapFromURL(photoUrl));





    }



    private void tinderAuthenticate(){

        Response<JSONObject>response = webb
                .post("/auth")
                .param("facebook_token", R.string.fbToken)
                .param("facebook_id", R.string.fbId)
                .asJsonObject();


        

        JSONObject apiResult = response.getBody();

        String test = apiResult.toString();

        Log.w("json:",test);

        try {
            webb.setDefaultHeader("X-Auth-Token",apiResult.getString("token"));

        } catch (JSONException e) {
            e.printStackTrace();
        }

        Toast.makeText(this, "Connected to Tinder", Toast.LENGTH_SHORT).show();

    }

    public static Bitmap getBitmapFromURL(String src) {
        try {
            Log.e("src",src);
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            Log.e("Bitmap","returned");
            return myBitmap;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Exception",e.getMessage());
            return null;
        }
    }

}

