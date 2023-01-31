package com.sistemidigitalim.blurrykids;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuItemCompat;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.sistemidigitalim.helper.AgeEstimationHelper;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";

    private AgeEstimationHelper ageEstimationHelper;
    private FaceDetector faceDetector;

    private Mat matImage;

    private TextView viewInfo;
    private ImageView imageView;

    private Button fromCameraBtn;
    private Button fromGalleryBtn;

    private TextView ageProgressLabel;
    private int selectedAge;

    private Uri outputUri;
    private Bitmap inputBitmap;

    private int numberOfAdults = 0;
    private int numberOfKids = 0;

    private String currentPhotoPath;

    public static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 101;
    public static final int IMAGE_FROM_CAMERA = 0;
    public static final int IMAGE_FROM_GALLERY = 1;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                    matImage = new Mat();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    static{
        if (OpenCVLoader.initDebug()){
            Log.d("MainActivity", "Opencv Loaded");
        }else{
            Log.d("MainActivity", "Opencv not Loaded");
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        OpenCVLoader.initDebug();

        //initalizing object of helper class
        ageEstimationHelper = new AgeEstimationHelper();

        //initialization of FaceDetector
        FaceDetectorOptions.Builder builder = new FaceDetectorOptions.Builder();
        FaceDetectorOptions faceDetectorOptions = builder.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build();
        faceDetector = FaceDetection.getClient(faceDetectorOptions);

        // Initialize the imageView (Link the imageView with front-end component ImageView)
        // Initialize the butons
        imageView = findViewById(R.id.output_image);
        fromCameraBtn = findViewById(R.id.camera_btn);
        fromGalleryBtn = findViewById(R.id.gallery_btn);


        //initialization of age model
        try {
            initializeAgeModel();
        } catch (IOException e) {
            Toast.makeText(this,"Cannot initialize age model",Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        // Check Permissions onCreate
        checkAndRequestPermissions(MainActivity.this);

        fromCameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent takePicture = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                //creating temp. image file and getting uri of that file
                File image = createImageFile();
                Uri photoUri = FileProvider.getUriForFile(getApplicationContext(), "com.sistemidigitalim.progetto"+".provider", image);
                takePicture.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

                //starting activity
                startActivityForResult(takePicture, IMAGE_FROM_CAMERA);
            }
        });

        fromGalleryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(pickPhoto, IMAGE_FROM_GALLERY);
            }
        });

        // set a change listener on the SeekBar
        SeekBar seekBar = findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(seekBarChangeListener);

        selectedAge = seekBar.getProgress();
        ageProgressLabel = findViewById(R.id.selectedAgeText);
        ageProgressLabel.setText("Minimum Age: " + selectedAge);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.header_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id){
            case R.id.app_bar_share:
                if (outputUri != null) {
                    // User chose the "Settings" item, show the app settings UI...
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_STREAM, outputUri);
                    intent.setType("image/jpeg");
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(Intent.createChooser(intent, "Share Via"));
                    return true;
                } else {
                    Toast.makeText(MainActivity.this, "Seleziona prima una foto dalla fotocamera o dalla galleria", Toast.LENGTH_LONG).show();
                }

        }
        return super.onOptionsItemSelected(item);

    }

    SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // updated continuously as the user slides the thumb
            selectedAge = progress;
            ageProgressLabel.setText("Minimum Age: " + selectedAge);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // called when the user first touches the SeekBar
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // called after the user finishes moving the SeekBar
            if(inputBitmap != null) {
                detectFaces(inputBitmap);
            }
        }
    };


    // function to check permission
    public static boolean checkAndRequestPermissions(final Activity context) {
        int WExtstorePermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int cameraPermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.CAMERA);
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (WExtstorePermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded
                    .add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(context, listPermissionsNeeded
                            .toArray(new String[listPermissionsNeeded.size()]),
                    REQUEST_ID_MULTIPLE_PERMISSIONS);
            return false;
        }
        return true;
    }

    //method used to crop bitmap into a rectangle with single face
    private Bitmap cropBitmap(Bitmap image, Rect rect) throws IllegalArgumentException{
        return Bitmap.createBitmap(
                image,
                rect.left + 5,
                rect.top,
                rect.width() - 10,
                rect.height() - 10);
    }

    private Paint createBlurPaint(int width){


        Paint paintForRectangle = new Paint();

        //drawing yellow rectangle if person <= selectedAge
        paintForRectangle.setColor(Color.TRANSPARENT);


        //setting outlined empty rectangle
        paintForRectangle.setStyle(Paint.Style.STROKE);

        //painting thinner edges of rectangle in case of lower resolution images
        if(width<500)
            paintForRectangle.setStrokeWidth(5);
        else if(width>=500 && width<1000)
            paintForRectangle.setStrokeWidth(10);
        else if(width>=1000 && width<2000)
            paintForRectangle.setStrokeWidth(15);
        else if(width>=2000 && width<3000)
            paintForRectangle.setStrokeWidth(20);
        else
            paintForRectangle.setStrokeWidth(25);

        return paintForRectangle;
    }

    //method used to create and return Paint object which helps to draw rectangle around detected face
    private Paint createPaint(int width){
        Paint paintForRectangle = new Paint();

        //drawing green rectangle if person is >= 18yo
        paintForRectangle.setColor(Color.TRANSPARENT);
        numberOfAdults++;


        //setting outlined empty rectangle
        paintForRectangle.setStyle(Paint.Style.STROKE);

        //painting thinner edges of rectangle in case of lower resolution images
        if(width<500)
            paintForRectangle.setStrokeWidth(5);
        else if(width>=500 && width<1000)
            paintForRectangle.setStrokeWidth(10);
        else if(width>=1000 && width<2000)
            paintForRectangle.setStrokeWidth(15);
        else if(width>=2000 && width<3000)
            paintForRectangle.setStrokeWidth(20);
        else
            paintForRectangle.setStrokeWidth(25);

        return paintForRectangle;
    }


    // Handled permission Result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS:
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(),
                            "FlagUp Requires Access to Camera.", Toast.LENGTH_SHORT)
                            .show();

                } else if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(),
                            "FlagUp Requires Access to Your Storage.",
                            Toast.LENGTH_SHORT).show();

                }
                break;
        }
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case IMAGE_FROM_CAMERA:
                    if (resultCode == RESULT_OK) {
                        //creating bitmap from image taken with camera
                        this.inputBitmap = BitmapFactory.decodeFile(currentPhotoPath);
                        imageView.setImageBitmap(inputBitmap);
                        imageView.setRotation(-90);
                        detectFaces(inputBitmap);
                    }
                    break;
                case IMAGE_FROM_GALLERY:
                    if (resultCode == RESULT_OK && data != null) {
                        Uri imageUri = data.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        if (imageUri != null) {
                            try {
                                this.inputBitmap = rotateBitmap(MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri), 0);
                                imageView.setImageBitmap(inputBitmap);
                                detectFaces(inputBitmap);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                    break;
            }
        }
    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Output Image", null);
        return Uri.parse(path);
    }

    private Uri saveImage(Bitmap image) {
        //TODO - Should be processed in another thread
        File imagesFolder = new File(getExternalFilesDir("images"), "images");
        Uri uri = null;
        try {
            imagesFolder.mkdirs();
            File file = new File(imagesFolder, "shared_image.png");

            FileOutputStream stream = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.PNG, 90, stream);
            stream.flush();
            stream.close();
            uri = FileProvider.getUriForFile(getApplicationContext(), "com.sistemidigitalim.progetto"+".provider", file);

        } catch (IOException e) {
            Log.d(TAG, "IOException while trying to write file for sharing: " + e.getMessage());
        }
        return uri;
    }

    private int getNumberOfAdults(){
        return this.numberOfAdults;
    }

    private int getNumberOfKids(){
        return this.numberOfKids;
    }

    private void resetCounter() {
        this.numberOfAdults = 0;
        this.numberOfKids = 0;
    }

    //method used to detect, count and identify age of faces
    private void detectFaces(Bitmap image){
        InputImage inputImage = InputImage.fromBitmap(image,0);

        AtomicReference<String> description = new AtomicReference<>("");

        //pass picture to MLKIT face detector
        faceDetector.process(inputImage).addOnSuccessListener(faces ->{

            //if there is at least one face detected
            if(faces.size()!=0){

                //creating temp. bitmap and canvas object
                Bitmap tempBitmap = Bitmap.createBitmap(image.getWidth(),image.getHeight(), Bitmap.Config.RGB_565);
                Canvas tempCanvas = new Canvas(tempBitmap);

                tempCanvas.drawBitmap(image,0,0,null);

                //declaring array used to store age of detected faces
                float [] age = new float[faces.size()];
                int i = 0;

                //loop for each of detected faces
                for(Face face : faces){

                    //creating rectangle with bounds of detected face
                    Rect rect = face.getBoundingBox();

                    Log.d(TAG, "Dims " + rect.width()+ " " + rect.height());

                    try{
                        //estimating age of person and drawing rectangle around detected face on bitmap
                        age[i] = (float) Math.floor(ageEstimationHelper.predictAge(cropBitmap(image,rect)));
                        if(age[i]<selectedAge){
                            numberOfKids++;
                            int width = Math.abs(rect.left-rect.right);
                            int heigth = Math.abs(rect.top-rect.bottom);
                            if (width % 2 == 0)
                                width++;
                            if( heigth % 2==0)
                                heigth++;


                            // roi = rect
                            matImage = new Mat();
                            Utils.bitmapToMat(tempBitmap, matImage);
                            // blur the roi
                            org.opencv.core.Rect roi = new org.opencv.core.Rect(rect.left,rect.top,rect.width(),rect.height());
                            Mat matRoi = matImage.submat(roi);
                            Imgproc.GaussianBlur(matRoi, matRoi,new Size(width,heigth),0);
                            Utils.matToBitmap(matImage, tempBitmap);

                            description.set(description + " Minimum Age: " + age[i] +" \n");
                            //viewInfo.setText(description.toString());

                            tempCanvas.drawRoundRect(new RectF(face.getBoundingBox()),2,2,createBlurPaint(image.getWidth()));
                        }else{
                            description.set(description + " Minimum Age: " + age[i] +" \n");
                            //viewInfo.setText(description.toString());
                            tempCanvas.drawRoundRect(new RectF(face.getBoundingBox()),2,2,createPaint(image.getWidth()));
                        }

                    }catch(IllegalArgumentException e){
                        //if theres a problem with inappropriate frame
                        e.printStackTrace();
                        Toast.makeText(this,"Cannot estimate age - rearrange frame of picture", Toast.LENGTH_LONG).show();
                    }
                    i++;

                }

                //displaying bitmap with rectangles inside ImageView
                imageView.setImageDrawable(new BitmapDrawable(getResources(),tempBitmap));
                imageView.setImageBitmap(tempBitmap);
                outputUri = saveImage(tempBitmap);
                imageView.setImageURI(outputUri);

                if (getNumberOfKids() == 0) {
                    Toast.makeText(getApplicationContext(),
                            "No kids detected with age <" + selectedAge + " in this photo.",
                            Toast.LENGTH_SHORT).show();
                }

            } else{
                //if there are no faces detected
                Toast.makeText(this,"No faces found in analyzed picture",Toast.LENGTH_SHORT).show();
            }
            resetCounter();
            imageView.setBackgroundColor(Color.TRANSPARENT);


        });
    }

    //method used to initialize model
    private void initializeAgeModel() throws IOException {
        Interpreter.Options options = new Interpreter.Options();

        //loading tflite file (from https://github.com/shubham0204/Age-Gender_Estimation_TF-Android)
        Interpreter ageModelInterpreter = new Interpreter(FileUtil.loadMappedFile(this, "model_age.tflite"), options.addDelegate(new NnApiDelegate()));

        //initializing interpreter from helper class
        ageEstimationHelper.interpreter = ageModelInterpreter;
    }

    //getting real path from uri
    private String getFilePath(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};

        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(projection[0]);
            String picturePath = cursor.getString(columnIndex); // returns null
            cursor.close();
            return picturePath;
        }
        return null;
    }

    //method used to create temp. file
    private File createImageFile(){
        File temporaryPhotoFile = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        try {
            //creating temp. image.jpg file
            File tempFile = File.createTempFile("image",".jpg",temporaryPhotoFile);

            //getting path to that file
            currentPhotoPath = tempFile.getAbsolutePath();
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void deleteImage(String filepath) {
        File fdelete = new File(filepath.toString());
        if (fdelete.exists()) {
            if (fdelete.delete()) {
                System.out.println("file Deleted :" + filepath);
            } else {
                System.out.println("file not Deleted :" + filepath);
            }
        }
    }

    public static Bitmap rotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }



}
