/*
 * Copyright (C) 2011 Emant Pte Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.smartphonedaq;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.wifi.WifiManager;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;

import android.widget.EditText;

import android.widget.TextView;
import android.widget.Toast;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.lang.Math;

import com.smartphonedaq.scratchsensor.R;

public class ScratchSensor extends Activity implements SensorEventListener, OnClickListener {

	final String tag = "Scratch2Droid";
	SensorManager sm = null;

	TextView xViewA = null;
	TextView yViewA = null;
	TextView zViewA = null;
	TextView xViewO = null;
	TextView yViewO = null;
	TextView zViewO = null;
	TextView mylist = null;
	TextView mystat = null;

	EditText myip = null;

	private Socket socket;
	private DataOutputStream dataOutputStream;
	private WifiManager wifiManager;


	private long mLastDirectionChangeTime;
	private long mLastAccelerationChangeTime;
	private long mLastActionTime;

	private boolean YNeg = false;
	private long YNegTime = 0;
	private boolean YPos = false;
	private long YPosTime = 0;

	private boolean XNeg = false;
	private long XNegTime = 0;
	private boolean XPos = false;
	private long XPosTime = 0;

	private float lastX = 0;
	private float lastY = 0;
	private float lastZ = 0;
	private float lastDir = 0;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		setContentView(R.layout.main);
		xViewA = (TextView) findViewById(R.id.xbox);
		yViewA = (TextView) findViewById(R.id.ybox);
		zViewA = (TextView) findViewById(R.id.zbox);
		xViewO = (TextView) findViewById(R.id.xboxo);

		mylist = (TextView) findViewById(R.id.textView1);
		mystat = (TextView) findViewById(R.id.status);

		mystat.setText("Not Connected");
		String thisver="";
		try {
			PackageInfo manager=getPackageManager().getPackageInfo(getPackageName(), 0);
			thisver = manager.versionName;
		} catch (NameNotFoundException e) {
			//Handle exception
		}

		mylist.setText(
				Html.fromHtml(
						"version " + thisver + "<BR />" +
						"<a href=\"http://smartphonedaq.com/scratch-sensor.page\">smartphonedaq.com/scratch-sensor.page</a>"));
		mylist.setMovementMethod(LinkMovementMethod.getInstance());

		myip = (EditText) findViewById(R.id.editText1);
		
		Button button = (Button) findViewById(R.id.button1);
		button.setOnClickListener(this);

		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		String ipadd = settings.getString("ipAdd", "");
		myip.setText(ipadd);


		wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		if(wifiManager.isWifiEnabled()){
			// wifiManager.setWifiEnabled(false);
		}else{
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Turn on wifi?")
			.setCancelable(false)
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					wifiManager.setWifiEnabled(true);
				}
			})
			.setNegativeButton("No", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
				}
			});
			AlertDialog alert = builder.create();
			alert.show();

		}
		long now = System.currentTimeMillis();
		mLastAccelerationChangeTime = now;
		setmLastDirectionChangeTime(now);
	}



	@Override
	public void onSensorChanged(SensorEvent event) {
		long now = System.currentTimeMillis();
		if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
			float[] values = event.values;
			String dx = String.format("%.0f", values[0]);
			xViewO.setText("X: " + dx);

			if (now-mLastActionTime<300) 
			{
				dx = String.format("%.0f", (values[0]+lastDir)/2);  
			}
			sendtoscratch("sensor-update dir " + dx);
			setmLastDirectionChangeTime(now);
			lastDir = values[0];
		}
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			float[] values = event.values;
			String ax = String.format("%.1f", values[0]);
			String ay = String.format("%.1f", values[1]);
			String az = String.format("%.1f", values[2]);
			// Movement
			xViewA.setText("X: " + ax);
			yViewA.setText("Y: " + ay);
			zViewA.setText("Z: " + az);

			sendtoscratch("sensor-update accx " + ax + " accy " + ay + " accz " + az);

			float delY =  (values[1] - lastY) / (now - mLastAccelerationChangeTime);
			float delX =  (values[0] - lastX) / (now - mLastAccelerationChangeTime);


			if (delY < -0.03){
				if (now-YNegTime>500) {
					YNeg = true;
					YNegTime = now;
				}
			}

			if (delY > 0.03){
				if (now-YPosTime>500) {
					YPos = true;
					YPosTime = now;
				}
			}

			if (YPos && YNeg) {
				if (Math.abs(YPosTime-YNegTime)<500){
					sendtoscratch("broadcast \"jump\"");
					YPos = false;
					YNeg = false;
					XPos = false;
					XNeg = false;
					mLastActionTime = now;
				}
			}


			if (delX < -0.02){
				XNeg = true;
				XNegTime = now;
			}

			if (delX > 0.02){
				XPos = true;
				XPosTime = now;
			}

			if (XPos && XNeg) {
				if (Math.abs(XPosTime-XNegTime)<500){
					sendtoscratch("broadcast \"walk\"");
					YPos = false;
					YNeg = false;
					XPos = false;
					XNeg = false;
					mLastActionTime = now;
				}
			}	            
			mLastAccelerationChangeTime = now;
			lastX = values[0];
			lastY = values[1];
			setLastZ(values[2]);



		}            

	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		Log.d(tag,"onAccuracyChanged: " + sensor + ", accuracy: " + accuracy);
	}

	@Override
	protected void onResume() {
		super.onResume();
		sm.registerListener(this,
				sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);
		sm.registerListener(this,
				sm.getDefaultSensor(Sensor.TYPE_ORIENTATION),
				SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	protected void onStop() {
		sm.unregisterListener(this);
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();

		editor.putString("ipAdd",myip.getText().toString());
		editor.commit();


		if (dataOutputStream != null){
			try {
				dataOutputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (socket != null){
			try {
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		mystat.setText("Not Connected");  
		super.onStop();
	}    
	/**
	 * Sends dataOut to Scratch using the format described here
	 * http://scratch.mit.edu/forums/viewtopic.php?id=9458
	 * @param dataOut
	 */
	public void sendtoscratch(String dataOut){
		if (mystat.getText()=="Connected"){

			try
			{
				byte [] sizeBytes = { 0, 0, 0, 0 };
				int len = dataOut.length();

				sizeBytes[0] =(byte)( len >> 24 );
				sizeBytes[1] =(byte)( (len << 8) >> 24 );
				sizeBytes[2] =(byte)( (len << 16) >> 24 );
				sizeBytes[3] =(byte)( (len << 24) >> 24 );

				for (int i=0; i<4; i++) {                  
					dataOutputStream.write(sizeBytes[i]);
				}
				dataOutputStream.write(dataOut.getBytes());
			}
			catch(IOException e) {
				Log.d(tag,"Couldn't send "+ dataOut
						+ " to Scratch.");
				Toast.makeText(ScratchSensor.this, "IO Error", Toast.LENGTH_SHORT).show();
				mystat.setText("Not Connected");

			}
		}
	}


	@Override
	public void onClick(View v) {

		// TODO Auto-generated method stub
		if (mystat.getText()=="Not Connected") {
			try {
				//"192.168.1.1"
				//socket = new Socket(myip.getText().toString(), 42001);
				socket = new Socket();
				socket.setSoTimeout(5000);
				socket.setKeepAlive(true);
				socket.setSendBufferSize(10000);
				SocketAddress sockaddr = new InetSocketAddress(myip.getText().toString(), 42001);
				socket.connect(sockaddr, 3000);
				dataOutputStream = new DataOutputStream(socket.getOutputStream());
				Toast.makeText(ScratchSensor.this, "Connected", Toast.LENGTH_SHORT).show();
				mystat.setText("Connected");
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				Log.d(tag,"unknown socket error!!");
				Toast.makeText(ScratchSensor.this, "Unknown Connection Error", Toast.LENGTH_SHORT).show();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Log.d(tag,"Connect IO error!!");
				Toast.makeText(ScratchSensor.this, "Connection Error", Toast.LENGTH_SHORT).show();
			}

		} else {

			if (dataOutputStream != null){
				try {
					dataOutputStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}        	
			if (socket != null){
				try {
					socket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			Toast.makeText(ScratchSensor.this, "Disconnect", Toast.LENGTH_SHORT).show();
			mystat.setText("Not Connected");
		}		
	}

	public float getLastZ() {
		return lastZ;
	}

	public void setLastZ(float lastZ) {
		this.lastZ = lastZ;
	}

	public long getmLastDirectionChangeTime() {
		return mLastDirectionChangeTime;
	}

	public void setmLastDirectionChangeTime(long mLastDirectionChangeTime) {
		this.mLastDirectionChangeTime = mLastDirectionChangeTime;
	}    

}