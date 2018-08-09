package andriod.sys;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);
        startService(new Intent(getBaseContext(), sysService.class));
        Toast.makeText(getApplicationContext(), "from main acti", Toast.LENGTH_LONG).show();
    }
}
