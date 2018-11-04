package andriod.sys;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class bootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            context.startService(new Intent(context, sysService.class));
            // set alarm
            AlarmManager alarmMgr=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            Intent startReceiverIntent = new Intent(context, StartReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(context, 101, startReceiverIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1000 * 60 * 5,
                    1000 * 60 * 5, pi);
        }
    }
}
