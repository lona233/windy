package com.example.zdycamera2;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    /**相机管理类，含有相机信息*/
    CameraManager mCameraManager;
    CameraDevice mCameraDevice;
    /**前后摄像头ID，后置摄像头时0*/
    String[] mFrontBackCameraID;

    /**预览，获取Surface*/
    TextureView mTevPreview;
    Surface surfaceTev;
    Size bestMatchSize;
    CameraCaptureSession mCaptureSession;

    /**'捕获'请求创建*/
    ArrayList<Surface> mSurfaceList = new ArrayList<>();
    private Button button,btn;
    private EditText x;
    private EditText y;
    private ImageView img1;
    private int count;
//    private ConstraintLayout mConstraintLayout;
//    private ConstraintSet constraintSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTevPreview = findViewById(R.id.camera_preview);
        ImageView img = (ImageView) findViewById(R.id.img);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(),R.drawable.file);
//        img.setImageBitmap(bitmap);
        img.setImageBitmap(handlerImageNegative(bitmap));

        mTevPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

                // 1. 获取CameraManager
                mCameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
                try {
                    // 2. 获取相机id列表
                    String[] cameraIdList = mCameraManager.getCameraIdList();
                    // 3. 根据相机id获取CameraCharacteristics，相机信息
                    mFrontBackCameraID = getFrontBackCameraID(cameraIdList); // 像头模糊（此处不适用）
                    mFrontBackCameraID[0] = mCameraManager.getCameraIdList()[0]; // 直接获取
                    // 获取合适尺寸
                    Size[] supportedSize = getConfigurationMap(mFrontBackCameraID[0]);
                    bestMatchSize = getBestMatchSize(supportedSize, 3 / 4.0f);
                    mTevPreview.getSurfaceTexture().setDefaultBufferSize(bestMatchSize.getWidth(), bestMatchSize.getHeight());
                    // 封装Surface的List对象
                    surfaceTev = new Surface(mTevPreview.getSurfaceTexture());
                    mSurfaceList.add(surfaceTev);
                    // 4. 开启相机(此处时后置摄像机)
                    openCameras(mFrontBackCameraID[0]);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            }
        });

        //定位坐标
        img1=(ImageView) findViewById(R.id.position_img);
        button=(Button) findViewById(R.id.button);
        x=(EditText) findViewById(R.id.edit_x);
        y=(EditText) findViewById(R.id.edit_y);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                float str_x = Integer.parseInt(x.getText().toString());
                float str_y = Integer.parseInt(y.getText().toString());
                img1.setX(str_x);
                img1.setY(str_y);
            }
        });

        btn=(Button) findViewById(R.id.btn);
        count=0;
        int[] file_id = new int[]{R.drawable.file,R.drawable.file1};
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (count>1)  count=0;
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(),file_id[count]);
                img.setImageBitmap(handlerImageNegative(bitmap));
                count=count+1;
            }
        });
    }

    /**
     * 1. 获取所有Hardware Level在FULL以上的相机ID
     * 2. 获取前后相机（0:后摄像头，1：前置摄像头）
     * */
    private String[] getFrontBackCameraID(String[] cameraIdList) throws CameraAccessException {
        String[] frontBackId = new String[2];
        for (String id : cameraIdList) {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                frontBackId[1] = id;
            }

            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                frontBackId[0] = id;
            }
        } // 获取合格的Camera
        return frontBackId;
    }

    @SuppressLint("MissingPermission")
    private void openCameras(String cameraId) {

        try {
            mCameraManager.openCamera(cameraId, cameraStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**监听相机开启状态*/
    CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            try {
                mCameraDevice.createCaptureSession(mSurfaceList, sessionStateCallback, null); // 获取到session
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
        }
    };

    /**获取相机支持的预览尺寸*/
    private Size[] getConfigurationMap(String id) {
        try {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            /*getOutputSizes支持以下类型:
             * ImageReader：常用来拍照或接收 YUV 数据。
             * MediaRecorder：常用来录制视频。
             * MediaCodec：常用来录制视频。
             * SurfaceHolder：常用来显示预览画面。
             * SurfaceTexture：常用来显示预览画面。
             * 不支持类型返回null。
             * */
            return map.getOutputSizes(SurfaceTexture.class);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**选取支持尺寸中最相符合的项
      @param aspectRatio: 短边/长边 **/

    private Size getBestMatchSize(Size[] supportSizes, float aspectRatio) {
        for (Size size: supportSizes) {
            if (size.getHeight()*1.0f / size.getWidth() == aspectRatio) {
                return size;
            }
        } // 找到尺寸正合适的直接返回

        float minDiff = 1; // 比例差不可能大于1
        Size bestMatchSize = supportSizes[0];
        for (Size size : supportSizes) {
            float diff = Math.abs(size.getHeight()*1.0f / size.getWidth() - aspectRatio);
            if (diff < minDiff) {
                bestMatchSize = size;
                minDiff = diff;
            }
        } // 获取比例最接近的Size（允许图像变形时添加此段）
        return bestMatchSize;
    }


     //8. ‘捕获会话’(CaptureSession)类状态监听。

    CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCaptureSession = session;
            try {
                CaptureRequest.Builder req_builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                req_builder.addTarget(surfaceTev);
                CaptureRequest request = req_builder.build();
                mCaptureSession.setRepeatingRequest(request, null, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraDevice != null) {
            mCameraDevice.close();
            Log.d(TAG, "Camera Closed");
        }
    }

    public Bitmap handlerImageNegative(Bitmap bm){
        int width = bm.getWidth();
        int height= bm.getHeight();
        int color;
        int r,g,b,a;
        Bitmap bmp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        int[] oldPx=new int[width*height];
        int[] newPx=new int[width*height];


        bm.getPixels(oldPx,0,width,0,0,width,height);
        for(int i =0; i < width*height;i++){
            color = oldPx[i];
            r = Color.red(color);
            g = Color.green(color);
            b = Color.blue(color);
            a = Color.alpha(color);

//            r = 255-r;
//            g = 255-g;
//            b = 255-b;
            if(r==255&g==255&b==255)
            {
                a=0;
            }
            if(r!=255)
            {
                r=255;
                g=0;
                b=0;
            }
            newPx[i]  = Color.argb(a,r,g,b);
        }
        bmp.setPixels(newPx,0,width,0,0,width,height);
        return bmp;
    }

}
