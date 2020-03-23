package com.biffodeveloper.covid19_admin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class HomeActivity extends AppCompatActivity {
    FirebaseAuth mAuth;
    FirebaseUser mUser;
    FirebaseFirestore db;
    private ZXingScannerView qrcode_scanner;
    private RelativeLayout loading;
    private boolean dialogShowing = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Button signout_btn = findViewById(R.id.signout_btn);
        qrcode_scanner = findViewById(R.id.qrcode_scanner);
        loading = findViewById(R.id.loading);
        mAuth = FirebaseAuth.getInstance();
        mUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        signout_btn.setOnClickListener(view -> signOut());
        setScannerProperties();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(dialogShowing) return;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA }, 1010);
                return;
            }
        }
        qrcode_scanner.startCamera();
        qrcode_scanner.setResultHandler(result -> {
            if(result != null) {
                onStop();
                String[] text = result.getText().split("/");
                if(text.length != 6) {
                    Toast.makeText(this, "Codice mal formulato, si prega di riprovare", Toast.LENGTH_SHORT).show();
                    loading.setVisibility(View.GONE);
                    onResume();
                    return;
                }
                String uid = text[0];
                String name = text[1];
                String surname = text[2];
                String[] birthdateStr = text[3].split("_");
                String operation = text[4];
                long time = Long.parseLong(text[5]);
                AlertDialog.Builder builder = new AlertDialog.Builder(HomeActivity.this);
                builder.setCancelable(false);
                builder.setTitle("QR Code trovato");
                builder.setMessage("Nome: " + name + " " + surname + "\n" + "Data di nascita: " + birthdateStr[0] + "/" + birthdateStr[1] + "/" + birthdateStr[2] + "\n" + "Richiesta per: " + translateOperation(operation) + "\n" + "Accesso settimanale n. " + time);
                builder.setNeutralButton("Valida", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    dialogShowing = false;
                    loading.setVisibility(View.VISIBLE);
                    db.collection("users").document(uid).get().addOnCompleteListener(task -> {
                        if(task.isSuccessful()) {
                            long before = (long) Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(task.getResult()).getData()).get(operation));
                            if(before != time) {
                                Toast.makeText(this, "Numero di accessi non conforme", Toast.LENGTH_SHORT).show();
                                loading.setVisibility(View.GONE);
                                onResume();
                                return;
                            }
                            Map<String, Object> update = new HashMap<>();
                            update.put(operation, before - 1);
                            db.collection("users").document(uid).update(update).addOnCompleteListener(task1 -> {
                                if(task1.isSuccessful()) {
                                    Toast.makeText(HomeActivity.this, "Validato", Toast.LENGTH_SHORT).show();
                                }
                                else {
                                    Toast.makeText(HomeActivity.this, "Qualcosa è andato storto, si prega di riprovare", Toast.LENGTH_SHORT).show();
                                }
                                loading.setVisibility(View.GONE);
                                onResume();
                            });
                        }
                        else {
                            Toast.makeText(this, "Qualcosa è andato storto, si prega di riprovare", Toast.LENGTH_SHORT).show();
                            loading.setVisibility(View.GONE);
                            onResume();
                        }
                    });
                });
                builder.create().show();
                dialogShowing = true;
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        qrcode_scanner.stopCamera();
    }

    private void setScannerProperties() {
        List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.QR_CODE);
        qrcode_scanner.setFormats(formats);
        qrcode_scanner.setAutoFocus(true);
        qrcode_scanner.setLaserColor(R.color.colorAccent);
        qrcode_scanner.setMaskColor(R.color.colorAccent);
        if(Build.MANUFACTURER.equalsIgnoreCase("Huawei")) {
            qrcode_scanner.setAspectTolerance(0.5f);
        }
    }

    private void signOut() {
        mAuth.signOut();
        Intent intent = new Intent(HomeActivity.this, MainActivity.class);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 1010) {
            Toast.makeText(this, "La fotocamera è necessaria", Toast.LENGTH_SHORT).show();
        }
    }

    public String translateOperation(String op) {
        switch (op) {
            case "supermarket":
                return "Supermercato";
            case "drugstore":
                return "Farmacia";
            case "tobacconist":
                return "Tabaccaio";
            default:
                return "";
        }
    }
}
