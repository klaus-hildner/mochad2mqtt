package com.hausgart.bridge;

import java.io.InputStream;
import java.io.IOException;
import java.io.Console;
import java.io.FileInputStream;
import java.net.Socket;
import java.util.Properties;
import java.util.Scanner;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

public class Mochad2Mqtt {
	
	static String MOCHAD_HOST, MQTT_PROTOCOL, MQTT_HOST, MQTT_TOPIC, MY_NAME,CMD_QUIT;
	static int MOCHAD_PORT, MQTT_PORT, BUFFER_SIZE;
	static MqttClient client;
	static MqttMessage message;
	static boolean doLoop = true;
	
	private static synchronized String decorateStatus(String s) {
		String s2 = new java.util.Date() + ": " + MY_NAME + ": " + s;
		return(s2);
	}
	private static synchronized void pubStatus(String s) throws MqttException {
		String s2 = decorateStatus(s);
		message.setPayload(s2.getBytes());
		client.publish(MQTT_TOPIC, message);
		System.out.println(s2);
	}
	private static synchronized void readProperties() {
		Properties prop = new Properties();
		InputStream input = null;
		try {
			input = new FileInputStream("/home/pi/workspace/mochad2mqtt/src/config.properties");
			prop.load(input);
			MOCHAD_HOST = prop.getProperty("MOCHAD_HOST");
			MOCHAD_PORT = Integer.parseInt(prop.getProperty("MOCHAD_PORT"));
			MQTT_PROTOCOL = prop.getProperty("MQTT_PROTOCOL");
			MQTT_HOST = prop.getProperty("MQTT_HOST");
			MQTT_PORT = Integer.parseInt(prop.getProperty("MQTT_PORT"));
			MQTT_TOPIC = prop.getProperty("MQTT_TOPIC");
			BUFFER_SIZE = Integer.parseInt(prop.getProperty("BUFFER_SIZE"));
			MY_NAME = prop.getProperty("MY_NAME");
			CMD_QUIT = prop.getProperty("CMD_QUIT");
		} catch(IOException e) {
			e.printStackTrace();
		} finally {
			try {
				input.close();
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		readProperties();
		System.out.println(decorateStatus("OK: in: MOCHAD_HOST = " + MOCHAD_HOST + ", MOCHAD_PORT = " + MOCHAD_PORT));
		System.out.println(decorateStatus("OK: out: MQTT_HOST = " + MQTT_HOST + ", MQTT_PORT = " + MQTT_PORT + ", MQTT_TOPIC = " + MQTT_TOPIC));
		System.out.println(decorateStatus("INFO: Type " + CMD_QUIT + " to quit."));
		
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					String s;
					
					// output to MQTT
					MqttConnectOptions options;
					client = new MqttClient(MQTT_PROTOCOL + "://" + MQTT_HOST + ":" + MQTT_PORT, "pahomqttpublish1");
					options = new MqttConnectOptions();
					s = decorateStatus("Crashed.");
					options.setWill(MQTT_TOPIC, s.getBytes(), 0, true);
					client.connect(options);
					message = new MqttMessage();
					pubStatus("OK: Connected to MQTT.");
					
					// input from Mochad
					Socket clientSocket = new Socket(MOCHAD_HOST, MOCHAD_PORT);
					byte[] buffer = new byte[BUFFER_SIZE];
					int read;
					// int totalRead = 0;
					InputStream clientInputStream = clientSocket.getInputStream();
					pubStatus("OK: Connected to Mochad.");
					
					pubStatus("OK: Running.");
										
					while(doLoop) {
						while((read = clientInputStream.read(buffer)) != -1) {
							s = new String(buffer,0, read);
							// if(s.contains("_MS10A") || s.contains("_DS10A") || s.contains("_KR10A") || s.contains("House: A")) { // workaround to avoid loops
							if(s.contains(" Rx RFSEC") || s.contains(" Rx RF ") || s.contains(" Rx PL ")) {
								// message.setPayload(buffer);
								message.setPayload(s.getBytes());
								String suffix = "";
								if(s.contains("Motion_alert_MS10A")) { // not Motion_normal_MS10A
									if(s.contains("Addr: EC:EF:80")) suffix = "/motion/ms90/1";
									if(s.contains("Addr: xx:xx:xx")) suffix = "/motion/ms90/2";
									if(s.contains("Addr: 7A:44:00")) suffix = "/motion/ms90/3";
									if(s.contains("Addr: 98:CD:80")) suffix = "/motion/ms90/4";
								}
								else if(s.contains("HouseUnit: B")) { // MS13 motion sensor
									if(s.contains("B1 Func: On")) suffix = "/motion/ms13/1";
									if(s.contains("B2 Func: On")) suffix = "/motion/ms13/2";
									if(s.contains("B3 Func: On")) suffix = "/motion/ms13/3";
									if(s.contains("B4 Func: On")) suffix = "/motion/ms13/4";
								}
								else if(s.contains("_DS10A")) suffix = "/ds90/1";
								else if(s.contains("_KR10A")) suffix = "/remote/1";
								else if(s.contains("HouseUnit: A")) { // KR22 remote control
									suffix = "/remote/2";
									if(s.contains(" A1 Func: On")) suffix += "/a1/on"; // no trailing space!
									if(s.contains(" A1 Func: Off")) suffix += "/a1/off";
									if(s.contains(" A2 Func: On")) suffix += "/a2/on";
									if(s.contains(" A2 Func: Off")) suffix += "/a2/off";
									if(s.contains(" A3 Func: On")) suffix += "/a3/on";
									if(s.contains(" A3 Func: Off")) suffix += "/a3/off";
									if(s.contains(" A4 Func: On")) suffix += "/a4/on";
									if(s.contains(" A4 Func: Off")) suffix += "/a4/off";
								}
								else if(s.contains(" House: A")) {
									suffix = "/remote/2";
									if(s.contains(" Func: Dim")) suffix += "/a/dim"; // no trailing space!
									if(s.contains(" Func: Bright")) suffix += "/a/bright";
								}
								client.publish(MQTT_TOPIC + suffix, message);
								System.out.println(MQTT_TOPIC + suffix);
								System.out.println(s);
							}
						}
					}
					s = decorateStatus("INFO: Disconnecting.");
					System.out.println(s);
					client.publish(MQTT_TOPIC, s.getBytes(), 0, true);
					client.disconnect();
					// if(Thread.interrupted()) throw new RuntimeException();
				} catch(IOException e) {
					e.printStackTrace();
				} catch(MqttException e) {
					e.printStackTrace();
				} finally {
					System.out.println("finally");
				}
			}
		});
		t.start();
		
		// input from keyboard
		Scanner scanner = new Scanner(System.in);
		scanner.useDelimiter("[\n]+");

		while(doLoop && scanner.hasNextLine()) {
			String item = scanner.nextLine();
			doLoop = ! item.equalsIgnoreCase(CMD_QUIT);
		}
		// t.interrupt();
		System.out.println(decorateStatus("INFO: Exiting."));
		System.exit(0);
	}
}
