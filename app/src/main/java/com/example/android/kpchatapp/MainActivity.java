package com.example.android.kpchatapp;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.*;
import com.firebase.ui.auth.BuildConfig;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Here all the chats are maintained
 */
public class MainActivity extends AppCompatActivity {

    //Final variables
    private final int RC_SIGN_IN = 1, RC_PHOTO_PICKER = 2;

    //Firebase Instances
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private FirebaseDatabase firebaseDatabase;
    private DatabaseReference databaseReference;
    private ChildEventListener childEventListener;
    private FirebaseStorage firebaseStorage;
    private StorageReference storageReference;
    private FirebaseRemoteConfig firebaseRemoteConfig;

    //View instance variables
    private ConstraintLayout constraintLayout;
    private RecyclerView recyclerView;
    private LinearLayout linearLayout;
    private ImageView photoPickerButton;
    private Button sendButton;
    private ProgressBar progressBar;
    private EditText messageEditText;
    private ChatAdapter chatAdapter;

    //Other variables
    ArrayList<Message> messageArrayList;
    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final String CHAT_MSG_LENGTH_KEY = "chat_message_length";
    private static final String TAG = "MainActivity";
    private String username;
    private Message message;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //View intances object assignments
        constraintLayout = (ConstraintLayout) findViewById(R.id.constraint_layout);
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        linearLayout = (LinearLayout) findViewById(R.id.linear_layout);
        photoPickerButton = (ImageView) findViewById(R.id.photo_picker_button);
        sendButton = (Button) findViewById(R.id.send_button);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        messageEditText = (EditText) findViewById(R.id.message_edit_text);

        showProgress();

        //Other variables object assignments
        messageArrayList = new ArrayList<>();

