package com.hoogle.fileditto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.os.Handler;
import android.widget.Toast;

public class MainActivity extends Activity {
    private Handler h = new Handler();
    private Button snackActionClick;
    Button button;
    TextView textview;
    Context context;
    LocationManager locationManager ;
    boolean GpsStatus ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = getApplicationContext();



        // TODO Auto-generated method stub

        CheckGpsStatus();

        if (GpsStatus == true) {
            Intent i = new Intent(MainActivity.this ,MaiwActivity.class );
            startActivity(i);
        } else {

        Snackbar.make(findViewById(android.R.id.content), "turn on your location so hotspot can start", Snackbar.LENGTH_LONG)
                .setAction("open", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent gps = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(gps);

                        }
                    })
                    .setActionTextColor(getResources().getColor(android.R.color.holo_red_light ))
                    .show();



        }


    }
    public void loct(View view) {
        Intent intent = new Intent(this, MainActivity.class); //restart main activity
        startActivity(intent);
    }

    public void CheckGpsStatus(){

        locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);

        GpsStatus = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }



}