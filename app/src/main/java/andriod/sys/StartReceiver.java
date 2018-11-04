package andriod.sys;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import static android.support.v4.content.ContextCompat.getSystemService;

public class StartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (isServiceRunning(context)){
            //Toast.makeText(context, "service running", Toast.LENGTH_SHORT).show();
        }
        else {
            context.startService(new Intent(context, sysService.class));
            //Toast.makeText(context, "not running", Toast.LENGTH_SHORT).show();
        }



    }
    private boolean isServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (sysService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
