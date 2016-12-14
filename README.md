<p align="center">
  <img width="320" src="https://raw.githubusercontent.com/enioluwa23/BandData/master/app/src/main/band_icon-web.png">
</p>



# BandData

An Android app that collects raw data from a Microsoft Band 2 paired by Bluetooth. 

It is a 1/2 part application that works together with <a href="https://github.com/enioluwa23/PcServerReceiver">PCServerReceiver</a>.

Definitely still a work in progress as many things are not optimal.

## Features 

- Written in Android-Java with Android Studio.
- UI components defined in XML and programmed in Java.
- Sends data to PC by IP address over WiFi.

## Install

- Download the project and import it using Android Studio.
- Compile the application into an apk file
- Either upload the .apk file to allow an Android to download it, or manually transfer it to the Anroid otherwise.

## Dependencies

- A Microsoft Band 2 
- An Android phone running KitKat OS 4.4.2 or later [<i> Needs confirmation </i>]

## Configuration

- The IP address of the PC is set up  in <a href="https://github.com/enioluwa23/BandData/blob/master/app/src/main/java/drexelairlab/banddata/MainActivity.java">MainActivity.java</a>. This socket also needs a port matching the one programatically configured in the <a href="https://github.com/enioluwa23/PcServerReceiver/blob/master/src/Server.java">Server.java</a> of PcServerReceiver.
  
  ```java
  Socket client = new Socket("144.118.240.219", 2323);
  ```

## Usage

<i> Screenshots coming soon! </i>

- Pair the Microsoft Band 2 to the Android device
- Open the application
- The FIRST thing to do is to give the application permission to get Heart Rate data from the band. Hit the button and accept the dialog box
- The rest of the data does not require consent. Hit 'Get Band Data' and the data should start showing up on screen.
- Hit 'Connect to Server' to begin streaming the data to the configured PC server.
- Hit 'Disconnect from Server' to stop sending the data to the PC server.

## Known Issues

> Please build this list with issues related to user experience. 

- Pausing the app can skewer the network thread, causing the app to be in a weird state when re-opened.

## Contributing

Members of the Drexel AIR Lab can contribute using pull requests. Please see <a href="https://docs.google.com/document/d/1hueJ8q0_FTgQarVDhghgH3nDOef1GUFje2NScXixKpE">this guide</a> to learn how to create pull requests.

