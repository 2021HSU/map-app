package com.hsu.mapapp.profile

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.hsu.mapapp.R
import com.hsu.mapapp.databinding.ActivityProfileBinding
import com.hsu.mapapp.login.LoginActivity
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class ProfileActivity : AppCompatActivity() {
    private lateinit var profileBinding: ActivityProfileBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val TAG: String = "AppDebug"
    private val GALLERY_REQUEST_CODE = 1234
    private var uriPhoto: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileBinding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(profileBinding.root)

        setProfileModifyBtnClickEvent()
        setUpdatePasswordBtn()

        profileBinding.logoutBtn.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setMessage("정말로 로그아웃 하시겠습니까?")

            builder.setPositiveButton("예") { dialog, which ->
                signOut()
            }
            builder.setNegativeButton("아니오") { dialog, which ->
                builder.setCancelable(true)
            }
            builder.show()
        }

        profileBinding.deleteAccountBtn.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setMessage("정말로 탈퇴 하시겠습니까?")

            builder.setPositiveButton("예") { dialog, which ->
                revokeAccess()
            }
            builder.setNegativeButton("아니오") { dialog, which ->
                builder.setCancelable(true)
            }
            builder.show()

        }

        //---------------------사용자 정보 load----------------------//

        //firebase auth 객체
        firebaseAuth = FirebaseAuth.getInstance()

        // 구글 로그인 정보
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.firebase_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        //----------------------------------------------------------//

        // 프로필 표시
        setProfile()

        // name 설정
        profileBinding.buildNameIv.setOnClickListener {
            updateName()
        }

        // 프로필 사진 설정
        profileBinding.buildProfileIv.setOnClickListener {
            //startActivity(Intent(this, CropImg::class.java))
            pickFromGallery()
        }
    }

    //---------------------프로필 표시----------------------//
    private fun setProfile() {
        val user = Firebase.auth.currentUser
        if (user != null) {
            user?.let {
                val name = user.displayName
                val email = user.email
                val photoUrl = user.photoUrl
                val emailVerified = user.isEmailVerified
                val uid = user.uid

                profileBinding.profileNameTV.text = name
                profileBinding.profileEmailTV.text = email

                val progressDialog = ProgressDialog(this)
                progressDialog.setMessage("Fetching Image ...")
                progressDialog.setCancelable(false)
                progressDialog.show()
                val localfile = File.createTempFile("tempImage","jpg")
                val storageRef = FirebaseStorage.getInstance().reference.child("ProfileImage").child("$name").getFile(localfile)
                storageRef.addOnSuccessListener {
                    if(progressDialog.isShowing)
                        progressDialog.dismiss()
                    val bitmap = BitmapFactory.decodeFile(localfile.absolutePath)
                    profileBinding.profileImageIV.setImageBitmap(bitmap)
                }.addOnFailureListener{
                if(progressDialog.isShowing)
                    progressDialog.dismiss()
                Toast.makeText(this, "Failed to retrieve your image",Toast.LENGTH_SHORT).show()
            }
            }
        } else {
            // No user is signed in
        }
    }

    // 프로필 수정 버튼 이벤트
    private fun setProfileModifyBtnClickEvent() {
        profileBinding.profileModifyBtn.setOnClickListener {
            startActivity(Intent(this, CropImg::class.java))
            overridePendingTransition(R.anim.anim_slide_in_left, R.anim.anim_slide_out_right)
        }
    }

    //---------------------로그아웃----------------------//
    private fun signOut() {
        firebaseAuth.signOut() // Firebase sign out
        // Google sign out
        googleSignInClient.signOut().addOnCompleteListener(this) {
            //updateUI(null)
        }

        val loginIntent = Intent(this, LoginActivity::class.java)
        startActivity(loginIntent)
    }

    //---------------------회원 탈퇴----------------------//
    private fun revokeAccess() {
        val user = Firebase.auth.currentUser!!
        signOut()
        user.delete()
            .addOnCompleteListener { task ->
                Toast.makeText(this, "회원 정보 삭제 완료", Toast.LENGTH_SHORT).show()
            }

        val loginIntent = Intent(this, LoginActivity::class.java)
        startActivity(loginIntent)
    }

    //---------------------비밀번호 재설정----------------------//
    private fun setUpdatePasswordBtn() {
        profileBinding.passwordChangeBtn.setOnClickListener {
            startActivity(Intent(this, UpdatePasswordActivity::class.java))
            overridePendingTransition(R.anim.anim_slide_in_left, R.anim.anim_slide_out_right)
        }
    }

    //---------------------닉네임 재설정----------------------//
    private fun updateName() {
        val user = Firebase.auth.currentUser
        var value = ""
        val linearLayout = View.inflate(this, R.layout.dialog_name, null)
        if (user != null) {
            // 닉네임 입력 다이얼로그 설정
            val builder = AlertDialog.Builder(this)
                .setView(linearLayout)
                .setPositiveButton("확인") { dialog, which ->
                    val editText: EditText = linearLayout.findViewById(R.id.name_editText)
                    value = editText.text.toString()
                    // firestore - users - name 업데이트
                    val firestore = FirebaseFirestore.getInstance()
                    var map = mutableMapOf<String,Any>()
                    map["name"] = value
                    firestore.collection("users").document(Firebase.auth.uid.toString()).update(map)
                        .addOnCompleteListener {
                            if (it.isSuccessful) {
                                Log.d("user name", "updated")
                                // 바뀐 name text 설정
                                profileBinding.profileNameTV.text = value
                            }
                        }
                    // [END update_profile]
                }
                .setNegativeButton("취소") { dialog, which ->
                    dialog.dismiss()
                }
            // 다이얼로그 실행
            builder.show()

        }
    }

    //------------------프로필 이미지 크롭----------------------//
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {

            GALLERY_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        launchImageCrop(uri)
                    }
                }
                else{
                    Log.e(TAG, "Image selection error: Couldn't select that image from memory." )
                }
            }

            CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE -> {
                val result = CropImage.getActivityResult(data)
                uriPhoto = result.uri
                if (resultCode == Activity.RESULT_OK) {
                    setImage(result.uri)
                    imageUpload()
                }
                else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                    Log.e(TAG, "Crop error: ${result.getError()}" )
                }
            }
        }
    }

    private fun imageUpload() {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Uploading File ...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        val imageName = profileBinding.profileNameTV.text
        val storageReference = FirebaseStorage.getInstance().getReference("ProfileImage/$imageName")

        storageReference.putFile(uriPhoto!!).addOnSuccessListener {
            Toast.makeText(this@ProfileActivity, "Successfully uploaded", Toast.LENGTH_SHORT).show()
            if (progressDialog.isShowing)
                progressDialog.dismiss()
        }.addOnFailureListener {
            if (progressDialog.isShowing)
                progressDialog.dismiss()
            Toast.makeText(this@ProfileActivity, "Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setImage(uri: Uri){
        Glide.with(this)
            .load(uri)
            .into(profileBinding.profileImageIV)
    }

    private fun launchImageCrop(uri: Uri){
        CropImage.activity(uri)
            .setGuidelines(CropImageView.Guidelines.ON)
            .setCropShape(CropImageView.CropShape.RECTANGLE) // default is rectangle
            .start(this)
    }

    private fun pickFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }
}