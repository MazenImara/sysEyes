package andriod.sys;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class Screenshot {

    public static Screenshot obj;
    MediaProjection mp;
    MediaProjectionManager mpm;
    WindowManager wm;
    Image image;
    ByteArrayOutputStream byteStream;
    byte[] imageByteArray;
    final DisplayMetrics metrics = new DisplayMetrics();



    public static Screenshot getObj(){
        if (obj == null) {
            obj = new Screenshot();
        }
        return obj;
    }
    public  void finish(){
        if (obj != null) {
            obj = null;
        }
        if (this.mp != null){
            this.mp.stop();
            this.mp = null;
        }
        this.mpm = null;
        this.wm = null;
        this.image = null;
        this.byteStream = null;
        this.byteStream = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void takeScreenshot(sysService sys){
        if (mpm == null){
            mpm = (MediaProjectionManager) sys.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        }
        if (mp == null){
            mp = mpm.getMediaProjection(sys.perResultCode, sys.perData);
        }
        wm = (WindowManager) sys.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        display.getMetrics(metrics);
        Point size = new Point();
        display.getRealSize(size);
        final int mWidth = size.x;
        final int mHeight = size.y;
        int mDensity = metrics.densityDpi;

        final ImageReader mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);

        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
        mp.createVirtualDisplay("screen-mirror", mWidth, mHeight, mDensity, flags, mImageReader.getSurface(), null, sys.handler);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                reader.setOnImageAvailableListener(null, sys.handler);


                image = reader.acquireLatestImage();

                final Image.Plane[] planes = image.getPlanes();
                final ByteBuffer buffer = planes[0].getBuffer();

                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * metrics.widthPixels;
                // create bitmap
                Bitmap bmp = Bitmap.createBitmap(metrics.widthPixels + (int) ((float) rowPadding / (float) pixelStride), metrics.heightPixels, Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(buffer);

                image.close();
                reader.close();

                Bitmap realSizeBitmap = Bitmap.createBitmap(bmp, 0, 0, metrics.widthPixels, bmp.getHeight());
                bmp.recycle();
                Log.d("takeScreenshot", "2");
                byteStream = new ByteArrayOutputStream();
                realSizeBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteStream);
                imageByteArray = byteStream.toByteArray();
                sys.gotScreenshot(realSizeBitmap, imageByteArray);
                realSizeBitmap.recycle();
                /* do something with [realSizeBitmap] */
            }
        }, sys.handler);
    }

}





