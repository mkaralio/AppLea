package com.example.commontask;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.example.commontask.fragment.PostListFragment;
import com.example.commontask.model.Post;
import com.example.commontask.model.User;
import com.example.commontask.ui.ProfileActivity;
import com.example.commontask.utils.Constants;
import com.example.commontask.utils.EmailEncoding;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.weiwangcn.betterspinner.library.BetterSpinner;

import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NewPostActivity3 extends BaseActivity {
  String imageLocation;
  private static final String TAG = "NewPostActivity";
  private static final String REQUIRED = "Required";

  //SCORES
  private static final Long COUNTER_IMAGE_POST = 25L;
  private static final Long COUNTER_IMAGE = 15L;
  private static final Long COUNTER_TEXT_POST = 10L;

  private Context mView;
  // [START declare_database_ref]
  private DatabaseReference mDatabase;
  // [END declare_database_ref]
  private StorageReference mStorage;
  private EditText mTitleField;
  private EditText mBodyField;
  private FloatingActionButton mSubmitButton;
  private FirebaseAuth mFirebaseAuth;
  private FirebaseDatabase mFirebaseDatabase;
  private String currentUserEmail,currentUserEmail1;
  ImageButton fab1,fab2;
  FloatingActionButton fab;
  String downloadURl;
  private DatabaseReference mCurrentUserDatabaseReference;
  private DatabaseReference mCurrentUserDatabaseReference1;
  private static int RESULT_LOAD_IMAGE = 1;
  private static int RESULT_LOAD_TEXT_ONLY = 0;
  boolean imageload=false;
  boolean textOnly = false;
  Uri selectedImage;
  String key,userTestEmail;
  String  em;
  BetterSpinner spinner5;
  String item1,item2,item3,item4;
  TextView textView,textView1,textView2,textView3,textView4,textView5,textView6,textView7,textView8;
  String userTestLocation;
  String currentTime;
  Spinner areaSpinner,spinner3;
  private DatePickerDialog.OnDateSetListener mDateSetListener;
  private TextView mDisplayDate;
  private Calendar calendar = Calendar.getInstance();
  String strHrsToShow;
  String  name,startdate,starttime;


  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_new_post1);
    mView=NewPostActivity3.this;

    // String[] list2 = getResources().getStringArray(R.array.shopping_item);
    String[] list4 = getResources().getStringArray(R.array.duration);
    String[] list = getResources().getStringArray(R.array.shopping_item);
    // [START initialize_database_ref]


    mDatabase = FirebaseDatabase.getInstance().getReference();
    // [END initialize_database_ref]
    key = mDatabase.child("posts").push().getKey();
    spinner3=(Spinner) findViewById(R.id.spinner3);
    spinner5=(BetterSpinner) findViewById(R.id.spinner5);
    textView2=(TextView) findViewById(R.id.txttask);
    textView3=(TextView) findViewById(R.id.textView5);
    textView4=(TextView) findViewById(R.id.txtxorafi);
    textView5=(TextView) findViewById(R.id.txtseek);
    textView6=(TextView) findViewById(R.id.txtkind);
    textView7=(TextView) findViewById(R.id.tvDate);
    textView8=(TextView) findViewById(R.id.tvTime);
    mDisplayDate = (TextView) findViewById(R.id.textView4);

    areaSpinner = (Spinner) findViewById(R.id.spinner2);
    currentTime = DateFormat.getDateTimeInstance().format(new Date());


    ImageView backArrow = (ImageView)findViewById(R.id.backArrow);
    backArrow.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.d(TAG, "onClick: navigating back to ProfileActivity");
        finish();
      }
    });

    ArrayAdapter<String> adapter4 = new ArrayAdapter<String>(this,
            android.R.layout.simple_dropdown_item_1line, list4);

    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
            android.R.layout.simple_dropdown_item_1line, list);

    Bundle extras = getIntent().getExtras();

          if (extras != null) {
            name = extras.getString("title").trim();
            startdate = extras.getString("startdate").trim();
            starttime = extras.getString("starttime").trim();
           BlogViewInfoHolder viewHolder = new BlogViewInfoHolder();{
              if (viewHolder != null) {
               viewHolder.setDate(startdate);
               viewHolder.setTime(starttime);
              }
            }
          }

    item1=name;
    textView2.setText(item1);
    spinner5.setAdapter(adapter4);
    spinner3.setAdapter(adapter);

    DiscreteSeekBar discreteSeekBar1 = (DiscreteSeekBar) findViewById(R.id.discrete1);


    discreteSeekBar1.setNumericTransformer(new DiscreteSeekBar.NumericTransformer() {
      @Override
      public int transform(int value) {
        return value * 1;
      }
    });
    discreteSeekBar1.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
      @Override
      public void onProgressChanged(DiscreteSeekBar seekBar, int value, boolean fromUser) {
        item2= String.valueOf(value);
        textView5.setText(String.valueOf(value) + " "+" L");
      }

      @Override
      public void onStartTrackingTouch(DiscreteSeekBar seekBar) {

      }

      @Override
      public void onStopTrackingTouch(DiscreteSeekBar seekBar) {

      }
    });

    textView7.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                NewPostActivity3.this,
                android.R.style.Theme_Holo_Light_Dialog_MinWidth,
                mDateSetListener,
                year,month,day);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
      }
    });


    textView8.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        // TODO Auto-generated method stub
        timeOnClick(v);

      }
    });


    mDateSetListener = new DatePickerDialog.OnDateSetListener() {
      @Override
      public void onDateSet(DatePicker datePicker, int year, int month, int day) {
        month = month + 1;
        Log.d(TAG, "onDateSet: mm/dd/yyy: " + month + "/" + day + "/" + year);

        String date = day + "/" + month + "/" + year;
        mDisplayDate.setText(date);
      }
    };
    mFirebaseAuth = FirebaseAuth.getInstance();
    mFirebaseDatabase = FirebaseDatabase.getInstance();
    currentUserEmail = EmailEncoding.commaEncodePeriod(mFirebaseAuth.getCurrentUser().getEmail());
    mCurrentUserDatabaseReference = mFirebaseDatabase
            .getReference().child(Constants.USERS_LOCATION
                    + "/" + currentUserEmail);

    mCurrentUserDatabaseReference1 = mFirebaseDatabase
            .getReference().child(Constants.LOCATIONS
                    + "/" + currentUserEmail);
    mCurrentUserDatabaseReference1.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {

        final List<String> areas = new ArrayList<String>();
        String currentUserEmailfinal = EmailEncoding.commaEncodePeriod(mFirebaseAuth.getCurrentUser().getEmail());

        final String uid=getUid();
        for (DataSnapshot areaSnapshot : dataSnapshot.getChildren()) {
          if(uid.equalsIgnoreCase(currentUserEmailfinal)){
            String areaName = ((HashMap)areaSnapshot.getValue()).get("place_name").toString();
            areas.add(areaName);

            ArrayAdapter<String> areasAdapter = new ArrayAdapter<String>(NewPostActivity3.this, android.R.layout.simple_spinner_item, areas);
            areasAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            areaSpinner.setAdapter(areasAdapter);
          }
        }
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {

      }
    });


    mCurrentUserDatabaseReference1 = mFirebaseDatabase
            .getReference().child(Constants.LOCATIONS
                    + "/" + currentUserEmail);
    mCurrentUserDatabaseReference1.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {

        final List<String> areas = new ArrayList<String>();
        String currentUserEmailfinal = EmailEncoding.commaEncodePeriod(mFirebaseAuth.getCurrentUser().getEmail());

        final String uid=getUid();
        for (DataSnapshot areaSnapshot : dataSnapshot.getChildren()) {
          if(uid.equalsIgnoreCase(currentUserEmailfinal)){
            String areaName = ((HashMap)areaSnapshot.getValue()).get("place_name").toString();
            areas.add(areaName);

            ArrayAdapter<String> areasAdapter = new ArrayAdapter<String>(NewPostActivity3.this, android.R.layout.simple_spinner_item, areas);
            areasAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            areaSpinner.setAdapter(areasAdapter);
          }
        }
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {

      }
    });




    areaSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1,
                                 int arg2, long arg3) {
        item3 = areaSpinner.getSelectedItem().toString();
        textView4.setText(item3);
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // TODO Auto-generated method stub

      }
    });


    spinner3.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1,
                                 int arg2, long arg3) {
        item4 = spinner3.getSelectedItem().toString();
        textView6.setText(item4);
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // TODO Auto-generated method stub

      }
    });

    openImageSelector();
    ImageView checkmark = (ImageView) findViewById(R.id.saveChanges);
    mCurrentUserDatabaseReference= FirebaseDatabase.getInstance().getReference().child("posts").child(key);
    checkmark.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        submitPost();
      }
    });
  }

  public void openImageSelector(){
    fab = (FloatingActionButton) findViewById(R.id.fab);

    fab.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View arg0) {

        Intent i = new Intent(
                Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        startActivityForResult(i, RESULT_LOAD_IMAGE);
      }
    });

  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
      imageload=true;

      selectedImage= data.getData();
      String[] filePathColumn = {MediaStore.Images.Media.DATA};

      Cursor cursor = getContentResolver().query(selectedImage,
              filePathColumn, null, null, null);
      cursor.moveToFirst();

      int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
      String picturePath = cursor.getString(columnIndex);
      cursor.close();

      ImageView imageView = (ImageView) findViewById(R.id.img);
      imageView.setImageBitmap(BitmapFactory.decodeFile(picturePath));

    }
  }



  TimePickerDialog.OnTimeSetListener time = new TimePickerDialog.OnTimeSetListener() {
    @Override
    public void onTimeSet(TimePicker view, int hour, int minute) {
      calendar.set(Calendar.HOUR, hour);
      calendar.set(Calendar.MINUTE, minute);
      String am_pm = "";
      if (calendar.get(Calendar.AM_PM) == Calendar.AM)
        am_pm = "ΠM";
      else if (calendar.get(Calendar.AM_PM) == Calendar.PM)
        am_pm = "ΜM";

      strHrsToShow = (hour+":"+minute+" "+am_pm);
      setCurrentDateOnView();

    }
  };


  public class BlogViewInfoHolder {


    public void setDate(String date) {
      mDisplayDate.setText(date);
    }


    public void setTime(String time) {
      textView3.setText(time);
    }
  }

  public void timeOnClick(View view) {
    new TimePickerDialog(this, time, calendar.get(Calendar.HOUR), calendar.get(Calendar.MINUTE), false).show();
  }

  public void setCurrentDateOnView() {
    //  Locale locale = new Locale("el-GR");
    // String timeFormat = "hh:mm a";
    // SimpleDateFormat stf = new SimpleDateFormat(timeFormat, locale.US);

    textView3.setText(strHrsToShow);

  }

  private void submitPost() {

    final String title = "";
    final String body = item3;
    final String body2 =  item4;
    final String body3 =item1;
    final String body4 = spinner5.getText().toString() + " " + textView5.getText().toString();
    final String body5=mDisplayDate.getText().toString() + " " +textView3.getText().toString();

    //String tmp  = body.replace(" ","");
    //"^[A-Za-z]+$"
    // String str1="^[A-Za-zΑ-ΩΆ-Ωά-ώα-ω]+$";

    // if(tmp.matches(str1)){
    textOnly = true;
    // }
    // Disable button so there are no multi-posts

    Toast.makeText(this, "Posting...", Toast.LENGTH_SHORT).show();

    mFirebaseAuth = FirebaseAuth.getInstance();
    mFirebaseDatabase = FirebaseDatabase.getInstance();
    currentUserEmail = EmailEncoding.commaEncodePeriod(mFirebaseAuth.getCurrentUser().getEmail());

    // [START single_value_read]
    final String userId = currentUserEmail;


    mDatabase.child("users").child(userId).addListenerForSingleValueEvent(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot dataSnapshot) {
                // Get user value
                User user = dataSnapshot.getValue(User.class);

                // [START_EXCLUDE]
                if (user == null) {
                  // User is null, error out
                  Log.e(TAG, "User " + userId + " is unexpectedly null");
                  Toast.makeText(NewPostActivity3.this,
                          "Error: could not fetch user.",
                          Toast.LENGTH_SHORT).show();
                } else {

                  SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                  Date date = new Date();
                  String timestamp = dateFormat.format(date);

                  final Post post=new Post();
                  // Write new post
                  writeNewPost(userId, user.getUsername(),user.getProfilePicLocation(),post.getImagepic(), title, body,body2,body3,body4,body5,timestamp,key);

                  if(imageload && textOnly){
                    mStorage = FirebaseStorage.getInstance().getReference();

                    //Keep all images for a specific chat grouped together
                    imageLocation  = "Posts/post_picture/" + currentUserEmail;
                    final String imageLocationId = imageLocation + "/" + selectedImage.getLastPathSegment();
                    final String uniqueId = UUID.randomUUID().toString();
                    final StorageReference filepath = mStorage.child(imageLocation).child(uniqueId + "/post_pic");
                    final String downloadURl = filepath.getPath();


                    filepath.putFile(selectedImage).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                      @Override
                      public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        //create a new message containing this image
                        addImageToProfile(downloadURl);
                      }
                    });
                  }
                  else if(textOnly){
                    increaseByImageAndPost(false, true);
                  }
                  else{
                    mStorage = FirebaseStorage.getInstance().getReference();

                    //Keep all images for a specific chat grouped together
                    imageLocation  = "Posts/post_picture/" + currentUserEmail;
                    final String imageLocationId = imageLocation + "/" + selectedImage.getLastPathSegment();
                    final String uniqueId = UUID.randomUUID().toString();
                    final StorageReference filepath = mStorage.child(imageLocation).child(uniqueId + "/post_pic");
                    final String downloadURl = filepath.getPath();

                    filepath.putFile(selectedImage).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                      @Override
                      public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        //create a new message containing this image

                        mCurrentUserDatabaseReference.child("imagepic").setValue(downloadURl);
                      }
                    });

                    increaseByImageAndPost(true, false);
                  }

                }

                // Finish this Activity, back to the stream
                Intent intent = new Intent(getApplicationContext(), PostListFragment.class);
                startActivityForResult(intent, 0);
                // [END_EXCLUDE]
              }

              @Override
              public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "getUser:onCancelled", databaseError.toException());
                // [START_EXCLUDE]
                // [END_EXCLUDE]
              }
            });
    // [END single_value_read]
  }
  //call when image && post
  private void addImageToProfile(final String downloadURl) {
    mCurrentUserDatabaseReference.child("imagepic").setValue(downloadURl);
    increaseByImageAndPost(true,true);
  }

  private void increaseByImageAndPost(final boolean hasImage, final boolean hasText) {

    mFirebaseAuth = FirebaseAuth.getInstance();
    mFirebaseDatabase = FirebaseDatabase.getInstance();
    currentUserEmail = EmailEncoding.commaEncodePeriod(mFirebaseAuth.getCurrentUser().getEmail());
    mCurrentUserDatabaseReference1 = mFirebaseDatabase
            .getReference().child(Constants.USERS_LOCATION
                    + "/" + currentUserEmail);
    Query query = mCurrentUserDatabaseReference1;
    query.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {

        String  currentUserEmail1 = EmailEncoding.commaDecodePeriod(mFirebaseAuth.getCurrentUser().getEmail());
        String userTestEmail = ((HashMap) dataSnapshot.getValue()).get("email").toString();
        if(userTestEmail.equalsIgnoreCase(currentUserEmail1)){
          if(hasImage && hasText){
            long i = (long) ((HashMap) dataSnapshot.getValue()).get("score") + COUNTER_IMAGE_POST;
            mCurrentUserDatabaseReference1.child("score").setValue(i);

            final Toast tag = Toast.makeText(getBaseContext(),"Οι πόντοι σας είναι:  " + i + " !",Toast.LENGTH_SHORT);

            tag.show();

            new CountDownTimer(7000, 1000)
            {

              public void onTick(long millisUntilFinished) {tag.show();}
              public void onFinish() {tag.show();}

            }.start();
          }
          else if(!hasText){
            long i = (long) ((HashMap) dataSnapshot.getValue()).get("score") + COUNTER_IMAGE;
            mCurrentUserDatabaseReference1.child("score").setValue(i);
            final Toast tag = Toast.makeText(getBaseContext(),"Οι πόντοι σας είναι:  " + i + " !",Toast.LENGTH_SHORT);

            tag.show();

            new CountDownTimer(7000, 1000)
            {

              public void onTick(long millisUntilFinished) {tag.show();}
              public void onFinish() {tag.show();}

            }.start();
          }
          else {
            long i = (long) ((HashMap) dataSnapshot.getValue()).get("score") + COUNTER_TEXT_POST;
            mCurrentUserDatabaseReference1.child("score").setValue(i);
            final Toast tag = Toast.makeText(getBaseContext(),"Οι πόντοι σας είναι: " + i + " !",Toast.LENGTH_SHORT);

            tag.show();

            new CountDownTimer(7000, 1000)
            {

              public void onTick(long millisUntilFinished) {tag.show();}
              public void onFinish() {tag.show();}

            }.start();
          }

        }

      }

      @Override
      public void onCancelled(DatabaseError databaseError) {

      }
    });

  }




  // [START write_fan_out]
  private void writeNewPost(String userId, String username,String profilePicLocation,String imagepic, String title, String body,String body1,String body2,String body3,String body4,String timestamp,String key) {
    // Create new post at /user-posts/$userid/$postid and at
    // /posts/$postid simultaneously

    Post post = new Post(userId, username,profilePicLocation,imagepic, title, body,body1,body2,body3,body4,timestamp,key);
    Map<String, Object> postValues = post.toMap();

    Map<String, Object> childUpdates = new HashMap<>();
    childUpdates.put("/posts/" + key, postValues);
    childUpdates.put("/user-posts/" + userId + "/" + key, postValues);

    mDatabase.updateChildren(childUpdates);
  }
  public String getUid() {
    mFirebaseAuth = FirebaseAuth.getInstance();
    mFirebaseDatabase = FirebaseDatabase.getInstance();
    currentUserEmail = EmailEncoding.commaEncodePeriod(mFirebaseAuth.getCurrentUser().getEmail());
    return currentUserEmail;
  }


}