        //Firebase instances object assignments
        //FirebaseAuth
        firebaseAuth = FirebaseAuth.getInstance();
        //AuthStateListener listens for the change in auth state in app
        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                if (firebaseUser == null) {
                    //Signed out
                    constraintLayout.setVisibility(View.INVISIBLE);
                    doSignoutCleanup();
                    authUi();
                } else {
                    //Signed in
                    String username = firebaseUser.getDisplayName();
                    doSigninInitialize(username);
                    constraintLayout.setVisibility(View.VISIBLE);
                }
            }
        };

        //FirebaseDatabase
        firebaseDatabase = FirebaseDatabase.getInstance();
        //child means node after root node
        databaseReference = firebaseDatabase.getReference().child("messages");

        //FirebaseStorage
        firebaseStorage = FirebaseStorage.getInstance();
        //child means folder after root folder
        storageReference = firebaseStorage.getReference().child("chat_photos");

        //FirebaseRemoteConfig
        firebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        //Applying RecyclerView Functionality
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        chatAdapter = new ChatAdapter(this, messageArrayList);
        recyclerView.setAdapter(chatAdapter);

        //Setting the maximum number of characters at a time to be DEFAULT_MSG_LENGTH_LIMIT that is 1000 initially
        messageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        //To judge if at least one character at edit text to enable button
        messageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    sendButton.setEnabled(true);
                } else {
                    sendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        //To send message at FirebaseDatabase on click of send button
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Message message = new Message(messageEditText.getText().toString(), username, null);
                //push() is used to generate unique push id's for each message to make them differentiable
                databaseReference.push().setValue(message);
                messageEditText.setText("");
            }
        });

        //To send photo to FirebaseStorage and store its path at FirebaseDatabase to make it loaded by Glide easily
        photoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, "true");
                startActivityForResult(intent.createChooser(intent, "Completed Action Using"), RC_PHOTO_PICKER);
            }
        });

        //Making a setting for remote config for Developer mode that is judged by BuildConfig.DEBUG
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        firebaseRemoteConfig.setConfigSettings(configSettings);

        //Setting a default value in RemoteConfig in case new value is not supplied
        Map<String, Object> defaultValue = new HashMap<>();
        defaultValue.put(CHAT_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);
        firebaseRemoteConfig.setDefaults(defaultValue);

        fetchConfig();
    }

    /**
     * Adding {@link com.google.firebase.auth.FirebaseAuth.AuthStateListener} to {@link FirebaseAuth}
     */
    @Override
    protected void onResume() {
        super.onResume();
        firebaseAuth.addAuthStateListener(authStateListener);
    }


    /**
     * Removing {@link com.google.firebase.auth.FirebaseAuth.AuthStateListener} to {@link FirebaseAuth}
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (authStateListener != null) {
            firebaseAuth.removeAuthStateListener(authStateListener);
            showProgress();
        }
        messageArrayList.clear();
        chatAdapter.notifyDataSetChanged();
        detachChildEventListener();
    }

    /**
     * Used to show progressBar and hide recyclerView
     */
    private void showProgress() {
        recyclerView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Used to hide progressBar and show recyclerView
     */
    private void hideProgress() {
        recyclerView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    /**
     * Used to attach {@link ChildEventListener} to {@link DatabaseReference}
     */
    private void attachChildEventListner() {
        if (childEventListener == null) {
            childEventListener = new ChildEventListener() {
                /**
                 * When a new child in the messages added, this function is called.
                 * Used in fetching newly added text messages and images from {@link FirebaseDatabase}
                 * @param dataSnapshot contains {@link Message} object
                 * @param s
                 */
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    message = dataSnapshot.getValue(Message.class);
                    messageArrayList.add(message);
                    chatAdapter.notifyDataSetChanged();
                    recyclerView.smoothScrollToPosition(messageArrayList.indexOf(message));
                    hideProgress();
                }

                /**
                 * When a child that is a message here, is changed.
                 * Used in FirebaseFunctions to replace text with emoji
                 * @param dataSnapshot contains {@link Message} object
                 * @param s
                 */
                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                    messageArrayList.remove(message);
                    message = dataSnapshot.getValue(Message.class);
                    messageArrayList.add(message);
                    chatAdapter.notifyDataSetChanged();
                    hideProgress();
                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }

            };
            databaseReference.addChildEventListener(childEventListener);
        }
    }

    /**
     * Used to detach {@link ChildEventListener} from {@link DatabaseReference}
     */
    private void detachChildEventListener() {
        if (childEventListener != null) {
            databaseReference.removeEventListener(childEventListener);
            //null is used to avoid adding and removing more than one Listener at a time that is for better reliability
            childEventListener = null;
        }
    }

    /**
     * Do the work for Logged in user.
     *
     * @param username the person's username.
     */
    private void doSigninInitialize(String username) {
        this.username = username;
        attachChildEventListner();
    }

    /**
     * Do the work for Logged out user.
     */
    private void doSignoutCleanup() {
        this.username = ANONYMOUS;
        messageArrayList.clear();
        chatAdapter.notifyDataSetChanged();
        detachChildEventListener();
    }

    /**
     * It helps in providing amazing UI for Auth process. Also helps in Reset password through email.
     */
    private void authUi() {
        AuthUI authUI = AuthUI.getInstance();
        AuthUI.SignInIntentBuilder signInIntentBuilder = authUI.createSignInIntentBuilder();
        signInIntentBuilder.setLogo(R.drawable.kpchatapp_logo_login);
        signInIntentBuilder.setTheme(R.style.AppTheme);
        //Providing my Github Accounts for PrivacyPolicy and TermsOfConditionsUrl
        signInIntentBuilder.setPrivacyPolicyUrl("https://github.com/kawarpreetsingh");
        signInIntentBuilder.setTosUrl("https://github.com/kawarpreetsingh");
        //Only giving the hint for smart lock but not help in saving credentials
        signInIntentBuilder.setIsSmartLockEnabled(false, true);
        //The providers used in Auth UI same as I set in FirebaseAuth console
        signInIntentBuilder.setAvailableProviders(Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()));
        Intent intent = signInIntentBuilder.build();
        startActivityForResult(intent, RC_SIGN_IN);
    }

    /**
     * To perform something after coming back from Activity that was started for getting some result.
     *
     * @param requestCode Either RC_SIGN_IN or RC_PHOTO_PICKER.
     * @param resultCode  can be any based upon user input. Here RESULT_OK or RESULT_CANCELED used.
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            //When back is pressed in Auth Process
            if (resultCode == RESULT_CANCELED) {
                finish();
            }
        } else if (requestCode == RC_PHOTO_PICKER) {
            if (resultCode == RESULT_OK) {
                showProgress();
                Uri photoUri = data.getData();
                //Create a ref till image name inside chat_photos folder
                StorageReference photoReference = storageReference.child(photoUri.getLastPathSegment());
                //Use the photoReference to send photo to FirebaseStorage
                UploadTask uploadTask = photoReference.putFile(photoUri);
                Toast.makeText(this, "Upload in progress...", Toast.LENGTH_LONG).show();
                Toast.makeText(this, "Wait for a while...", Toast.LENGTH_LONG).show();

                //After completion of Task check success or failure
                //On Success, send the url to FirebaseDatabase so that Image can be loaded by Glide
                uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Uri photoUrl = taskSnapshot.getDownloadUrl();
                        Message message = new Message(null, username, photoUrl.toString());
                        databaseReference.push().setValue(message);
                    }
                });

                //On failure, check if it is due to StorageException (that occurs when file with required size is not allowed to upload) or any other exception.
                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (e instanceof StorageException) {
                            Toast.makeText(MainActivity.this, "Sorry, your image size is too large. Try uploading smaller image", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Sorry, some problem occured. Try again after some time", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }
    }

    /**
     * For creating menu for logout button.
     *
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_1, menu);
        return true;
    }

    /**
     * Used to judge the click on logout in menu.
     *
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_sign_out) {
            showConfirmDialog();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Used to ask for confirmation to logout or not.
     */
    private void showConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(R.string.message_logout_confirmation);
        builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                firebaseAuth.signOut();
                doSignoutCleanup();
            }
        });
        builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });
        builder.create();
        builder.show();
    }

    /**
     * Used to fetch if there is any new value available.
     */
    private void fetchConfig() {
        // This time is used to cache the old value and then search for new updated value if the time is complete
        long cacheExpirationTime = 10;
        if (firebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpirationTime = 0;
        }
        Task task = firebaseRemoteConfig.fetch(cacheExpirationTime);
        // If new value is fetched, activate it as soon as possible
        task.addOnSuccessListener(new OnSuccessListener() {
            @Override
            public void onSuccess(Object o) {
                firebaseRemoteConfig.activateFetched();
            }
        });
        applyChanges();
    }

    /**
     * Apply the new value where it is required.
     */
    public void applyChanges() {
        Long chat_msg_length = firebaseRemoteConfig.getLong(CHAT_MSG_LENGTH_KEY);
        messageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(chat_msg_length.intValue())});
        // Just to show the Toast only when different value is applied to messageEditText than the previous one
        SharedPreferences preferences = getSharedPreferences("MYPREF", MODE_PRIVATE);
        Log.d(TAG, "Message Length : " + chat_msg_length);
        if (preferences.getInt("chat_length", 0) != chat_msg_length) {
            Toast.makeText(MainActivity.this, "Hey, Your one " + CHAT_MSG_LENGTH_KEY + " is now " + chat_msg_length, Toast.LENGTH_LONG).show();
            preferences.edit().putInt("chat_length", chat_msg_length.intValue()).commit();
        }
    }
}
