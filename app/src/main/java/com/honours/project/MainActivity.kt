package com.honours.project

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.icu.text.SimpleDateFormat
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.core.widget.addTextChangedListener
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.firebase.ui.auth.AuthUI
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.honours.project.models.Award
import com.honours.project.models.Report
import com.honours.project.models.User
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

private const val TAG = "MainActivity"

// Request parameters
private const val REQUEST_IMAGE_CAPTURE = 1



private const val AUTH_REQUEST_SIGN_IN = 201

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener,
    AdapterView.OnItemSelectedListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private lateinit var navView: NavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var menu: Menu

    // Firebase Auth
    private lateinit var auth: FirebaseAuth
    private lateinit var uid: String
    private var signedIn = false

    // Firebase Storage
    private lateinit var storage: FirebaseStorage
    private lateinit var imageRef: StorageReference
    private lateinit var currentPhotoPath: String

    // Firebase Database
    private lateinit var database: FirebaseDatabase
    private lateinit var dataRef: DatabaseReference
    private lateinit var dataReportRef: DatabaseReference
    private lateinit var contributionsListener: ValueEventListener
    private lateinit var countListener: ValueEventListener
    private lateinit var scoreListener: ValueEventListener
    private lateinit var awardsListener: ValueEventListener
    private lateinit var dataUserRef: DatabaseReference
    private var userScore = 0L
    private var contributionCount = 0L
    private var awards = ArrayList<Award>()
    private lateinit var location: Location

    // Leaderboard State
    private var leaderboard = ArrayList<User>()

    // List of Contributions
    private var contributions = ArrayList<Report>()
    private var marked = ArrayList<String>()

    private var description = ""
    private var category = ""

    private val model: MarkerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        drawerLayout = findViewById(R.id.drawer_layout)
        drawerToggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.nav_app_bar_open_drawer_description, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        navView = nav_view
        navController = findNavController(R.id.nav_host_fragment)

        appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        menu = navView.menu

        /*
            SIGN IN
        */
        auth = FirebaseAuth.getInstance()
        val current = auth.currentUser
        signedIn = current != null

        /*
            STORAGE SETUP
         */
        // Get the firebase storage object
        storage = Firebase.storage

        // Get a reference for the images folder
        imageRef = storage.reference.child("images")

        /*
            DATABASE SETUP
         */
        // Get the firebase database object
        database = Firebase.database

        // Get a references to locations in the database database
        dataRef = database.reference
        dataReportRef = dataRef.child("reports")
        dataUserRef = dataRef.child("users")

        // Listener for checking the user's score
        scoreListener = object : ValueEventListener {
            override fun onDataChange(p0: DataSnapshot) {

                if (p0.exists()) {
                    // If the user has a score store the value locally
                    userScore = p0.value as Long
                } else {
                    // If the user has no score the user needs to be initialised
                    dataUserRef.child(uid).child("name").setValue(current!!.displayName)
                    dataUserRef.child(uid).child("score").setValue(userScore)
                    dataUserRef.child(uid).child("contributions").setValue(contributionCount)
                    awards.add(0, Award(0, "First Report", "Make your first Contribution", false))
                    awards.add(1, Award(1, "Community Helper", "Make 10 Contributions", false))
                    awards.add(2, Award(2, "Community Icon", "Make 100 Contributions", false))
                    awards.forEach {
                        dataUserRef.child(uid).child("awards").child(it.id.toString())
                            .setValue(it)
                    }
                }
            }

            override fun onCancelled(p0: DatabaseError) {
                Log.e(TAG, "Database Retrieve Cancelled")
            }
        }

        // Listener for checking the user's score
        countListener = object : ValueEventListener {
            override fun onDataChange(p0: DataSnapshot) {

                if (p0.exists()) {
                    // If the user has a score store the value locally
                    contributionCount = p0.value as Long
                } else {
                    // If the user has no count the value needs to be initialised
                    dataUserRef.child(uid).child("count").setValue(contributionCount)

                }
            }

            override fun onCancelled(p0: DatabaseError) {
                Log.e(TAG, "Database Retrieve Cancelled")
            }
        }

        // Listener for checking the user's contributions
        contributionsListener = object: ValueEventListener {
            override fun onDataChange(p0: DataSnapshot) {
                Log.i("Count " ,""+p0.childrenCount)
                p0.children.forEach {
                    contributions.clear()

                    // Use the imageRef as a key as it is unique
                    val ref = it.child("imgRef").value.toString()

                    val new = Report(it.child("owner").value.toString(),
                        it.child("lat").value as Double,
                        it.child("long").value as Double,
                        ref,
                        it.child("time").value.toString(),
                        it.child("category").value.toString(),
                        it.child("description").value.toString())

                    contributions.add(new)

                    if(!marked.contains(ref)){
                        marked.add(ref)
                        model.mark(new)
                    }

                }
            }

            override fun onCancelled(p0: DatabaseError) {
                Log.e(TAG, "Database Retrieve Cancelled")
            }
        }

        // Listener for checking the user's awards
        awardsListener = object: ValueEventListener {
            override fun onDataChange(p0: DataSnapshot) {
                Log.i("Count " ,""+p0.childrenCount)
                awards.clear()
                p0.children.forEach {
                    val new = Award(((it.child("id").value) as Long).toInt(),
                        it.child("title").value.toString(),
                        it.child("desc").value.toString(),
                        it.child("awarded").value as Boolean
                    )
                    awards.add(new)
                }
            }

            override fun onCancelled(p0: DatabaseError) {
                Log.e(TAG, "Database Retrieve Cancelled")
            }
        }

        // Get the uid and score of the user if they are signed in
        if (signedIn) {
            // Update the UI to reflect signed in status
            onSignIn(false)
        } else {
            // Update the UI to reflect signed out status
            onSignOut(false)
        }

        val leadersListener = object: ValueEventListener {
            override fun onDataChange(p0: DataSnapshot) {
                Log.i("Count " ,""+p0.childrenCount)
                leaderboard.clear()
                p0.children.forEach {
                    leaderboard.add(0, User(it.key!!, it.child("name").value as String, it.child("score").value as Long))
                }
            }

            override fun onCancelled(p0: DatabaseError) {
                Log.e(TAG, "Database Retrieve Cancelled")
            }
        }

        dataUserRef.orderByChild("score").limitToFirst(10).addValueEventListener(leadersListener)
    }

    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onPostCreate(savedInstanceState, persistentState)
        drawerToggle.syncState()

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_leaderboard -> {
                navController.navigate(R.id.action_MapFragment_to_LeaderboardFragment)
            }
            R.id.nav_contributions -> {
                navController.navigate(R.id.action_MapFragment_to_ContributionsFragment)
            }
            R.id.nav_awards -> {
                navController.navigate(R.id.action_MapFragment_to_AwardsFragment)
            }
        }
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    /**
     * Begins signing the user in when the menu item is clicked
     */
    fun onSignInClick(item: MenuItem) {
        Log.i(TAG, "Attempt to Sign In")
        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(
                    arrayListOf(AuthUI.IdpConfig.GoogleBuilder().build())).build()
            , AUTH_REQUEST_SIGN_IN
        )
    }

    /**
     * Handles changes required when the user successfully signs in
     */
    private fun onSignIn(inform: Boolean) {
        Log.i(TAG, "Successfully Signed In")
        signedIn = true
        uid = auth.uid!!

        if(inform)
            makeToast("Signed In")

        menu.setGroupVisible(R.id.nav_group_logged_in, true)
        menu.setGroupVisible(R.id.nav_group_logged_out, false)

        // Add listeners to get the user's score and contributions
        dataUserRef.child(uid).child("score").addListenerForSingleValueEvent(scoreListener)
        dataUserRef.child(uid).child("count").addListenerForSingleValueEvent(countListener)
        dataReportRef.orderByChild("owner").equalTo(uid).addValueEventListener(contributionsListener)
        dataUserRef.child(uid).child("awards").addValueEventListener(awardsListener)

        navView.getHeaderView(0).findViewById<TextView>(R.id.title_view).text =
            auth.currentUser!!.displayName
        navView.getHeaderView(0).findViewById<TextView>(R.id.subtitle_view).text =
            auth.currentUser!!.email
    }

    /**
     * Begins signing the user out when the menu item is clicked
     */
    fun onSignOutClick(item: MenuItem) {
        // Sign out from the application
        Log.i(TAG, "Attempt to Sign Out")
        AuthUI.getInstance().signOut(this)
            .addOnSuccessListener {
                onSignOut(true)
            }
            .addOnFailureListener {
                Log.e(TAG, "Attempt to Sign Out Failed")
                makeToast("Sign Out Failed")
            }
    }

    /**
     * Handles changes required when the user successfully signs out
     */
    private fun onSignOut(inform: Boolean) {
        Log.i(TAG, "Successfully Signed Out")

        signedIn = false
        uid = null.toString()
        userScore = 0

        if(inform) {
            makeToast("Signed Out")
        }

        // Remove the event listeners
        dataReportRef.removeEventListener(contributionsListener)
        dataUserRef.removeEventListener(scoreListener)
        dataUserRef.removeEventListener(awardsListener)

        menu.setGroupVisible(R.id.nav_group_logged_in, false)
        menu.setGroupVisible(R.id.nav_group_logged_out, true)

        navView.getHeaderView(0).findViewById<TextView>(R.id.title_view).text = ""
        navView.getHeaderView(0).findViewById<TextView>(R.id.subtitle_view).text = ""
    }

    // AUXILIARY METHODS
    /**
     * Make a toast message appear with the passed in string
     */
    private fun makeToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    fun onFabClick(loc: Location){
        location = loc
        dispatchTakePictureIntent()
    }

    private fun onPictureSuccess() {
        Log.i(TAG, "picture returned")
        val bottomSheet = BottomSheetDialog(this)

        val root = layoutInflater.inflate(R.layout.content_bottom_sheet, null)

        val scoreTextView = root.findViewById<TextView>(R.id.score_indicator)
        val categorySpinner = root.findViewById<Spinner>(R.id.category_spinner)
        val submitButton = root.findViewById<Button>(R.id.submit_button)

        // Change the indicated Score when the user changes the description
        root.findViewById<EditText>(R.id.description_field).addTextChangedListener {
            description = it.toString()
            if(it.isNullOrEmpty()){
                scoreTextView.text = getString(R.string.base_score)
            }else{
                scoreTextView.text = getString(R.string.extra_score)
            }
        }

        // Setup the Adapter for the Spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.categories_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            categorySpinner.adapter = adapter
        }

        categorySpinner.onItemSelectedListener = this

        submitButton.setOnClickListener {
            bottomSheet.dismiss()
            submit()
        }

        bottomSheet.setContentView(root)
        bottomSheet.show()
    }

    private fun submit(){
        // Extract the Latitude and Longitude
        val lat = location.latitude
        val long = location.longitude

        // Upload the photo
        val file = Uri.fromFile(File(currentPhotoPath))
        val refToImage = "images/${file.lastPathSegment}"
        val ref = imageRef.child(refToImage)
        val uploadTask = ref.putFile(file)

        val report = Report(
            uid,
            lat,
            long,
            refToImage,
            SimpleDateFormat("HH:mm dd/MM/yyyy").format(Date()),
            category,
            description
        )

        // Add status Listeners to the upload task
        uploadTask.addOnFailureListener {
            Log.e(TAG, "Error Uploading Photo")
        }.addOnSuccessListener {
            Log.i(TAG, "Photo Uploaded")

            // Once the image has successfully been upload proceed to update the database
            dataRef.child("reports")
                .child(uid + "_" + SimpleDateFormat("yyyyMMdd_HHmmss")
                    .format(Date())
                ).setValue(report)

            // Update the user's score
            userScore += 10
            if (description != "") {
                userScore += 20
            }
            dataUserRef.child(uid).child("score").setValue(userScore)

            contributionCount += 1
            if(contributionCount >= 1){
                dataUserRef.child(uid).child("awards").child("0").child("awarded").setValue(true)
            }

            if(contributionCount >= 10){
                dataUserRef.child(uid).child("awards").child("1").child("awarded").setValue(true)
            }

            if(contributionCount >= 100){
                dataUserRef.child(uid).child("awards").child("2").child("awarded").setValue(true)
            }

            dataUserRef.child(uid).child("count").setValue(contributionCount)
        }

    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(this.packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Log.e(TAG, "Image File Creation Failed")
                    null
                }

                // Proceed only if photo file was successfully created.
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(this,
                        "com.honours.project.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            onPictureSuccess()
        } else if (requestCode == AUTH_REQUEST_SIGN_IN && resultCode == Activity.RESULT_OK) {
            onSignIn(true)
        }
    }

    fun getLeaderboard(): ArrayList<User>{
        return leaderboard
    }

    fun getContributions(): ArrayList<Report>{
        return contributions
    }

    fun getAwards(): ArrayList<Award>{
        return awards
    }

    override fun onBackPressed() {
        if (this.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            this.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {

    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
        if (parent != null) {
            category = parent.getItemAtPosition(pos) as String
        }
    }
}

class MarkerViewModel : ViewModel() {
    val toMark = MutableLiveData<Report>()

    fun mark(report: Report) {
        toMark.value = report
    }
}
