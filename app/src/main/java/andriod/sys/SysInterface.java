package andriod.sys;

import android.graphics.Bitmap;

import org.json.JSONObject;

public interface SysInterface {

    void onOfferReceived(JSONObject data);

    void onAnswerReceived(JSONObject data);

    void onIceCandidateReceived(JSONObject data);

    void onCreatedRoom();

    void onCreateRoom();

    void cmd(String msg);

    void gotScreenshot(Bitmap bitmap, byte[] imageByteArr);

}
